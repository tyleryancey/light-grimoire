package dev.tyler.grimoire.compendium

import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.RoomDatabase
import java.io.File

/**
 * The compendium's Room shapes (plan §Entities, ADR-0009): one generic `records` table for all 22 kinds and a
 * standalone `search_index` FTS4 table. Both are immutable after import — every column is derived by the pure
 * [Rows.of] from a record's raw JSON slice, so the row types are plain data classes the JVM gate can build
 * without Room. Any change to a column here needs a `SCHEMA_VERSION` bump: `buildDatabase` exposes no
 * migration API, so the file name is the migration (plan D6).
 */
@Entity(tableName = "records", primaryKeys = ["kind", "key"], indices = [Index("kind", "sortName")])
data class RecordRow(
    /** [Kind.id], the asset stem. */
    val kind: String,
    /** The upstream slug; a SQL keyword, so every hand-written query spells it as `key` in backticks. */
    val key: String,
    val name: String,
    /** `name.trim().lowercase()` — the list order of every kind-scoped query. */
    val sortName: String,
    /** Index in the asset array; for rule_sections the index within its chapter's `sections[]`. */
    val position: Int,
    /** spells 0–9; features 1–20. */
    val level: Int?,
    /** spells */
    val school: String?,
    /** spells */
    val castingTime: String?,
    /** spells */
    val concentration: Boolean?,
    /** spells */
    val ritual: Boolean?,
    /** spells: `" bard cleric "` — `classes[]` joined and space-padded so `LIKE '% bard %'` matches whole names. */
    val classList: String?,
    /** features, subclasses */
    val classKey: String?,
    /** features */
    val subclassKey: String?,
    /** features.parentKey; traits.parentKey; subraces.raceKey; rule_sections → the chapter's key. */
    val parentKey: String?,
    /** equipment.category; magic_items.category; creatures.type */
    val category: String?,
    /** equipment `"armor"` | `"weapon"` | null; magic_items `"variant"` | `"base"`; creatures: size */
    val subcategory: String?,
    /** magic_items */
    val rarity: String?,
    /** creatures */
    val cr: Double?,
    /** The raw asset slice, byte-identical to the bundle (plan D3). Last column; list queries never select it. */
    val json: String,
)

/** The FTS4 body per kind is [Body.of]; `kind`/`key` ride along un-indexed so a `MATCH` can be kind-scoped. */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["kind", "key"])
@Entity(tableName = "search_index")
data class SearchRow(val kind: String, val key: String, val name: String, val body: String)

/** The list projection every kind-scoped query returns — never `json` (27 KB rule sections would hit the cursor window). */
data class CompendiumRef(
    val kind: String,
    val key: String,
    val name: String,
    val level: Int?,
    val school: String?,
    val category: String?,
    val subcategory: String?,
    val rarity: String?,
    val cr: Double?,
)

data class KindCount(val kind: String, val n: Int)

data class CategoryCount(val category: String?, val n: Int)

/**
 * The compendium database (plan D1, D6): the two tables above and the one DAO. Room's `version` stays 1
 * for good — `buildDatabase` builds without migrations, so a schema change is a [SCHEMA_VERSION] bump,
 * which changes [FILE_NAME]; the new file imports from scratch and [StaleDbFiles] removes the old one.
 * User data (M3) lives in a separate `grimoire.db` and is never touched by this class.
 */
@Database(entities = [RecordRow::class, SearchRow::class], version = 1, exportSchema = false)
abstract class CompendiumDb : RoomDatabase() {
    abstract fun dao(): CompendiumDao

    companion object {
        /** Bump on any change to [RecordRow] or [SearchRow]; Room's `version` never moves. */
        const val SCHEMA_VERSION = 1

        /** The file `buildDatabase` opens — versioned so a schema bump is a fresh file, not a migration. */
        const val FILE_NAME = "compendium-v$SCHEMA_VERSION.db"
    }
}

/**
 * Best-effort deletion of compendium files left by other schema versions (plan D6). Room keeps its files
 * under `<data>/databases`, a sibling of `filesDir`; only `compendium-v<N>.db` and its `-wal`/`-shm`/
 * `-journal` sidecars with `N != current` are removed. Nothing else in the directory is touched, a missing
 * directory is not an error, and a file that will not delete is simply left — an orphan costs a few MB,
 * never a crash.
 */
object StaleDbFiles {
    private val VERSIONED = Regex("compendium-v(\\d+)\\.db(?:-wal|-shm|-journal)?")

    /** @return the names of the files deleted, in directory order */
    fun delete(filesDir: File, current: Int = CompendiumDb.SCHEMA_VERSION): List<String> {
        val databases = filesDir.parentFile?.resolve("databases") ?: return emptyList()
        val entries = databases.listFiles() ?: return emptyList()
        val deleted = ArrayList<String>()
        for (file in entries) {
            val match = VERSIONED.matchEntire(file.name) ?: continue
            if (match.groupValues[1].toIntOrNull() == current) continue
            if (runCatching { file.delete() }.getOrDefault(false)) deleted += file.name
        }
        return deleted
    }
}
