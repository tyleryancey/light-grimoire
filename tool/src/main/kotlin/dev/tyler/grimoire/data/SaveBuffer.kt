package dev.tyler.grimoire.data

/**
 * The debounce, with every deadline in one pure object — no coroutines, no Room, nothing from Android, and
 * the clock injected. All of the tool's save *timing* lives here, so the JVM gate can pin it exactly and at
 * zero real delay; [SaveLoop] is the coroutine that reads these deadlines and nothing else.
 *
 * The contract is **trailing edge, latest wins, with a ceiling**. A burst of taps on the HP screen is one
 * write: [put] replaces the pending value *and* re-arms the deadline, so nothing is written until the player
 * has stopped for [delayMs], and what is written is the last thing they did. Intermediate values are never
 * written and never observable — which is exactly right for a character sheet, where only the current state
 * is the record and the steps that reached it are the journal's business.
 *
 * The ceiling is what makes "at most one debounce window is at risk" a true sentence. A sliding window with
 * no maximum wait defers *every* write for as long as the player keeps editing: a wheel held through a
 * dozen HP steps re-arms the deadline at every detent, and a process killed at the end of it loses the whole
 * burst rather than the last 400 ms of it. So a value also carries the moment it *first* became pending, and
 * its deadline is never later than [maxWaitMs] after that. Coalescing survives — a burst is still one write
 * per ceiling, not one write per tap — and the loss is bounded by the ceiling instead of by the player.
 *
 * Keyed, because the store holds up to `CharacterLimits.MAX_CHARACTERS` characters and two of them can have
 * pending writes at once (edit one, back out, edit another inside 400 ms). A `LinkedHashMap` keeps that
 * batch deterministic: [take] hands the due entries back in the order they were first put, so a test — and a
 * logcat line — reads the same way twice.
 *
 * Retries are here rather than in the loop for the same reason the deadlines are: a retry is a deadline. A
 * failed write is re-armed [maxAttempts] times, one [delayMs] apart, and **a newer value supersedes a stale
 * retry** — re-writing what the player did two seconds ago on top of what they just did would be a visible
 * revert, which is the one failure this whole file exists to prevent.
 *
 * Every method is `@Synchronized`: [put] is called from the main thread (a view model's event handler) and
 * [take] from the save loop's IO thread, on every save the tool makes.
 */
