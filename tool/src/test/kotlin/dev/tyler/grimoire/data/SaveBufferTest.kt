package dev.tyler.grimoire.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The debounce, on a clock the test moves by hand — 400 ms of behaviour pinned at zero real delay, which is
 * the whole reason the timing lives in a pure object instead of inside the coroutine that acts on it.
 *
 * What these cases are really defending: a burst of taps is one write of the *last* value (never a write of
 * an intermediate one), a save that failed is retried but never at the cost of reverting a newer edit, and a
 * flush is instant. Everything the milestone's "kill the process mid-rest and relaunch: nothing lost" turns
 * on is either here or in the loop that reads these deadlines.
 */
class SaveBufferTest {
    private val clock = ManualClock()
    private val buffer = SaveBuffer<String, String>(
        delayMs = SaveBuffer.DEBOUNCE_MS,
        maxAttempts = 3,
        maxWaitMs = SaveBuffer.MAX_WAIT_MS,
        now = clock::now,
    )

    @Test
    fun theWindowIsTheOneTheDocsQuote() {
        assertEquals(400L, SaveBuffer.DEBOUNCE_MS, "docs/DATA-MODEL.md and docs/ARCHITECTURE.md both say 400 ms")
    }

    @Test
    fun theCeilingIsFiveWindows() {
        assertEquals(2_000L, SaveBuffer.MAX_WAIT_MS, "the longest an edit may sit unwritten while the player keeps going")
        assertEquals(
            5L,
            SaveBuffer.MAX_WAIT_MS / SaveBuffer.DEBOUNCE_MS,
            "the ceiling is five windows: brought near the window it stops being a ceiling and becomes a second, " +
                "shorter debounce, and every burst is written mid-tap instead of coalesced",
        )
    }

    @Test
    fun anEmptyBufferHasNothingDueAndNothingToTake() {
        assertNull(buffer.dueIn(), "nothing pending")
        assertEquals(emptyList(), buffer.take(), "nothing to write")
        assertEquals(0, buffer.size, "empty")
    }

    @Test
    fun nothingIsWrittenBeforeTheWindowExpires() {
        buffer.put("a", "v1")
        assertEquals(400L, buffer.dueIn(), "the deadline is one window away")
        assertEquals(emptyList(), buffer.take(), "nothing is due at t=0")
        clock.advance(399)
        assertEquals(1L, buffer.dueIn(), "one millisecond short")
        assertEquals(emptyList(), buffer.take(), "still nothing at t=399")
        clock.advance(1)
        assertEquals(0L, buffer.dueIn(), "due at t=400")
        assertEquals(listOf("a" to "v1"), buffer.take(), "the value is written at t=400")
        assertNull(buffer.dueIn(), "take emptied it")
    }

    /** The burst: a second tap at t=200 re-arms to t=600, and the value from the first tap never exists. */
    @Test
    fun aSecondPutReArmsTheDeadlineAndTheFirstValueIsNeverWritten() {
        buffer.put("a", "v1")
        clock.advance(200)
        buffer.put("a", "v2")
        assertEquals(400L, buffer.dueIn(), "the deadline moved to t=600")
        assertEquals(1, buffer.size, "coalesced into one entry")
        clock.advance(200)
        assertEquals(emptyList(), buffer.take(), "t=400 was the first tap's deadline and it no longer exists")
        clock.advance(200)
        assertEquals(listOf("a" to "v2"), buffer.take(), "only the latest value is written, at t=600")
    }

    /**
     * The other half of the burst, and the reason the window slides against a ceiling rather than for ever.
     * A player correcting a big hit holds the wheel: a detent every ~150–200 ms, each one a `save`, each one
     * re-arming the deadline. With no ceiling nothing reaches SQLite until they stop, so a process killed
     * mid-correction loses the whole correction instead of the last window of it.
     */
    @Test
    fun aBurstThatNeverPausesIsWrittenAtTheCeilingAndNotWhenThePlayerStops() {
        buffer.put("a", "tap-0")
        for (tap in 1..9) {
            clock.advance(200)
            buffer.put("a", "tap-$tap")
        }
        assertEquals(200L, buffer.dueIn(), "at t=1800 the ceiling is what is left, not another full window")
        assertEquals(emptyList(), buffer.take(), "nine taps in and nothing has been written yet")

        clock.advance(200)
        assertEquals(0L, buffer.dueIn(), "due at the ceiling, whether or not the player has stopped")
        assertEquals(listOf("a" to "tap-9"), buffer.take(), "the latest value at t=2000, not every value since t=0")

        clock.advance(200)
        buffer.put("a", "tap-10")
        assertEquals(400L, buffer.dueIn(), "the ceiling is per stretch of pending: the next value gets a whole window")
    }

