package dev.tyler.grimoire.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The tripwire on the character table's frozen columns, the counterpart of `compendium/StaleDbFilesTest`
 * — with the opposite remedy, which is why it is worth its own file.
 *
 * `SealedLightContext.buildDatabase` builds with no migrations, no destructive fallback and no asset
 * seeding, so a changed column set fails at open with "Room cannot verify the data integrity", on every
 * phone that already installed the tool, with no API anywhere to catch it. There is no way to ship out of
 * that: the compendium answers it by opening a new file name and re-importing 1 992 rows from the bundled
 * assets in 2.3 s, and this database has nothing to re-import from — it *is* the player's record. **So
 * the fix for a needed column is never a Room `version` bump and never a new empty file.** It is a second
 * file (`characters-v2.db`) that is *copied into*: read every row out of `grimoire.db`, re-insert `json`
 * byte-for-byte, derive the new column from the decoded document, and delete the old file only after the
 * copy commits (docs/DATA-MODEL.md §Room mapping).
 *
 * The scan bans the vocabulary that would let a test read a class's columns back at run time, so the
 * fingerprint below is the mechanism: [CharacterRow] is built positionally and its data-class `toString`
 * prints property names in declaration order, so adding, removing, renaming or reordering a column fails
 * here — at compile time or on this message. Annotation-only changes (a primary key, an index) are not
 * caught and carry exactly the same consequence.
 */
class GrimoireDbShapeTest {
    /**
     * The one unshippable change a column fingerprint cannot see. A Room `version` bump throws at open
     * with the same message and the same reach as an added column, so it is pinned the only way it can
     * be: the annotation is written in terms of [GRIMOIRE_ROOM_VERSION], and moving it fails here.
     */
    @Test
    fun theRoomVersionNeverMoves() {
        assertEquals(
            1,
            GRIMOIRE_ROOM_VERSION,
            "the character database has no second version — evolution is Model.SCHEMA_VERSION inside the json column",
        )
    }

    /**
     * The table name, pinned because a future `characters-v2.db` reads its rows out of it by name — and
     * standing in for the one unshippable change **nothing here can check**: a second `@Entity` added to
     * [GrimoireDb]'s `entities` array. That throws "Room cannot verify the data integrity" at open on every
     * installed phone, exactly like a column change, and it cannot be asserted: reading an annotation back
     * needs `.java.` or `kotlin.reflect`, both banned by the plugin scan, and Room cannot run here to be
     * asked. The guard is [GrimoireDb]'s own KDoc; this test is the place a reader arrives at from it.
     */
    @Test
    fun thereIsOneTableAndASecondEntityWouldBeUnshippable() {
        assertEquals(
            "characters",
            CHARACTERS_TABLE,
            "the one table of grimoire.db — a second entity on this database is as unshippable as a column " +
                "(the journal gets its own file, ADR-0010) and no test can catch it: see GrimoireDb's KDoc",
        )
    }

    @Test
    fun theFileNameIsPlainAndUnversioned() {
        assertEquals(
            "grimoire.db",
            GrimoireDb.FILE_NAME,
            "the character file is never version-named — a new name would abandon the player's characters",
        )
    }

    @Test
    fun aColumnChangeMeansASecondFileCopiedInto() {
        val row = CharacterRow(
            "9f1c4b2e-0000-4000-8000-000000000001",
            "Brother Aldric",
            "Cleric 5 · Hill Dwarf",
            1_756_000_000_000,
            "{}",
        )
        assertEquals(
            "CharacterRow(id=9f1c4b2e-0000-4000-8000-000000000001, name=Brother Aldric, " +
                "summary=Cleric 5 · Hill Dwarf, updatedAt=1756000000000, json={})",
            row.toString(),
            "the characters columns are frozen: a change needs a characters-v2.db copied out of grimoire.db " +
                "(docs/DATA-MODEL.md), never a Room migration and never an empty new file",
        )
    }

    /**
     * [CharacterSummaryRow] is a projection, not a column set — it is free to change with the SELECT in
     * [CharacterDao.summaries]. Pinned only so a change to it is a deliberate one, in step with the query.
     */
    @Test
    fun theHomeProjectionCarriesNoDocument() {
        val row = CharacterSummaryRow("id", "Brother Aldric", "Cleric 5 · Hill Dwarf", 1_756_000_000_000)
        assertEquals(
            "CharacterSummaryRow(id=id, name=Brother Aldric, summary=Cleric 5 · Hill Dwarf, updatedAt=1756000000000)",
            row.toString(),
            "the S0 list never reads json",
        )
    }
}
