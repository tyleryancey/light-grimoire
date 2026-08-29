package dev.tyler.grimoire.compendium

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan D6: the compendium database is file-versioned (`compendium-v$SCHEMA_VERSION.db`) because
 * `buildDatabase` exposes no migration API, so a schema bump opens a new file and the previous one is
 * deleted best-effort from `filesDir.parentFile/databases`. User data (M3's `grimoire.db`) and the
 * current file are never touched, and a missing directory is not an error.
 */
class StaleDbFilesTest {
    private fun sandbox(vararg names: String): File {
        val root = createTempDirectory("grimoire-stale").toFile()
        val files = root.resolve("files").also { it.mkdirs() }
        val databases = root.resolve("databases").also { it.mkdirs() }
        for (name in names) databases.resolve(name).writeText(name)
        return files
    }

    private fun remaining(filesDir: File): List<String> =
        filesDir.parentFile.resolve("databases").listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun schemaVersionAndFileNameAreTied() {
        assertEquals(1, CompendiumDb.SCHEMA_VERSION, "SCHEMA_VERSION")
        assertEquals("compendium-v1.db", CompendiumDb.FILE_NAME, "FILE_NAME carries the schema version")
    }

    /**
     * The tripwire behind ADR-0009's rule "any `RecordRow`/`SearchRow` change needs a `SCHEMA_VERSION` bump":
     * Room's `version` stays 1, so a changed column set with the same file name throws "Room cannot verify the
     * data integrity" at open on every installed phone. Both rows are built positionally and their data-class
     * `toString()` (property names in declaration order) is pinned beside the version, so adding, removing,
     * renaming or reordering a column fails here — at compile time or at this message — until the version moves
     * and the fingerprint is updated together. Annotation-only changes (index, primary key, FTS options) are not
     * caught; they still need the bump.
     */
    @Test
    fun aColumnChangeInEitherRowRequiresASchemaVersionBump() {
        val record = RecordRow(
            "spells", "fireball", "Fireball", "fireball", 0, 3, "evocation", "1 action", false, false,
            " sorcerer wizard ", null, null, null, null, null, null, null, "{}",
        )
        val search = SearchRow("spells", "fireball", "Fireball", "A bright streak")
        val message = "columns changed — bump CompendiumDb.SCHEMA_VERSION (a new compendium-v<N>.db, never a migration) and this fingerprint"
        assertEquals(
            "RecordRow(kind=spells, key=fireball, name=Fireball, sortName=fireball, position=0, level=3, " +
                "school=evocation, castingTime=1 action, concentration=false, ritual=false, " +
                "classList= sorcerer wizard , classKey=null, subclassKey=null, parentKey=null, category=null, " +
                "subcategory=null, rarity=null, cr=null, json={}) @ v1",
            "$record @ v${CompendiumDb.SCHEMA_VERSION}",
            "RecordRow $message",
        )
        assertEquals(
            "SearchRow(kind=spells, key=fireball, name=Fireball, body=A bright streak) @ v1",
            "$search @ v${CompendiumDb.SCHEMA_VERSION}",
            "SearchRow $message",
        )
    }

    @Test
    fun deletesExactlyTheOtherVersionsTrioAndKeepsTheCurrentFileAndUserData() {
        val filesDir = sandbox(
            "compendium-v0.db",
            "compendium-v0.db-wal",
            "compendium-v0.db-shm",
            "compendium-v1.db",
            "grimoire.db",
        )
        val deleted = StaleDbFiles.delete(filesDir)
        assertEquals(
            listOf("compendium-v0.db", "compendium-v0.db-shm", "compendium-v0.db-wal"),
            deleted.sorted(),
            "the v0 trio is reported as deleted",
        )
        assertEquals(listOf("compendium-v1.db", "grimoire.db"), remaining(filesDir), "the current file and user data survive")
    }

    @Test
    fun everySidecarOfAStaleVersionGoesAndEverySidecarOfTheCurrentVersionStays() {
        val cases = mapOf(
            "compendium-v0.db" to true,
            "compendium-v0.db-wal" to true,
            "compendium-v0.db-shm" to true,
            "compendium-v0.db-journal" to true,
            "compendium-v2.db" to true,
            "compendium-v2.db-journal" to true,
            "compendium-v12.db-wal" to true,
            "compendium-v1.db" to false,
            "compendium-v1.db-wal" to false,
            "compendium-v1.db-shm" to false,
            "compendium-v1.db-journal" to false,
            "compendium.db" to false,
            "compendium-v.db" to false,
            "compendium-vX.db" to false,
            "compendium-v0.db.bak" to false,
            "grimoire.db" to false,
            "grimoire.db-wal" to false,
        )
        val filesDir = sandbox(*cases.keys.toTypedArray())
        val deleted = StaleDbFiles.delete(filesDir).toSet()
        for ((name, stale) in cases) {
            assertEquals(stale, name in deleted, "$name reported deleted")
            assertEquals(!stale, filesDir.parentFile.resolve("databases").resolve(name).exists(), "$name still on disk")
        }
    }

    @Test
    fun missingDatabasesDirectoryIsEmptyAndDoesNotThrow() {
        val filesDir = createTempDirectory("grimoire-stale").toFile().resolve("files").also { it.mkdirs() }
        assertFalse(filesDir.parentFile.resolve("databases").exists(), "no databases directory")
        assertEquals(emptyList(), StaleDbFiles.delete(filesDir), "nothing to delete")
    }

    @Test
    fun emptyDatabasesDirectoryIsEmpty() {
        val filesDir = sandbox()
        assertEquals(emptyList(), StaleDbFiles.delete(filesDir), "nothing to delete")
        assertTrue(filesDir.parentFile.resolve("databases").isDirectory, "the directory itself stays")
    }

    @Test
    fun aFilesDirWithoutAParentIsEmptyAndDoesNotThrow() {
        assertEquals(emptyList(), StaleDbFiles.delete(File("files")), "no parent, nothing to walk")
    }

    @Test
    fun aSecondRunFindsNothingLeft() {
        val filesDir = sandbox("compendium-v0.db", "compendium-v0.db-wal", "compendium-v1.db")
        assertEquals(2, StaleDbFiles.delete(filesDir).size, "first run deletes the stale pair")
        assertEquals(emptyList(), StaleDbFiles.delete(filesDir), "second run is a no-op")
        assertEquals(listOf("compendium-v1.db"), remaining(filesDir), "only the current file remains")
    }
}
