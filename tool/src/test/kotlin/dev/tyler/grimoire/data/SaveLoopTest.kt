package dev.tyler.grimoire.data

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.Model
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Five seconds is a hang, not a slow machine — every wait in this file is a handshake that either happens at
 * once or never (`ImportGateTest`'s rule).
 */
private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(5_000) { block() } }

/**
 * The composed save path: [SaveBuffer]'s deadlines driven by a real coroutine, with `sleep` injected so the
 * test moves time instead of spending it. The clock is the authority here — a released sleep only makes the
 * loop *look* again, so an early or spurious release can never write something that was not due.
 *
 * The two properties worth the machinery: a flush writes with the clock frozen (the screen is going away and
 * the debounce has stopped being a kindness), and a write that throws neither escapes nor kills the loop —
 * the loop is the only writer the process has, and a dead one would lose every later save in silence.
 */
class SaveLoopTest {
    private class Rig {
        val clock = ManualClock()
        val buffer = SaveBuffer<String, String>(maxAttempts = 3, now = clock::now)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writes = Channel<String>(Channel.UNLIMITED)
        val sleeps = Channel<Long>(Channel.UNLIMITED)
        val wake = Channel<Unit>(Channel.UNLIMITED)
        val logs = Channel<String>(Channel.UNLIMITED)

        @Volatile
        var failOn: String? = null

        val loop = SaveLoop(
            scope = scope,
            buffer = buffer,
            write = { value ->
                if (value == failOn) {
                    failOn = null
                    throw IllegalStateException("disk is full")
                }
                writes.send(value)
            },
            sleep = { ms ->
                sleeps.send(ms)
                wake.receive()
            },
            log = { logs.trySend(it) },
        )

        /** Let the current sleep return. Never blocks: what is due is the clock's business, not this token's. */
        fun release() {
            wake.trySend(Unit)
        }
    }

    @Test
    fun aSaveLandsOnceItsDeadlineHasPassed() {
        val rig = Rig()
        rig.buffer.put("a", "v1")
        rig.loop.start()
        assertEquals(400L, await { rig.sleeps.receive() }, "the loop waits out the debounce window")
        assertNull(rig.writes.tryReceive().getOrNull(), "nothing is written while the window is open")

        rig.clock.advance(400)
        rig.release()
        assertEquals("v1", await { rig.writes.receive() }, "the value lands after the deadline")
    }

    /** What `flush()` is: the deadline moves to now and the wait is cut short, without the clock moving. */
    @Test
    fun aSignalCutsARunningWaitShortSoAFlushDoesNotWaitOutTheWindow() {
        val rig = Rig()
        rig.buffer.put("a", "v1")
        rig.loop.start()
        assertEquals(400L, await { rig.sleeps.receive() }, "the loop is waiting")

        rig.buffer.flushNow()
        rig.loop.signal()
        assertEquals("v1", await { rig.writes.receive() }, "written with the clock frozen at t=0")
    }

    @Test
    fun anIdleLoopParksAndWakesOnASignal() {
        val rig = Rig()
        rig.loop.start()
        rig.buffer.put("a", "v1")
        rig.clock.advance(400)
        rig.loop.signal()
        assertEquals("v1", await { rig.writes.receive() }, "a parked loop wakes and drains")
    }

    /**
     * `enqueue` and `flush` both call `start()`, so every save of the session calls it — a second loop per
     * save would be a coroutine leak and two writers racing over one buffer. Each loop that started would
     * ask for its own sleep, so the settle below counts them.
     */
    @Test
    fun startIsIdempotent() {
        val rig = Rig()
        rig.buffer.put("a", "v1")
        repeat(3) { rig.loop.start() }
        await { delay(100) }
        assertEquals(400L, await { rig.sleeps.receive() }, "one loop asked to wait")
        assertNull(rig.sleeps.tryReceive().getOrNull(), "a second loop would have asked for its own sleep")

        rig.clock.advance(400)
        rig.release()
        assertEquals("v1", await { rig.writes.receive() }, "the value lands once")
        assertNull(rig.writes.tryReceive().getOrNull(), "and only once")
    }

