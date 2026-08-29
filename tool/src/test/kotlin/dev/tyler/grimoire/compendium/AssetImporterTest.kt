package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The importer behind its three seams (plan §AssetImporter, D4, D5): one transaction that clears both tables
 * and inserts the 22 kinds in [Kind] order from the sha256-pinned assets; a DataStore stamp
 * `"$schemaVersion.$FORMAT:$bundleSha256"` written only after the commit; Ready = stamp match AND both
 * `records` and `search_index` holding `index.total` rows (a short FTS table would otherwise read Ready
 * forever); every failure propagates with the transaction rolled back and the marker untouched, so the next
 * launch simply tries again. Room cannot run on the JVM, so the writer is [FakeWriter] over
 * [FakeCompendiumDao] and the source is [FakeAssetSource] over the bundled bytes.
 */
class AssetImporterTest {
    private val index = Fixtures.compendiumIndex()
    private val hash = index.bundleSha256
    private val stamp = "1.1:$hash"
    private val indexPath = "compendium/index.json"

    /** Every kind's index count in [Kind] order — what each `insert:` event must carry. */
    private fun countOf(kind: Kind): Int = index.files.getValue(kind.file).count

    private class Rig(
        overrides: Map<String, ByteArray> = emptyMap(),
        stamp: String? = null,
        readThrows: Boolean = false,
        failOnInsert: Int? = null,
        schemaVersion: Int = 1,
    ) {
        val events = ArrayList<String>()
        val source = FakeAssetSource(overrides, events)
        val marker = FakeMarker(stamp, readThrows, events)
        val writer = FakeWriter(failOnInsert = failOnInsert, events = events)
        val ticks = ArrayList<Pair<Int, Int>>()
        val importer = AssetImporter(source, marker, writer, schemaVersion) { done, total -> ticks += done to total }

        fun ensure(): ImportResult = runBlocking { importer.ensure() }

        fun reset() {
            events.clear()
            source.reads.clear()
            ticks.clear()
        }

        fun inserts(): List<String> = events.filter { it.startsWith("insert:") }
    }

    /** index.json with its `files` object rewritten, re-encoded and handed back as bytes. */
    private fun indexWithFiles(edit: (JsonObject) -> JsonObject): ByteArray {
        val original = Json.parseToJsonElement(Fixtures.compendium("index.json")).jsonObject
        val rewritten = buildJsonObject {
            for ((k, v) in original) put(k, if (k == "files") edit(v.jsonObject) else v)
        }
        return Json.encodeToString(JsonObject.serializer(), rewritten).encodeToByteArray()
    }

    private fun indexWith(edit: (JsonObject) -> JsonObject): ByteArray {
        val original = Json.parseToJsonElement(Fixtures.compendium("index.json")).jsonObject
        return Json.encodeToString(JsonObject.serializer(), edit(original)).encodeToByteArray()
    }

    // ---- (a) fresh import ------------------------------------------------------------------------------------

    @Test
    fun freshImportClearsFirstThenInsertsEveryKindInOrderInsideOneTransaction() {
        val rig = Rig()
        val result = rig.ensure()
        val imported = assertIs<ImportResult.Imported>(result, "an empty store imports")
        assertEquals(index.total, imported.rows, "rows reported")
        assertEquals(1992, imported.rows, "SRD 5.1 bundle rows")

        val begin = rig.events.indexOf("begin")
        val clear = rig.events.indexOf("clear")
        val commit = rig.events.indexOf("commit")
        assertTrue(begin in 0 until clear, "clear happens inside the transaction")
        assertTrue(rig.events.none { it == "rollback" }, "no rollback")
        val inserts = rig.inserts()
        assertEquals(Kind.entries.map { "insert:${it.id}:${countOf(it)}:${countOf(it)}" }, inserts, "22 inserts in Kind order, one per file, records == search rows")
        for (insert in inserts) assertTrue(rig.events.indexOf(insert) in (clear + 1) until commit, "$insert lands after clear and before commit")
        assertEquals(listOf(indexPath) + Kind.entries.map { "compendium/${it.file}" }, rig.source.reads, "index first, then one file per kind in Kind order")
    }