class SaveBuffer<K : Any, V : Any>(
    private val delayMs: Long = DEBOUNCE_MS,
    private val maxAttempts: Int = 3,
    private val maxWaitMs: Long = MAX_WAIT_MS,
    private val now: () -> Long,
) {
    companion object {
        /**
         * The debounce window (docs/DATA-MODEL.md §Room mapping, docs/ARCHITECTURE.md §3). Long enough that
         * holding the wheel through a dozen HP steps is one row written, short enough that it is over before
         * a player's thumb reaches the back key.
         */
        const val DEBOUNCE_MS = 400L

        /**
         * The longest a value may stay unwritten while the player keeps editing it. Five windows, and picked
         * off the wheel rather than off a round number: a held wheel produces a detent every ~150 ms, so a
         * ceiling of 2 s writes roughly every thirteen detents — about one point of HP per detent, so an
         * interrupted correction of a big hit loses at most the tail of it. Below ~1 s the ceiling would
         * start firing inside ordinary bursts and cost the coalescing its point.
         */
        const val MAX_WAIT_MS = 2_000L
    }

    /**
     * [attempts] counts writes already made of *this* value; a fresh [put] resets it to zero. [firstAt] is
     * when the key *first* became pending, which survives coalescing — it is what the [maxWaitMs] ceiling is
     * measured from, and the reason a burst cannot defer a write for ever.
     */
    private data class Pending<V : Any>(val value: V, val dueAt: Long, val attempts: Int, val firstAt: Long)

    private val pending = LinkedHashMap<K, Pending<V>>()

    /**
     * Attempts made of the value [take] most recently handed out for a key, which the entry itself can no
     * longer carry because [take] emptied it. Read and cleared by [failed]; cleared by [put] and [drop].
     * Bounded by the number of characters, and a successful write leaves at most one stale count per key —
     * harmless, because the next [put] of that key clears it before anything can consult it.
     */
    private val attempted = LinkedHashMap<K, Int>()

    /** How many values are waiting. In-flight values — taken but not yet written — are not counted. */
    val size: Int
        @Synchronized get() = pending.size

    /**
     * Hold [value] for [key], to be written [delayMs] from now — or at the [maxWaitMs] ceiling measured from
     * when this key first became pending, whichever comes first. Coalescing: an existing pending value for
     * the same key is replaced and its deadline re-armed, and the entry keeps both the position and the
     * first-pending moment it was put at, so a burst of edits to one character is one write at the end of the
     * burst and, if the burst outlasts the ceiling, one write per ceiling until it stops.
     */
    @Synchronized
    fun put(key: K, value: V) {
        val at = now()
        val firstAt = pending[key]?.firstAt ?: at
        pending[key] = Pending(value, minOf(at + delayMs, firstAt + maxWaitMs), attempts = 0, firstAt = firstAt)
        attempted.remove(key)
    }

    /** Forget the pending value for [key] — what a delete does, so a debounced save cannot resurrect a row. */
    @Synchronized
    fun drop(key: K) {
        pending.remove(key)
        attempted.remove(key)
    }

    /**
     * Whether a value is waiting for [key] right now. What a caller holding its own copy of the latest value
     * asks before re-arming it: a value already pending must not be disturbed, because the pending one is by
     * definition at least as new — see `DebouncedSaver.flush`.
     */
    @Synchronized
    fun holds(key: K): Boolean = pending.containsKey(key)

    /**
     * Bring every deadline forward to now, so the next [take] returns everything. What `flush()` is: a
     * screen is going away, or the whole tool is, and the debounce has stopped being a kindness.
     */
    @Synchronized
    fun flushNow() {
        if (pending.isEmpty()) return
        val at = now()
        for (key in pending.keys.toList()) {
            val entry = pending.getValue(key)
            pending[key] = entry.copy(dueAt = at)
        }
    }

    /**
     * Milliseconds until the earliest deadline — `0` when something is due right now, `null` when nothing is
     * pending at all. The loop's whole state: park on `null`, wait on a positive number, drain on zero.
     */
    @Synchronized
    fun dueIn(): Long? {
        val earliest = pending.values.minOfOrNull { it.dueAt } ?: return null
        return maxOf(0L, earliest - now())
    }

    /**
     * Every value whose deadline has passed, in the order its key was first put, emptied out of the buffer.
     * The caller now owns them: each one must reach [failed] if its write throws, or it is simply gone.
     */
    @Synchronized
    fun take(): List<Pair<K, V>> {
        val at = now()
        val ripe = pending.entries.filter { it.value.dueAt <= at }.map { it.key to it.value }
        for ((key, entry) in ripe) {
            pending.remove(key)
            attempted[key] = entry.attempts + 1
        }
        return ripe.map { (key, entry) -> key to entry.value }
    }

    /**
     * Report that the write of [value] for [key] threw.
     *
     * @return true when the value was re-armed for another attempt, one [delayMs] from now — the caller's
     * loop will hand it back on a later [take]. False when it is gone: either a newer value arrived while
     * the write was in flight (latest wins, and re-arming would revert the player's newest edit), or the
     * value has now been attempted [maxAttempts] times and the database is not going to take it.
     *
     * A retry starts the [maxWaitMs] ceiling over, which is the whole reason [Pending.firstAt] is set here
     * rather than carried across: the ceiling exists to cut a *burst of edits* short, and a value whose
     * ceiling had already passed would come back due immediately, turning one window of backoff into a spin
     * against a database that just refused it.
     */
    @Synchronized
    fun failed(key: K, value: V): Boolean {
        val attempts = attempted.remove(key) ?: 1
        if (pending.containsKey(key)) return false
        if (attempts >= maxAttempts) return false
        val at = now()
        pending[key] = Pending(value, at + delayMs, attempts, firstAt = at)
        return true
    }
}