    @Test
    fun aThrowingWriteIsLoggedAndRetriedAndTheLoopSurvivesIt() {
        val rig = Rig()
        rig.failOn = "v1"
        rig.buffer.put("a", "v1")
        rig.loop.start()
        assertEquals(400L, await { rig.sleeps.receive() }, "the first attempt waits out the debounce")
        rig.clock.advance(400)
        rig.release()

        assertEquals("save failed, retrying: disk is full", await { rig.logs.receive() }, "the throw is logged, not raised")
        assertEquals(400L, await { rig.sleeps.receive() }, "the retry waits one window of backoff")
        rig.clock.advance(400)
        rig.release()
        assertEquals("v1", await { rig.writes.receive() }, "the retry lands the same value")

        rig.buffer.put("b", "v2")
        rig.loop.signal()
        assertEquals(400L, await { rig.sleeps.receive() }, "the loop is still the one waiting")
        rig.clock.advance(400)
        rig.release()
        assertEquals("v2", await { rig.writes.receive() }, "a later save still lands")
    }

    @Test
    fun aWriteThatKeepsThrowingIsGivenUpOnAndSaidSo() {
        val rig = Rig()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val clock = ManualClock()
        val buffer = SaveBuffer<String, String>(maxAttempts = 3, now = clock::now)
        val sleeps = Channel<Long>(Channel.UNLIMITED)
        val wake = Channel<Unit>(Channel.UNLIMITED)
        val logs = Channel<String>(Channel.UNLIMITED)
        val loop = SaveLoop<String, String>(
            scope = scope,
            buffer = buffer,
            write = { throw IllegalStateException("disk is full") },
            sleep = { ms ->
                sleeps.send(ms)
                wake.receive()
            },
            log = { logs.trySend(it) },
        )
        buffer.put("a", "v1")
        loop.start()
        repeat(3) { attempt ->
            assertEquals(400L, await { sleeps.receive() }, "attempt ${attempt + 1} waits")
            clock.advance(400)
            wake.trySend(Unit)
            val expected = if (attempt < 2) "save failed, retrying: disk is full" else "save failed, giving up: disk is full"
            assertEquals(expected, await { logs.receive() }, "attempt ${attempt + 1}")
        }
        assertEquals(0, buffer.size, "nothing is left pending after the last attempt")
    }

    // ---- DebouncedSaver: the buffer, the loop and the map that keeps a debounced write invisible ---------

    private class SaverRig {
        val clock = ManualClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** A write announces itself here and then blocks on [finish], so a test can act mid-write. */
        val started = Channel<PendingSave>(Channel.UNLIMITED)
        val finish = Channel<Unit>(Channel.UNLIMITED)
        val sleeps = Channel<Long>(Channel.UNLIMITED)
        val wake = Channel<Unit>(Channel.UNLIMITED)

        val saver = DebouncedSaver(
            scope = scope,
            now = clock::now,
            log = {},
            delayMs = SaveBuffer.DEBOUNCE_MS,
            sleep = { ms ->
                sleeps.send(ms)
                wake.receive()
            },
        ) { save ->
            started.send(save)
            finish.receive()
        }

        fun release() {
            wake.trySend(Unit)
        }

        /** Wait out the debounce and let the loop reach the write. */
        fun runOut() {
            assertEquals(400L, await { sleeps.receive() }, "the saver waits out the debounce")
            clock.advance(400)
            release()
        }
    }

    private val aldric = Model.decode(Fixtures.character("cleric-5-life"))

    @Test
    fun aPendingSaveIsVisibleUntilItsOwnWriteHasReturned() {
        val rig = SaverRig()
        rig.saver.enqueue(PendingSave(aldric, 1_000))
        assertEquals(aldric, rig.saver.pending()[aldric.id]?.character, "visible the moment it is enqueued")

        rig.runOut()
        assertEquals(aldric, await { rig.started.receive() }.character, "the loop writes it")
        assertEquals(
            aldric,
            rig.saver.pending()[aldric.id]?.character,
            "still visible while the write is in flight — a reload here must not read the older row",
        )

        rig.finish.trySend(Unit)
        await { while (rig.saver.pending().isNotEmpty()) delay(5) }
        assertTrue(rig.saver.pending().isEmpty(), "cleared once the write returned, and not before")
    }

    @Test
    fun aSaveMadeDuringAWriteSurvivesThatWriteFinishing() {
        val rig = SaverRig()
        rig.saver.enqueue(PendingSave(aldric, 1_000))
        rig.runOut()
        await { rig.started.receive() }

        val renamed = aldric.copy(name = "Aldric the Grey")
        rig.saver.enqueue(PendingSave(renamed, 2_000))
        rig.finish.trySend(Unit)

        assertEquals(400L, await { rig.sleeps.receive() }, "the loop is now waiting on the newer save")
        assertEquals(
            renamed,
            rig.saver.pending()[aldric.id]?.character,
            "the older write clears only its own value",
        )

        rig.clock.advance(400)
        rig.release()
        assertEquals(renamed, await { rig.started.receive() }.character, "and the newer value is written after it")
        rig.finish.trySend(Unit)
    }

