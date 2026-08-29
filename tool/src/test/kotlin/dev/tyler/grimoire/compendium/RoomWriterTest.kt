package dev.tyler.grimoire.compendium

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * The production [CompendiumWriter] (plan D4, D7): `db.withTransaction` over the one DAO. Room cannot run on
 * the JVM, so the transaction is injected — a function that runs the block and reports begin/commit/rollback —
 * and the DAO is [FakeCompendiumDao]. What is under test is the mapping the device relies on: the sink's
 * `clear` empties both tables, its `insert` fills both, the counts read both, and the block runs inside the
 * transaction with any exception propagating out of it. The last two tests run the real [AssetImporter]
 * through this writer over the whole bundle — the one composition the device import actually executes.
 */
class RoomWriterTest {
    private class Rig {
        val dao = FakeCompendiumDao()
        val events = ArrayList<String>()
        val writer = RoomWriter(dao) { block ->
            events += "begin"
            try {
                block()
            } catch (e: Throwable) {
                events += "rollback"
                throw e
            }
            events += "commit"
        }
    }

    @Test
    fun countsReadBothTablesThroughTheDao() = runBlocking {
        val rig = Rig()
        rig.dao.records += RecordRow("spells", "a", "A", "a", 0, null, null, null, null, null, null, null, null, null, null, null, null, null, "{}")
        rig.dao.search += SearchRow("spells", "a", "A", "")
        rig.dao.search += SearchRow("spells", "b", "B", "")
        assertEquals(1, rig.writer.count(), "count reads records")
        assertEquals(2, rig.writer.searchCount(), "searchCount reads search_index")
        assertEquals(listOf("count", "searchCount"), rig.dao.calls, "one DAO query each")
        assertEquals(emptyList(), rig.events, "counting opens no transaction")
    }

    @Test
    fun replaceAllRunsTheBlockInsideTheTransactionAndTheSinkWritesBothTables() = runBlocking {
        val rig = Rig()
        rig.dao.records += RecordRow("spells", "old", "Old", "old", 0, null, null, null, null, null, null, null, null, null, null, null, null, null, "{}")
        rig.dao.search += SearchRow("spells", "old", "Old", "")
        val record = RecordRow("conditions", "blinded", "Blinded", "blinded", 0, null, null, null, null, null, null, null, null, null, null, null, null, null, "{}")
        val search = SearchRow("conditions", "blinded", "Blinded", "cannot see")
        rig.writer.replaceAll { sink ->
            rig.events += "block"
            sink.clear()
            assertEquals(0, rig.dao.records.size, "clear empties records")
            assertEquals(0, rig.dao.search.size, "clear empties search_index")
            sink.insert(listOf(record), listOf(search))
        }
        assertEquals(listOf("begin", "block", "commit"), rig.events, "the block runs between begin and commit")
        assertEquals(listOf("clearRecords", "clearSearch", "insertRecords", "insertSearch"), rig.dao.calls, "clear hits both tables, then insert hits both")
        assertEquals(listOf(record), rig.dao.records, "the inserted record row")
        assertEquals(listOf(search), rig.dao.search, "the inserted search row")
    }

    @Test
    fun anExceptionInsideTheBlockPropagatesThroughTheTransaction() = runBlocking {
        val rig = Rig()
        val error = assertFailsWith<IllegalStateException>("the block's exception escapes replaceAll") {
            rig.writer.replaceAll { sink ->
                sink.clear()
                throw IllegalStateException("boom")
            }
        }
        assertEquals("boom", error.message, "the original exception")
        assertEquals(listOf("begin", "rollback"), rig.events, "the transaction saw the exception and did not commit")
    }

    @Test
    fun theImporterOverThisWriterFillsBothTablesFromTheBundle() = runBlocking {
        val rig = Rig()
        val ticks = ArrayList<Int>()
        val importer = AssetImporter(FakeAssetSource(), FakeMarker(), rig.writer) { done, _ -> ticks += done }
        val result = assertIs<ImportResult.Imported>(importer.ensure(), "a fresh store imports")
        assertEquals(1992, result.rows, "rows")
        assertEquals(1992, rig.dao.records.size, "records rows through the real sink")
        assertEquals(1992, rig.dao.search.size, "search_index rows through the real sink")
        assertEquals((1..22).toList(), ticks, "22 ticks")
        assertEquals(listOf("begin", "commit"), rig.events, "one transaction")
        val expectedCalls = listOf("clearRecords", "clearSearch") + List(22) { listOf("insertRecords", "insertSearch") }.flatten()
        assertEquals(expectedCalls, rig.dao.calls, "no counts without a stamp; clear both, then 22 paired inserts")
        val fireball = rig.dao.records.single { it.kind == "spells" && it.key == "fireball" }
        assertEquals(3, fireball.level, "fireball landed with its columns")
    }

    @Test
    fun aSecondRunOverThisWriterIsSkippedAfterCountingBothTables() = runBlocking {
        val rig = Rig()
        val marker = FakeMarker()
        AssetImporter(FakeAssetSource(), marker, rig.writer) { _, _ -> }.ensure()
        rig.dao.calls.clear()
        rig.events.clear()
        val result = AssetImporter(FakeAssetSource(), marker, rig.writer) { _, _ -> }.ensure()
        assertEquals(ImportResult.Skipped(1992), result, "stamp and counts match")
        assertEquals(listOf("count", "searchCount"), rig.dao.calls, "only the two counts run")
        assertEquals(emptyList(), rig.events, "no transaction")
    }
}
