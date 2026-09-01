package dev.tyler.grimoire.data

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

/**
 * Room's schema version for [GrimoireDb], which can never move. A `const` used inside the annotation
 * itself so that bumping the version means editing this line, and GrimoireDbShapeTest fails until whoever
 * did it has read why (a bump throws at open on every phone that already installed the tool, exactly like
 * an added column, and with the same remedy: a second file copied into, never a migration).
 */
const val GRIMOIRE_ROOM_VERSION = 1

/**
 * The one table [GrimoireDb] creates, and the only one it ever will. A `const` used inside the annotation
 * for the same reason the version is: the name is what a future `characters-v2.db` reads its rows out of,
 * and GrimoireDbShapeTest pins it beside the version so a rename is a deliberate act.
 *
 * It does not — and cannot — pin the `entities` array itself: reading an annotation back needs `.java.` or
 * `kotlin.reflect`, both banned by the plugin scan, and Room cannot run in a JVM test to be asked. Adding a
 * second `@Entity` to [GrimoireDb] is as unshippable as adding a column (the same "Room cannot verify the
 * data integrity" at open, on every phone that already installed the tool), so the guard is this file's
 * class KDoc and the test that carries the same sentence.
 */
const val CHARACTERS_TABLE = "characters"

/**
 * One stored character (docs/DATA-MODEL.md §Room mapping). [json] is the whole schema document — the
 * durable record, and the only thing that survives a file bump — while [name], [summary] and [updatedAt]
 * are denormalised copies of what S0's list draws, so the Home screen never decodes six documents to
 * paint two lines each.
 *
 * Nothing here is derived at read time: [summary] is written by [Summaries.summaryOf] at every save, so
 * a stale copy is impossible without a save that skipped it.
 */
@Entity(tableName = CHARACTERS_TABLE)
data class CharacterRow(
    /** [Ids.new] — a UUID string (docs/DATA-MODEL.md §5). */
    @PrimaryKey val id: String,
    /** `Character.name`, for the first line of an S0 row. */
    val name: String,
    /** [Summaries.summaryOf] — "Cleric 5 · Hill Dwarf", the second line of an S0 row. */
    val summary: String,
    /** Epoch milliseconds of the last save; S0 lists most-recently-touched first. */
    val updatedAt: Long,
    /** `Model.encode` of the character. Last column, and no list query ever selects it. */
    val json: String,
)

/**
 * The S0 projection — everything the Home list draws and never [CharacterRow.json]. A projection, not an
 * entity: adding a field here (and to the spelled-out SELECT in [CharacterDao.summaries]) is an ordinary
 * change, unlike a column of [CharacterRow].
 */
data class CharacterSummaryRow(val id: String, val name: String, val summary: String, val updatedAt: Long)

/**
 * The player's own database — the characters they transcribed, and nothing else.
 *
 * **Only `characters` lives here.** The journal gets its own `journal.db` in M5 (ADR-0010, forthcoming);
 * docs/DATA-MODEL.md carries the decision and the reason: `SealedLightContext.buildDatabase` is a plain
 * `Room.databaseBuilder(...).build()` with no `addMigrations`, no `fallbackToDestructiveMigration` and no
 * `createFromAsset`, so an entity can never be added to a database after it first ships on a phone.
 * Freezing six unexercised journal entities onto the file that holds a player's characters was the
 * alternative, and it was refused.
 *
 * **The `entities` array below is load-bearing and no test can read it.** Adding a second `@Entity` to it
 * is exactly as unshippable as adding a column to [CharacterRow] — the same "Room cannot verify the data
 * integrity" thrown at open, on every phone that already has the tool — and it is the change most likely to
 * be made by someone who has not read this far, because a journal entity looks like it belongs beside a
 * character. Nothing catches it: an annotation cannot be read back without `.java.` or `kotlin.reflect`
 * (both banned by the plugin scan) and Room cannot run in a JVM test. If a second entity ever has to share
 * this file, it goes through the same copy-into procedure a column does.
 *
 * **This file is deliberately NOT version-named, unlike `compendium-v1.db`.** That trick works for the
 * compendium because the file is disposable: a new name imports itself from the bundled assets in 2.3 s
 * and [dev.tyler.grimoire.compendium.StaleDbFiles] deletes the old one. This file has no source to
 * re-import from — it *is* the source — so deleting it would delete the player's characters.
 *
 * The consequence is that the columns of [CharacterRow] are **frozen at creation**. Character evolution
 * goes entirely through `Model.SCHEMA_VERSION` + `Model.migrate(json)` inside the [CharacterRow.json]
 * column, never a Room migration and never a Room `version` bump — either one throws at open, on every
 * installed phone, with no API to catch it. If the Home list ever needs a hot column it cannot derive
 * from the document, that is a second file (`characters-v2.db`) which is **copied into, not started
 * empty**: read every row out of this one, re-insert `json` byte-for-byte, derive the new column from the
 * decoded document, and delete this file only after the copy commits (docs/DATA-MODEL.md — a bump that
 * fails halfway must leave this file intact and retry on the next launch). GrimoireDbShapeTest is the
 * tripwire that makes a forgotten column change fail on a laptop instead of on a player's phone.
 *
 * No index: six rows at most, and an index would itself be a schema change that can never be made.
 */
@Database(entities = [CharacterRow::class], version = GRIMOIRE_ROOM_VERSION, exportSchema = false)
abstract class GrimoireDb : RoomDatabase() {
    abstract fun characters(): CharacterDao

    companion object {
        /** The file `buildDatabase` opens. Never versioned — see the class KDoc. */
        const val FILE_NAME = "grimoire.db"
    }
}