    @Test
    fun freshImportTicksOncePerKindAndWritesTheStampOnlyAfterTheCommit() {
        val rig = Rig()
        rig.ensure()
        assertEquals((1..22).map { it to 22 }, rig.ticks, "22 progress ticks of 22")
        assertEquals(listOf(stamp), rig.marker.writes, "the stamp is written exactly once")
        assertTrue(stamp.startsWith("1.1:fce4d793"), "stamp = schema.format:bundleSha256")
        val commit = rig.events.indexOf("commit")
        val write = rig.events.indexOf("marker.write:$stamp")
        assertTrue(commit >= 0 && write > commit, "the marker is written after the transaction commits (commit at $commit, write at $write)")
        assertEquals(rig.events.size - 1, write, "the stamp is the last thing that happens")
    }

    @Test
    fun freshImportProducesExactlyTheRowsOfRowsOfOverTheWholeBundle() {
        val rig = Rig()
        rig.ensure()
        val dao = rig.writer.dao
        var ctx = ImportContext.EMPTY
        val expected = Kind.entries.flatMap { kind ->
            val text = Fixtures.compendium(kind.file)
            val slices = JsonArraySplit.elements(text)
            val records = kind.decodeAll(text)
            if (kind == Kind.RULES) ctx = ImportContext.from(records)
            records.indices.map { Rows.of(kind, it, slices[it], records[it], ctx) }
        }
        assertEquals(expected.size, dao.records.size, "one records row per bundled record")
        assertEquals(expected.map { it.record }, dao.records, "records rows == Rows.of over the bundle, in import order")
        assertEquals(expected.map { it.search }, dao.search, "search rows == Rows.of over the bundle, in import order")
        runBlocking {
            assertEquals(index.total, dao.count(), "records count")
            assertEquals(index.total, dao.searchCount(), "search_index count")
            assertEquals(
                index.files.entries.sortedBy { it.key }.map { KindCount(it.key.removeSuffix(".json"), it.value.count) },
                dao.countsByKind(),
                "per-kind counts equal index.json",
            )
        }
        val sections = dao.records.filter { it.kind == "rule_sections" }
        assertEquals(40, sections.size, "rule sections")
        for (section in sections) assertTrue(section.parentKey != null, "rule section '${section.key}' knows its chapter (ImportContext was built from rules.json)")
        val fireball = dao.records.single { it.kind == "spells" && it.key == "fireball" }
        assertEquals(3, fireball.level, "fireball level")
        assertTrue(Fixtures.compendium("spells.json").contains(fireball.json), "json is the raw asset slice")
    }

    @Test
    fun importedCarriesNonNegativeTimingBucketsInsideTheTotal() {
        val imported = assertIs<ImportResult.Imported>(Rig().ensure(), "fresh import")
        assertTrue(imported.decodeMs >= 0, "decodeMs ≥ 0")
        assertTrue(imported.insertMs >= 0, "insertMs ≥ 0")
        assertTrue(imported.totalMs >= imported.decodeMs + imported.insertMs, "total covers decode + insert (${imported.totalMs} ≥ ${imported.decodeMs} + ${imported.insertMs})")
    }

    // ---- (b) ready ---------------------------------------------------------------------------------------------

    @Test
    fun matchingStampOverAFullTableSkipsAfterReadingOnlyTheIndex() {
        val rig = Rig()
        rig.ensure()
        rig.reset()
        val result = rig.ensure()
        assertEquals(ImportResult.Skipped(1992), result, "second launch is skipped")
        assertEquals(listOf(indexPath), rig.source.reads, "only index.json is read")
        assertEquals(listOf("read:$indexPath", "marker.read", "count", "searchCount"), rig.events, "stamp check, then both row counts, nothing written")
        assertEquals(emptyList(), rig.ticks, "no progress ticks")
        assertEquals(listOf(stamp), rig.marker.writes, "the stamp is not rewritten")
    }

