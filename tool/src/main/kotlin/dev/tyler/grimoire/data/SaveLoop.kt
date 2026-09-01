package dev.tyler.grimoire.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * The one coroutine that writes, for the life of the process: it reads deadlines out of a [SaveBuffer] and
 * hands due values to [write]. Everything about *when* is the buffer's; everything about *how* is a
 * coroutine, and the two are separate files so the timing can be pinned on the JVM with no real delay.
 *
 * One coroutine and not one per save, because a save is initiated by a view model that is usually about to
 * stop existing. `LightActivity.goBack()` runs `notifyWillHide()` → `destroy()` → `viewModelStore.clear()`
 * synchronously in a single call, so a coroutine launched on `viewModelScope` from `onScreenHide` is
 * cancelled microseconds later — and one that had not started yet simply never runs, which is why
 * `withContext(NonCancellable)` inside a `viewModelScope` coroutine is necessary but not sufficient. This
 * loop lives on the store's own scope instead: it was already running long before the screen appeared, and
 * it is still running after the screen is gone.
 *
 * `NonCancellable` around the drain is then belt and braces — the store's scope is never cancelled — but it
 * is the rule (CLAUDE.md) and it costs nothing to keep the guarantee local to the code that needs it.
 *
 * The loop has exactly three states, and [SaveBuffer.dueIn] names which: `null` → park on [signal];
 * positive → wait that long, unless a [signal] shortens it; zero → drain. [sleep] is injected only so the
 * JVM gate can drive the composed loop deterministically; on device it is `delay`.
 */
