package dev.tyler.grimoire.compendium

import kotlin.coroutines.cancellation.CancellationException

/** Where the bundled files come from; in production `SealedLightContext::readAsset` (plan D10). */
fun interface AssetSource {
    fun read(path: String): ByteArray
}

/** The one preference the importer keeps: the stamp of the bundle the database holds (plan D5). */
interface ImportMarker {
    suspend fun read(): String?
    suspend fun write(stamp: String)
}

/** What the importer may do inside the one transaction (plan D4). */
interface ImportSink {
    suspend fun clear()
    suspend fun insert(records: List<RecordRow>, search: List<SearchRow>)
}

/**
 * The database side of the importer; in production Room's `withTransaction` over the DAO. Both tables are
 * counted for the readiness check: `records` and `search_index` are written in one transaction, but only
 * counting both keeps a truncated FTS table from reading Ready with no repair path short of `pm clear`.
 */
interface CompendiumWriter {
    suspend fun count(): Int
    suspend fun searchCount(): Int
    suspend fun replaceAll(block: suspend (ImportSink) -> Unit)
}

/** What the Home screen renders while the compendium is checked or imported (plan D8). */
sealed interface ImportState {
    data object Idle : ImportState
    data object Checking : ImportState
    data class Importing(val done: Int, val total: Int) : ImportState
    data object Ready : ImportState
    data class Failed(val reason: String) : ImportState
}

sealed interface ImportResult {
    /** The stamp matched and both tables held every row — nothing to do. */
    data class Skipped(val rows: Int) : ImportResult

    /** A full import ran; the buckets are what `CompendiumStore` logs. */
    data class Imported(val rows: Int, val decodeMs: Long, val insertMs: Long, val totalMs: Long) : ImportResult
}

/**
 * Brings the compendium database up to date with the bundled assets (ADR-0002, plan D4–D5). Pure apart from
 * its three seams, so AssetImporterTest runs the whole bundle through it on the JVM.
 *
 * `ensure()` reads `compendium/index.json` strictly, refuses an index the models were not written for, then
 * decides: the database is ready when the marker holds `"$schemaVersion.$FORMAT:$bundleSha256"` AND both the
 * `records` and `search_index` tables hold `index.total` rows (the stamp alone lies after a lost file, the
 * counts alone after an equal-count bundle, the records count alone after a short FTS table). Otherwise one
 * transaction clears both tables and, per kind in [Kind] order, reads the
 * file, checks its size against the index, splits it into raw slices, decodes it strictly, checks the counts,
 * derives the rows and inserts them — 22 progress ticks, peak memory one file. The stamp is written only after
 * the commit. Any exception propagates: the transaction rolls back, the marker is untouched, and the next
 * `ensure()` starts over.
 */
class AssetImporter(
    private val source: AssetSource,
    private val marker: ImportMarker,
    private val writer: CompendiumWriter,
    private val schemaVersion: Int = CompendiumDb.SCHEMA_VERSION,
    private val onProgress: (done: Int, total: Int) -> Unit,
) {
    companion object {
        /** Bump when [Rows.of] changes what it derives — forces a re-import of the same bundle into the same file. */
        const val FORMAT = 1

        /** The `index.schemaVersion` the record models in Records.kt were written against. */
        const val INDEX_SCHEMA_VERSION = 1

        const val INDEX_PATH = "compendium/index.json"

        /** The DataStore value that marks a complete import of [bundleSha256] under this schema and format. */
        fun stamp(schemaVersion: Int, bundleSha256: String): String = "$schemaVersion.$FORMAT:$bundleSha256"
    }

    suspend fun ensure(): ImportResult {
        val t0 = System.nanoTime()
        val index = readIndex()
        val stamp = stamp(schemaVersion, index.bundleSha256)
        val expected = index.total

        if (currentStamp() == stamp && writer.count() == expected && writer.searchCount() == expected) {
            return ImportResult.Skipped(expected)
        }

        var rows = 0
        var decodeNs = 0L
        var insertNs = 0L
        val total = Kind.entries.size
        writer.replaceAll { sink ->
            sink.clear()
            var ctx = ImportContext.EMPTY
            for ((i, kind) in Kind.entries.withIndex()) {
                val meta = index.files.getValue(kind.file)
                val d0 = System.nanoTime()
                val built = load(kind, meta).also { if (kind == Kind.RULES) ctx = ImportContext.from(it.records) }.rows(ctx)
                val d1 = System.nanoTime()
                sink.insert(built.map { it.record }, built.map { it.search })
                val d2 = System.nanoTime()
                decodeNs += d1 - d0
                insertNs += d2 - d1
                rows += built.size
                onProgress(i + 1, total)
            }
        }
        marker.write(stamp)
        return ImportResult.Imported(rows, ms(decodeNs), ms(insertNs), ms(System.nanoTime() - t0))
    }

    private fun readIndex(): CompendiumIndex {
        val index = CompendiumJson.decodeFromString(CompendiumIndex.serializer(), source.read(INDEX_PATH).decodeToString())
        check(index.schemaVersion == INDEX_SCHEMA_VERSION) {
            "$INDEX_PATH has schemaVersion ${index.schemaVersion}; this build reads schemaVersion $INDEX_SCHEMA_VERSION"
        }
        val known = Kind.entries.map { it.file }.toSet()
        val unknown = index.files.keys - known
        val missing = known - index.files.keys
        check(unknown.isEmpty() && missing.isEmpty()) {
            buildString {
                append(INDEX_PATH).append(" does not list exactly the 22 kinds")
                if (unknown.isNotEmpty()) append("; unknown: ").append(unknown.sorted().joinToString())
                if (missing.isNotEmpty()) append("; missing: ").append(missing.sorted().joinToString())
            }
        }
        return index
    }

    /** The marker's stamp, or null when it cannot be read — an unreadable store means "import" (plan D5). */
    private suspend fun currentStamp(): String? = try {
        marker.read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private class Loaded(val kind: Kind, val slices: List<String>, val records: List<CompendiumRecord>) {
        fun rows(ctx: ImportContext): List<Rows.Built> = records.indices.map { Rows.of(kind, it, slices[it], records[it], ctx) }
    }

    /** One file: read, size-checked against the index, split into raw slices and strictly decoded, counts checked. */
    private fun load(kind: Kind, meta: FileMeta): Loaded {
        val path = "compendium/${kind.file}"
        val bytes = source.read(path)
        check(bytes.size == meta.bytes) { "$path is ${bytes.size} bytes; index.json says ${meta.bytes}" }
        val text = bytes.decodeToString()
        val slices = JsonArraySplit.elements(text)
        val records = kind.decodeAll(text)
        check(slices.size == records.size) { "$path split into ${slices.size} elements but decoded ${records.size} records" }
        check(records.size == meta.count) { "$path decoded ${records.size} records; index.json says ${meta.count}" }
        return Loaded(kind, slices, records)
    }

    private fun ms(ns: Long): Long = ns / 1_000_000
}
