package dev.tyler.grimoire.compendium

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The state machine behind `CompendiumStore` (plan D7, D8): `ensure` starts at most one run at a time,
 * `Checking` is set synchronously before the run, progress ticks become `Importing(done, total)`, a finished
 * run is `Ready` (sticky — every later `ensure` is a no-op) and a thrown run is `Failed(reason)` from which
 * the next `ensure` retries. The run itself is a lambda here; on device it is `AssetImporter.ensure()` over
 * Room and DataStore. Runs block on a [CompletableDeferred] so every transition is observed deterministically.
 */
class ImportGateTest {
    private class Rig {
        val lines = CopyOnWriteArrayList<String>()
        val gate = ImportGate(CoroutineScope(SupervisorJob() + Dispatchers.Default)) { lines += it }
        var runs = 0

        fun await(predicate: (ImportState) -> Boolean): ImportState =
            runBlocking { withTimeout(5_000) { gate.state.first(predicate) } }

        /** Starts a run that reports [ticks] and then parks until [release] completes; [started] completes after the ticks. */
        fun start(started: CompletableDeferred<Unit>, release: CompletableDeferred<ImportResult>, vararg ticks: Pair<Int, Int>): Boolean =
            gate.ensure { onProgress ->
                runs++
                for ((done, total) in ticks) onProgress(done, total)
                started.complete(Unit)
                release.await()
            }
    }

    private val imported = ImportResult.Imported(rows = 1992, decodeMs = 1200, insertMs = 500, totalMs = 1800)

    @Test
    fun startsIdle() {
        assertEquals(ImportState.Idle, Rig().gate.state.value, "before the first ensure")
    }

    @Test
    fun ensureMovesToCheckingSynchronouslyAndRunsExactlyOnceUntilTheRunFinishes() {
        val rig = Rig()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<ImportResult>()
        assertTrue(rig.start(started, release), "the first ensure starts a run")
        assertEquals(ImportState.Checking, rig.gate.state.value, "Checking is set before ensure returns")
        runBlocking { started.await() }
        assertEquals(ImportState.Checking, rig.gate.state.value, "still Checking while the run has not ticked")
        assertFalse(rig.gate.ensure { error("must not run") }, "a second ensure while a run is in flight does nothing")
        assertEquals(1, rig.runs, "one run")
        release.complete(ImportResult.Skipped(1992))
        assertEquals(ImportState.Ready, rig.await { it == ImportState.Ready }, "a skipped run ends Ready")
    }

    @Test
    fun progressTicksBecomeImportingAndACompletedImportEndsReady() {
        val rig = Rig()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<ImportResult>()
        rig.start(started, release, 1 to 22, 3 to 22)
        runBlocking { started.await() }
        assertEquals(ImportState.Importing(3, 22), rig.gate.state.value, "the last tick is the state")
        assertFalse(rig.gate.ensure { error("must not run") }, "ensure while Importing does nothing")
        release.complete(imported)
        assertEquals(ImportState.Ready, rig.await { it == ImportState.Ready }, "Ready after the import")
    }

    @Test
    fun readyIsStickyAndEveryLaterEnsureIsANoOp() {
        val rig = Rig()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<ImportResult>()
        rig.start(started, release)
        release.complete(imported)
        rig.await { it == ImportState.Ready }
        for (i in 1..3) {
            assertFalse(rig.gate.ensure { error("must not run") }, "ensure #$i after Ready does nothing")
            assertEquals(ImportState.Ready, rig.gate.state.value, "still Ready after ensure #$i")
        }
        assertEquals(1, rig.runs, "the run happened once")
    }

    @Test
    fun aThrowingRunBecomesFailedWithItsMessageAndTheNextEnsureRetries() {
        val rig = Rig()
        assertTrue(rig.gate.ensure { throw IllegalStateException("compendium/feats.json is 1 bytes; index.json says 2") }, "the run starts")
        val failed = assertIs<ImportState.Failed>(rig.await { it is ImportState.Failed }, "a thrown run ends Failed")
        assertEquals("compendium/feats.json is 1 bytes; index.json says 2", failed.reason, "the exception message is the reason")
        assertEquals(listOf("compendium import failed: compendium/feats.json is 1 bytes; index.json says 2"), rig.lines, "the failure is logged")

        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<ImportResult>()
        assertTrue(rig.start(started, release), "ensure after Failed retries")
        assertEquals(ImportState.Checking, rig.gate.state.value, "Checking again")
        release.complete(imported)
        assertEquals(ImportState.Ready, rig.await { it == ImportState.Ready }, "the retry ends Ready")
        assertEquals(1, rig.runs, "the retry ran (the failing lambda was not counted)")
    }

    @Test
    fun anExceptionWithoutAMessageStillNamesItself() {
        val rig = Rig()
        rig.gate.ensure { throw IllegalStateException() }
        val failed = assertIs<ImportState.Failed>(rig.await { it is ImportState.Failed }, "Failed")
        assertTrue(failed.reason.isNotBlank(), "a blank reason would leave the screen empty")
        assertTrue("IllegalStateException" in failed.reason, "the exception's name stands in for a missing message: ${failed.reason}")
    }

    @Test
    fun requireReadyRefusesEveryStateButReadyAndNamesTheState() {
        // Each case arranges a rig into one non-Ready state and hands back the deferred that would let its run finish.
        val cases: List<Pair<String, (Rig) -> CompletableDeferred<ImportResult>?>> = listOf(
            "Idle" to { null },
            "Checking" to { rig ->
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<ImportResult>()
                rig.start(started, release)
                runBlocking { started.await() }
                release
            },
            "Importing" to { rig ->
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<ImportResult>()
                rig.start(started, release, 1 to 22, 3 to 22)
                runBlocking { started.await() }
                release
            },
            "Failed" to { rig ->
                rig.gate.ensure { throw IllegalStateException("boom") }
                rig.await { it is ImportState.Failed }
                null
            },
        )
        for ((name, arrange) in cases) {
            val rig = Rig()
            val release = arrange(rig)
            val before = rig.gate.state.value
            assertTrue(name in before.toString(), "$name: the rig is in the state the case names, got $before")
            val e = assertFailsWith<IllegalStateException>("$name refuses a reader") { rig.gate.requireReady() }
            assertTrue(before.toString() in e.message.orEmpty(), "$name: the current state is named in the message: ${e.message}")
            assertEquals(before, rig.gate.state.value, "$name: requireReady does not move the state")
            release?.complete(imported)
        }

        val rig = Rig()
        rig.gate.ensure { imported }
        rig.await { it == ImportState.Ready }
        rig.gate.requireReady()
        assertEquals(ImportState.Ready, rig.gate.state.value, "Ready passes and stays Ready")
    }

    @Test
    fun theLogLineNamesTheImportBucketsOrTheReadyRowCount() {
        assertEquals(
            "compendium import rows=1992 decode=1200ms insert=500ms total=1800ms",
            ImportGate.describe(imported),
            "the import line the LP3 measurement reads",
        )
        assertEquals("compendium ready rows=1992", ImportGate.describe(ImportResult.Skipped(1992)), "the skip line carries no 'import'")
        val cases = mapOf(
            imported to "compendium import rows=1992 decode=1200ms insert=500ms total=1800ms",
            ImportResult.Skipped(1992) to "compendium ready rows=1992",
        )
        for ((result, line) in cases) {
            val rig = Rig()
            rig.gate.ensure { result }
            rig.await { it == ImportState.Ready }
            assertEquals(listOf(line), rig.lines.toList(), "$result is logged once as its line")
        }
    }
}