    @Test
    fun flushWritesWithoutWaitingOutTheWindow() {
        val rig = SaverRig()
        rig.saver.enqueue(PendingSave(aldric, 1_000))
        assertEquals(400L, await { rig.sleeps.receive() }, "the debounce started")

        rig.saver.flush()
        assertEquals(aldric, await { rig.started.receive() }.character, "flushed with the clock frozen")
        rig.finish.trySend(Unit)
    }

    /** A writer that refuses until [failing] is cleared — the retry budget, and what is left after it. */
    private class FlakyRig {
        val clock = ManualClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sleeps = Channel<Long>(Channel.UNLIMITED)
        val wake = Channel<Unit>(Channel.UNLIMITED)
        val tried = Channel<PendingSave>(Channel.UNLIMITED)
        val landed = Channel<PendingSave>(Channel.UNLIMITED)

        @Volatile
        var failing = true

        val saver = DebouncedSaver(
            scope = scope,
            now = clock::now,
            log = {},
            delayMs = SaveBuffer.DEBOUNCE_MS,
            sleep = { ms ->
                sleeps.send(ms)
                wake.receive()
            },
        ) { save ->
            tried.send(save)
            if (failing) throw IllegalStateException("disk is full")
            landed.send(save)
        }

        /** Wait out one window and let the loop reach the write. */
        fun runOut() {
            assertEquals(400L, await { sleeps.receive() }, "the loop waits out a window")
            clock.advance(400)
            wake.trySend(Unit)
        }
    }

    /**
     * The gap a spent retry budget used to leave. Three attempts one window apart is 1.2 s, and a phone that
     * took 11.5 s to open a database while dozing (CLAUDE.md) can burn all of it on failures that would have
     * succeeded a second later. The value stays visible — the player is looking at their finished rest — but
     * it had left the buffer, and `flush()` can only reach the buffer: back out of the screen, let the
     * process die, and the rest is gone with nothing but a logcat line. So a flush puts it back first.
     */
    @Test
    fun aFlushGivesASaveThatSpentItsAttemptsAnotherChance() {
        val rig = FlakyRig()
        rig.saver.enqueue(PendingSave(aldric, 1_000))
        repeat(3) { attempt ->
            rig.runOut()
            assertEquals(aldric, await { rig.tried.receive() }.character, "attempt ${attempt + 1} is made")
        }
        assertNull(rig.landed.tryReceive().getOrNull(), "three attempts and nothing has landed")
        assertEquals(
            aldric,
            rig.saver.pending()[aldric.id]?.character,
            "the player is still shown what they did, which is exactly why it must still be reachable",
        )

        rig.failing = false
        rig.saver.flush()
        assertEquals(aldric, await { rig.tried.receive() }.character, "the flush put it back into the buffer")
        assertEquals(aldric, await { rig.landed.receive() }.character, "and this time it lands")
        await { while (rig.saver.pending().isNotEmpty()) delay(5) }
        assertTrue(rig.saver.pending().isEmpty(), "and stops being pending once it has")
    }

    /** The flush re-arm must not disturb a value the buffer is already holding: that one is at least as new. */
    @Test
    fun aFlushDoesNotWriteAValueTwiceWhenTheBufferStillHoldsIt() {
        val rig = SaverRig()
        rig.saver.enqueue(PendingSave(aldric, 1_000))
        assertEquals(400L, await { rig.sleeps.receive() }, "the debounce started")

        rig.saver.flush()
        assertEquals(aldric, await { rig.started.receive() }.character, "written once")
        rig.finish.trySend(Unit)
        await { while (rig.saver.pending().isNotEmpty()) delay(5) }
        assertNull(rig.started.tryReceive().getOrNull(), "and only once — the pending value was not also re-armed")
    }

    @Test
    fun aDroppedSaveIsForgottenAndNeverWritten() {
        val rig = SaverRig()
        rig.saver.enqueue(PendingSave(aldric, 1_000))
        rig.saver.drop(aldric.id)
        assertTrue(rig.saver.pending().isEmpty(), "a dropped save is invisible to reads too")

        rig.clock.advance(400)
        rig.saver.flush()
        assertNull(rig.started.tryReceive().getOrNull(), "there is nothing left for the loop to write")
    }
}
