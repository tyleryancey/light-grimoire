package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * index.json is the importer's only source of truth for what ships (ADR-0002, plan D5): the stamp is derived
 * from its bundleSha256 and the row count from its per-file counts. These tests pin that the index decodes
 * strictly, names exactly the 22 kinds the Kotlin side knows, and still describes the bytes beside it.
 */
class IndexIntegrityTest {
    private val index = Fixtures.compendiumIndex()

    @Test
    fun indexDecodesStrictlyWithTheBundleConstants() {
        assertEquals(1, index.schemaVersion, "schemaVersion")
        assertEquals("2014", index.edition, "edition")
        assertEquals("5.1", index.srdVersion, "srdVersion")
        assertEquals("CC-BY-4.0", index.license, "license")
        assertEquals("assets/legal/ATTRIBUTION.md", index.attribution, "attribution")
        assertEquals(64, index.bundleSha256.length, "bundleSha256 is a hex sha256")
        assertTrue(index.bundleSha256.all { it in '0'..'9' || it in 'a'..'f' }, "bundleSha256 is lowercase hex")
        assertTrue(index.sources.isNotEmpty(), "pinned sources are carried along")
    }

    @Test
    fun theTwentyTwoIndexedFilesAreExactlyTheKinds() {
        assertEquals(22, index.files.size, "indexed file count")
        assertEquals(Kind.entries.map { it.file }.toSet(), index.files.keys, "index.files == Kind files")
        assertEquals(Kind.entries.size, Kind.entries.map { it.file }.toSet().size, "Kind files are distinct")
    }

    @Test
    fun everyIndexedFileMatchesItsRecordedSizeAndSha256OnDisk() {
        for ((name, meta) in index.files) {
            val bytes = Fixtures.compendiumBytes(name)
            assertEquals(meta.bytes, bytes.size, "$name bytes")
            assertEquals(meta.sha256, BundleHash.sha256(bytes), "$name sha256")
            assertTrue(meta.count > 0, "$name count is positive")
        }
    }

    @Test
    fun bundleSha256IsTheHashOfTheSortedNameShaPairs() {
        assertEquals(BundleHash.of(index.files), index.bundleSha256, "bundleSha256 == sha256(join(name:sha) sorted)")
    }

    @Test
    fun totalIsTheSumOfThePerFileCounts() {
        assertEquals(index.files.values.sumOf { it.count }, index.total, "total == Σ files[*].count")
        assertEquals(1992, index.total, "SRD 5.1 bundle record total")
    }
}