    // ---- (c) stamp without rows --------------------------------------------------------------------------------

    @Test
    fun matchingStampOverAnEmptyTableReimports() {
        val rig = Rig(stamp = stamp)
        val result = rig.ensure()
        assertIs<ImportResult.Imported>(result, "a lost database is rebuilt")
        assertEquals(22, rig.inserts().size, "all 22 files inserted")
        assertEquals(1992, runBlocking { rig.writer.dao.count() }, "rows")
        assertEquals(listOf(stamp), rig.marker.writes, "the stamp is rewritten after the commit")
    }

    @Test
    fun matchingStampOverAShortTableReimports() {
        val rig = Rig()
        rig.ensure()
        rig.writer.dao.records.removeAt(0)
        rig.reset()
        val result = rig.ensure()
        assertIs<ImportResult.Imported>(result, "a row count off by one re-imports")
        assertEquals(1992, runBlocking { rig.writer.dao.count() }, "rows restored")
    }

    @Test
    fun matchingStampOverAFullRecordsTableWithAShortSearchIndexReimports() {
        val rig = Rig()
        rig.ensure()
        rig.writer.dao.search.removeAt(0)
        rig.reset()
        val result = rig.ensure()
        assertIs<ImportResult.Imported>(result, "a search_index short by one row re-imports even though records is full")
        assertEquals(
            listOf("read:$indexPath", "marker.read", "count", "searchCount", "begin"),
            rig.events.take(5),
            "both tables are counted before the transaction begins",
        )
        runBlocking {
            assertEquals(1992, rig.writer.dao.count(), "records rows")
            assertEquals(1992, rig.writer.dao.searchCount(), "search_index rows restored")
        }
        assertEquals(listOf(stamp, stamp), rig.marker.writes, "the stamp is rewritten after the commit")
    }

    // ---- (d) stale stamp ---------------------------------------------------------------------------------------

    @Test
    fun staleStampOverAFullTableClearsAndReimports() {
        val rig = Rig()
        rig.ensure()
        rig.marker.stamp = "1.1:" + "0".repeat(64)
        rig.reset()
        val result = rig.ensure()
        assertIs<ImportResult.Imported>(result, "a new bundle hash re-imports")
        assertEquals("clear", rig.events.first { it == "clear" || it.startsWith("insert:") }, "clear precedes every insert")
        assertEquals(22, rig.inserts().size, "all 22 files inserted")
        assertEquals(1992, runBlocking { rig.writer.dao.count() }, "no duplicates after the re-import")
        assertEquals(stamp, rig.marker.stamp, "the current stamp replaces the stale one")
    }

    // ---- (e) schema version ------------------------------------------------------------------------------------

    @Test
    fun schemaVersionIsTheFirstFieldOfTheStampAndFormatTheSecond() {
        assertEquals(1, AssetImporter.FORMAT, "FORMAT")
        val v2 = Rig(schemaVersion = 2)
        v2.ensure()
        assertEquals("2.${AssetImporter.FORMAT}:$hash", v2.marker.stamp, "schema 2 stamp")
        assertEquals("2.1:$hash", v2.marker.stamp, "literal shape")
        val default = Rig()
        default.ensure()
        assertEquals("${CompendiumDb.SCHEMA_VERSION}.1:$hash", default.marker.stamp, "the default schema version is CompendiumDb.SCHEMA_VERSION")
    }

    @Test
    fun aSchemaVersionBumpReimportsOverAFullTableWithTheOldStamp() {
        val rig = Rig()
        rig.ensure()
        rig.reset()
        val bumped = AssetImporter(rig.source, rig.marker, rig.writer, 2) { done, total -> rig.ticks += done to total }
        val result = runBlocking { bumped.ensure() }
        assertIs<ImportResult.Imported>(result, "the old stamp does not match the bumped schema")
        assertEquals(listOf(stamp, "2.1:$hash"), rig.marker.writes, "old stamp, then the bumped one")
        assertEquals(1992, runBlocking { rig.writer.dao.count() }, "rows")
    }