class SaveLoop<K : Any, V : Any>(
    private val scope: CoroutineScope,
    private val buffer: SaveBuffer<K, V>,
    private val write: suspend (V) -> Unit,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val log: (String) -> Unit,
) {
    /**
     * Conflated: a signal means "look at the buffer sooner", never "here is a value", so a second signal
     * before the loop wakes is the same event and collapsing them is free. Nothing durable rides on it —
     * the buffer is the only state — so a signal that races a wake-up costs one extra pass around the loop
     * at worst, and a signal lost to a cancelled wait costs a wait that was already expiring.
     */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var job: Job? = null

    /** Idempotent: the loop starts on the first save of the process and runs until the process ends. */
    @Synchronized
    fun start() {
        if (job != null) return
        job = scope.launch { loop() }
    }

    /** Shorten a running wait — what a `put` that moved a deadline earlier, or a `flush`, has to say. */
    fun signal() {
        wake.trySend(Unit)
    }

    private suspend fun loop() {
        while (true) {
            val due = buffer.dueIn()
            when {
                due == null -> wake.receive()
                due > 0L -> waitUpTo(due)
                else -> drain()
            }
        }
    }

    /**
     * [sleep] for [ms], cut short by a [signal]. Two children racing rather than a timeout, because the
     * timeout would have to be a real `delay` and then the JVM gate could not drive the loop. Whichever
     * finishes first ends the other; the buffer is re-read on the next pass either way, so it does not
     * matter which won.
     */
    private suspend fun waitUpTo(ms: Long): Unit = coroutineScope {
        val sleeper = launch { sleep(ms) }
        val waker = launch {
            wake.receive()
            sleeper.cancel()
        }
        sleeper.join()
        waker.cancel()
    }

    /**
     * Write everything due, in the order the buffer holds it. A write that throws is reported back to the
     * buffer, which decides between a retry and giving up — and never kills the loop, because the loop is
     * the only writer the process has and a dead one loses every later save silently.
     *
     * [Throwable] and not `Exception`, so the sentence above is true as written. An `Error` escaping a
     * `scope.launch` under a `SupervisorJob` with no handler reaches the thread's uncaught handler and takes
     * the process down with it — loud rather than silent, but the saves after it are lost either way, and a
     * logged retry is strictly better than that. The `CancellationException` rethrow above keeps the one
     * throwable that must not be caught moving.
     */
    private suspend fun drain() {
        withContext(NonCancellable) {
            for ((key, value) in buffer.take()) {
                try {
                    write(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    val reason = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
                    val retrying = buffer.failed(key, value)
                    log(if (retrying) "save failed, retrying: $reason" else "save failed, giving up: $reason")
                }
            }
        }
    }
}

/**
 * The production [CharacterSaver]: a [SaveBuffer] for the deadlines, a [SaveLoop] to drain it, and the map
 * of values that are pending or in flight, which is what makes a debounced write invisible to a reader
 * inside the process (docs/ARCHITECTURE.md §3).
 *
 * That map is the half of this class that is easy to miss. `onScreenHide` → `flush()` and the reload the
 * next `onScreenShow` does are *not* ordered: `onPause` → `notifyAppPause` and `onResume` →
 * `notifyWillShow` run back to back, and the flushed write is still in flight when the reload's query runs.
 * Without [pending] the reload would read the row from before the edit and the player would watch their
 * last HP change undo itself on returning from a volume modal. So a value stays visible here from [enqueue]
 * until the write of *that same value* has returned — cleared after the write and never before, so reads
 * never go backwards, and only for the value written, so a newer enqueue during the write survives.
 *
 * A value whose write ran out of attempts stays visible too. That is deliberate: the database is refusing
 * writes, nothing better is going to happen, and showing the player what they did beats showing them a
 * silent revert. The next successful save of that character clears it — and so does the next [flush], which
 * is the difference between "the retry budget was spent" and "the edit is lost". Three attempts one window
 * apart is 1.2 s of budget, and a phone that took 11.5 s to open a database while dozing (CLAUDE.md) can
 * spend all of it on transient failures; without the re-arm below, a player who then finished their rest,
 * made no further edit and pressed back would have flushed nothing, because [flush] can only see the buffer
 * and the buffer had let the value go.
 */
class DebouncedSaver(
    scope: CoroutineScope,
    now: () -> Long,
    log: (String) -> Unit,
    delayMs: Long = SaveBuffer.DEBOUNCE_MS,
    sleep: suspend (Long) -> Unit = { delay(it) },
    private val write: suspend (PendingSave) -> Unit,
) : CharacterSaver {
    private val buffer = SaveBuffer<String, PendingSave>(delayMs = delayMs, now = now)

    private val latest = ConcurrentHashMap<String, PendingSave>()

    private val loop = SaveLoop(
        scope = scope,
        buffer = buffer,
        write = { save ->
            write(save)
            latest.remove(save.character.id, save)
        },
        sleep = sleep,
        log = log,
    )

    override fun enqueue(save: PendingSave) {
        latest[save.character.id] = save
        buffer.put(save.character.id, save)
        loop.start()
        loop.signal()
    }

    override fun drop(id: String) {
        latest.remove(id)
        buffer.drop(id)
    }

    /**
     * Everything held becomes due at once — and anything this class still believes in that the buffer has
     * let go of is put back first, so a flush is the last chance it claims to be.
     *
     * A value reaches [latest] without being in the buffer two ways: it is in flight (taken, write not
     * returned), or its three attempts were spent and the buffer gave up on it. Re-arming the first costs
     * one redundant upsert of a value already being written, which the row does not notice; re-arming the
     * second is the point. **Once per flush, and never unconditionally** — a character that cannot be
     * encoded fails deterministically, so an automatic re-arm would retry it for ever. A flush is a screen
     * going away or the tool being paused: bounded by the player, and each one buys the value another three
     * attempts. The re-arm has to come before [SaveBuffer.flushNow] or these values would be put with a
     * fresh deadline and the flush would leave them behind exactly as it does now.
     */
    override fun flush() {
        for ((id, save) in latest) {
            if (!buffer.holds(id)) buffer.put(id, save)
        }
        buffer.flushNow()
        loop.start()
        loop.signal()
    }

    override fun pending(): Map<String, PendingSave> = latest.toMap()
}