    /**
     * The non-obvious half of the ceiling. A retry is re-armed one window out, and its ceiling starts over —
     * carried across, the value's ceiling would already have passed and the buffer would hand it straight
     * back, turning backoff into a spin against a database that has just refused it.
     */
    @Test
    fun aRetryAfterTheCeilingHasFiredStillWaitsAFullWindowOfBackoff() {
        buffer.put("a", "v1")
        for (tap in 1..10) {
            clock.advance(200)
            buffer.put("a", "v1")
        }
        assertEquals(listOf("a" to "v1"), buffer.take(), "the ceiling handed it over at t=2000")

        assertTrue(buffer.failed("a", "v1"), "the write threw and the value is re-armed")
        assertEquals(400L, buffer.dueIn(), "a full window of backoff, not zero")
        assertEquals(emptyList(), buffer.take(), "the retry is not due the instant it failed")
        clock.advance(400)
        assertEquals(listOf("a" to "v1"), buffer.take(), "attempt 2, one window later")
    }

    @Test
    fun holdsIsTrueOnlyWhileAValueIsWaiting() {
        assertFalse(buffer.holds("a"), "nothing pending")
        buffer.put("a", "v1")
        assertTrue(buffer.holds("a"), "waiting for its deadline")
        clock.advance(400)
        buffer.take()
        assertFalse(buffer.holds("a"), "taken — in flight, and the buffer no longer speaks for it")
        buffer.put("a", "v2")
        buffer.drop("a")
        assertFalse(buffer.holds("a"), "dropped")
    }

    @Test
    fun flushNowMakesEverythingDueAtOnce() {
        buffer.put("a", "v1")
        buffer.put("b", "v2")
        buffer.flushNow()
        assertEquals(0L, buffer.dueIn(), "a flush cannot wait for the clock")
        assertEquals(listOf("a" to "v1", "b" to "v2"), buffer.take(), "both, with the clock frozen")
    }

    @Test
    fun dropRemovesAPendingValue() {
        buffer.put("a", "v1")
        buffer.put("b", "v2")
        buffer.drop("a")
        assertEquals(1, buffer.size, "one left")
        clock.advance(400)
        assertEquals(listOf("b" to "v2"), buffer.take(), "the dropped value is not written")
    }

    /** A re-put keeps the position it was first put at, so a batch reads the same way twice. */
    @Test
    fun severalKeysAreWrittenInTheOrderTheyWereFirstPut() {
        buffer.put("a", "v1")
        buffer.put("b", "v2")
        buffer.put("c", "v3")
        clock.advance(200)
        buffer.put("a", "v1b")
        clock.advance(400)
        assertEquals(listOf("a" to "v1b", "b" to "v2", "c" to "v3"), buffer.take(), "insertion order, latest values")
    }

    @Test
    fun aFailedWriteIsReArmedOneWindowLaterAndGivenUpOnAfterThreeAttempts() {
        buffer.put("a", "v1")
        clock.advance(400)
        assertEquals(listOf("a" to "v1"), buffer.take(), "attempt 1")

        assertTrue(buffer.failed("a", "v1"), "the first failure is re-armed")
        assertEquals(400L, buffer.dueIn(), "one window of backoff")
        assertEquals(emptyList(), buffer.take(), "the retry waits out the backoff")
        clock.advance(400)
        assertEquals(listOf("a" to "v1"), buffer.take(), "attempt 2")

        assertTrue(buffer.failed("a", "v1"), "the second failure is re-armed")
        clock.advance(400)
        assertEquals(listOf("a" to "v1"), buffer.take(), "attempt 3")

        assertFalse(buffer.failed("a", "v1"), "three attempts is the limit")
        assertNull(buffer.dueIn(), "the value is gone — the caller logs it")
        assertEquals(0, buffer.size, "nothing left to write")
    }

    /** Latest wins over a retry: re-arming the old value would revert what the player just did. */
    @Test
    fun aNewerValueSupersedesAStaleRetry() {
        buffer.put("a", "v1")
        clock.advance(400)
        assertEquals(listOf("a" to "v1"), buffer.take(), "v1 is in flight")

        buffer.put("a", "v2")
        assertFalse(buffer.failed("a", "v1"), "the failure of v1 is not re-armed over v2")
        clock.advance(400)
        assertEquals(listOf("a" to "v2"), buffer.take(), "v2 is what gets written")
    }

    /** A retry counts against the same key only; a fresh put starts the count over. */
    @Test
    fun aFreshPutResetsTheAttemptCount() {
        buffer.put("a", "v1")
        clock.advance(400)
        buffer.take()
        buffer.failed("a", "v1")
        clock.advance(400)
        buffer.take()
        assertTrue(buffer.failed("a", "v1"), "two attempts made")

        buffer.put("a", "v2")
        clock.advance(400)
        buffer.take()
        assertTrue(buffer.failed("a", "v2"), "v2 gets its own three attempts, not v1's leftovers")
    }

    @Test
    fun dueInIsTheEarliestDeadlineAcrossKeys() {
        buffer.put("a", "v1")
        clock.advance(300)
        buffer.put("b", "v2")
        assertEquals(100L, buffer.dueIn(), "a's deadline is the near one")
        clock.advance(100)
        assertEquals(listOf("a" to "v1"), buffer.take(), "only a is due")
        assertEquals(300L, buffer.dueIn(), "b's deadline is what is left")
    }
}