    // ---- (f) failure mid-import --------------------------------------------------------------------------------

    @Test
    fun aFailingInsertRollsBackLeavesTheMarkerUntouchedAndTheNextRunImportsEverything() {
        val rig = Rig(failOnInsert = 7)
        val error = assertFailsWith<IllegalStateException>("the sink's exception propagates") { rig.ensure() }
        assertEquals("insert 7 failed", error.message, "the original exception, unwrapped")
        assertEquals((1..6).map { it to 22 }, rig.ticks, "six kinds ticked before the failure")
        assertEquals(6, rig.inserts().size, "six inserts landed before the seventh threw")
        assertTrue("rollback" in rig.events && "commit" !in rig.events, "the transaction rolled back")
        assertEquals(0, runBlocking { rig.writer.dao.count() }, "nothing survives the rollback")
        assertEquals(emptyList(), rig.marker.writes, "the marker is untouched")
        assertNull(rig.marker.stamp, "no stamp")

        rig.reset()
        val result = rig.ensure()
        assertIs<ImportResult.Imported>(result, "the next launch imports")
        assertEquals(22, rig.inserts().size, "all 22 files")
        assertEquals((1..22).map { it to 22 }, rig.ticks, "22 ticks")
        assertEquals(listOf(stamp), rig.marker.writes, "stamped after the successful commit")
        assertEquals(1992, runBlocking { rig.writer.dao.count() }, "rows")
    }

    @Test
    fun aFailingReimportKeepsThePreviousRowsAndThePreviousStamp() {
        val rig = Rig()
        rig.ensure()
        val stale = "1.1:" + "0".repeat(64)
        rig.marker.stamp = stale
        rig.writer.failOnInsert = 3
        rig.reset()
        assertFailsWith<IllegalStateException>("the failure propagates") { rig.ensure() }
        assertEquals(1992, runBlocking { rig.writer.dao.count() }, "the rollback restores the previous bundle's rows")
        assertEquals(stale, rig.marker.stamp, "the stale stamp stays, so the next launch retries")
    }

    // ---- (g) a file that does not match the index --------------------------------------------------------------

    @Test
    fun aFileOneByteShortFailsNamingTheFileAndRollsBack() {
        val feats = Fixtures.compendiumBytes("feats.json")
        val rig = Rig(overrides = mapOf("compendium/feats.json" to feats.copyOf(feats.size - 1)))
        val error = assertFailsWith<IllegalStateException>("a size mismatch fails the import") { rig.ensure() }
        val message = error.message.orEmpty()
        assertTrue("feats.json" in message, "names the file: $message")
        assertTrue("${feats.size - 1}" in message && "${feats.size}" in message, "names both sizes: $message")
        assertEquals((1..11).map { it to 22 }, rig.ticks, "the eleven kinds before feats ticked")
        assertTrue("rollback" in rig.events, "rolled back")
        assertEquals(0, runBlocking { rig.writer.dao.count() }, "no rows survive")
        assertEquals(emptyList(), rig.marker.writes, "the marker is untouched")
    }

    @Test
    fun aRecordCountThatDisagreesWithTheIndexFailsNamingTheFile() {
        val rig = Rig(
            overrides = mapOf(
                indexPath to indexWithFiles { files ->
                    buildJsonObject {
                        for ((name, meta) in files) {
                            put(
                                name,
                                if (name != "feats.json") meta else buildJsonObject {
                                    for ((k, v) in meta.jsonObject) put(k, if (k == "count") JsonPrimitive(2) else v)
                                },
                            )
                        }
                    }
                },
            ),
        )
        val error = assertFailsWith<IllegalStateException>("a count mismatch fails the import") { rig.ensure() }
        val message = error.message.orEmpty()
        assertTrue("feats.json" in message, "names the file: $message")
        assertTrue(Regex("\\b1\\b").containsMatchIn(message), "names the decoded count: $message")
        assertTrue(Regex("\\b2\\b").containsMatchIn(message), "names the index count: $message")
        assertEquals(emptyList(), rig.marker.writes, "the marker is untouched")
        assertEquals(0, runBlocking { rig.writer.dao.count() }, "no rows survive")
    }

    // ---- (h) an index the importer cannot trust ----------------------------------------------------------------

    @Test
    fun anIndexNamingAnUnknownFileFailsBeforeAnythingElseIsReadOrWritten() {
        val rig = Rig(
            overrides = mapOf(
                indexPath to indexWithFiles { files ->
                    buildJsonObject {
                        for ((name, meta) in files) put(name, meta)
                        put(
                            "monsters.json",
                            buildJsonObject {
                                put("bytes", JsonPrimitive(1))
                                put("count", JsonPrimitive(1))
                                put("sha256", JsonPrimitive("00"))
                            },
                        )
                    }
                },
            ),
        )
        val error = assertFailsWith<IllegalStateException>("an unknown file fails the import") { rig.ensure() }
        assertTrue("monsters.json" in error.message.orEmpty(), "names the offender: ${error.message}")
        assertEquals(listOf(indexPath), rig.source.reads, "nothing but the index is read")
        assertEquals(listOf("read:$indexPath"), rig.events, "no marker, no writer, no transaction")
        assertEquals(emptyList(), rig.ticks, "no ticks")
    }

    @Test
    fun anIndexMissingAKindFailsNamingTheMissingFile() {
        val rig = Rig(
            overrides = mapOf(
                indexPath to indexWithFiles { files ->
                    buildJsonObject { for ((name, meta) in files) if (name != "feats.json") put(name, meta) }
                },
            ),
        )
        val error = assertFailsWith<IllegalStateException>("a missing file fails the import") { rig.ensure() }
        assertTrue("feats.json" in error.message.orEmpty(), "names the missing file: ${error.message}")
        assertEquals(listOf(indexPath), rig.source.reads, "nothing but the index is read")
        assertEquals(emptyList(), rig.marker.writes, "the marker is untouched")
    }

    @Test
    fun anIndexOfAnotherSchemaVersionFailsNamingIt() {
        val rig = Rig(
            overrides = mapOf(
                indexPath to indexWith { original ->
                    buildJsonObject { for ((k, v) in original) put(k, if (k == "schemaVersion") JsonPrimitive(2) else v) }
                },
            ),
        )
        val error = assertFailsWith<IllegalStateException>("an unknown bundle schema fails the import") { rig.ensure() }
        val message = error.message.orEmpty()
        assertTrue("schemaVersion" in message && "2" in message, "names the schema version: $message")
        assertEquals(listOf(indexPath), rig.source.reads, "nothing but the index is read")
    }

    @Test
    fun anIndexThatDoesNotDecodeStrictlyFails() {
        val rig = Rig(
            overrides = mapOf(
                indexPath to indexWith { original ->
                    buildJsonObject {
                        for ((k, v) in original) put(k, v)
                        put("mystery", JsonPrimitive(true))
                    }
                },
            ),
        )
        assertFailsWith<Exception>("an unknown index field is rejected, never ignored") { rig.ensure() }
        assertEquals(listOf(indexPath), rig.source.reads, "nothing but the index is read")
        assertEquals(emptyList(), rig.marker.writes, "the marker is untouched")
    }

    // ---- (i) an unreadable marker ------------------------------------------------------------------------------

    @Test
    fun anUnreadableMarkerImportsAndThenWritesTheStamp() {
        val rig = Rig()
        rig.ensure()
        rig.marker.readThrows = true
        rig.reset()
        val result = rig.ensure()
        assertIs<ImportResult.Imported>(result, "a marker that cannot be read counts as no stamp")
        assertEquals(listOf(stamp, stamp), rig.marker.writes, "the stamp is written again after the commit")
        assertEquals(1992, runBlocking { rig.writer.dao.count() }, "rows")
    }
}
