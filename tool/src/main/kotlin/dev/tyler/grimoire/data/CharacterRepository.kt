package dev.tyler.grimoire.data

import dev.tyler.grimoire.rules.Character

/**
 * One character on its way to the database: what the player has, and the moment they had it.
 *
 * [updatedAt] is stamped when the save is *made*, not when it lands, so the Home list's order is the order
 * the player touched their characters in and not the order a debounce happened to fire in. The row's other
 * denormalised columns are derived at write time from [character] — see `DbCharacterRepository.rowOf`.
 */
data class PendingSave(val character: Character, val updatedAt: Long)

/**
 * The pending-write side of the repository, as a seam: [DebouncedSaver] is the real one (a [SaveBuffer] and
 * a [SaveLoop] on the store's process scope) and the tests substitute one that applies writes on demand, so
 * every repository test runs on the JVM with no clock and no coroutine racing it.
 *
 * [enqueue], [drop] and [flush] are all non-`suspend` on purpose — see [CharacterRepository.save].
 */
interface CharacterSaver {
    /** Hold [save] and write it when the debounce expires, replacing any pending save of the same character. */
    fun enqueue(save: PendingSave)

    /** Forget the pending save for [id], if any. A deleted character must not be written back. */
    fun drop(id: String)

    /** Bring every pending deadline forward to now. Returns at once; the write itself is still asynchronous. */
    fun flush()

    /**
     * Saves that are enqueued or in flight, by character id — what a read inside this process must see
     * instead of the stored row (docs/ARCHITECTURE.md §3).
     */
    fun pending(): Map<String, PendingSave>
}

/**
 * The character store as the screens see it (docs/UI-SPEC.md S0–S13): six characters at most, one of them
 * open at a time, every mutation a whole [Character] written back.
 *
 * **[save] and [flush] are deliberately not `suspend`.** This is the signature the milestone's definition
 * of done turns on — "kill the process mid-rest and relaunch: nothing lost" — and a suspending flush is
 * precisely the bug it tests for. The caller is a view model that is usually about to be destroyed:
 * `LightActivity.goBack()` runs `notifyWillHide()` → `destroy()` → `viewModelStore.clear()` synchronously
 * in one call, so a coroutine launched from `onScreenHide` on `viewModelScope` is cancelled microseconds
 * later, and one that has not started yet never runs at all — `withContext(NonCancellable)` inside it
 * cannot save what was never scheduled. Non-`suspend` means the caller needs no scope of its own: it hands
 * the value to a buffer owned by the process and returns, and the write happens on a coroutine that was
 * running before the screen existed. A view model calls [flush] from **both** `onScreenHide` and
 * `onAppPause`, because neither is a superset of the other — hide does not fire when LightOS takes the
 * screen, pause does not fire on a back press.
 *
 * The cost of that signature is that [save] cannot report a database failure to its caller. It reports the
 * failure it can: [CharacterLimits.check] runs synchronously, on the caller's thread, so an illegal
 * character throws where a view model can render the sentence — and the buffer only ever holds characters
 * that are legal to store.
 *
 * Reads are served from the pending value while a write is in flight, so no reload inside this process can
 * observe a character older than the last [save]. Across process death there is no pending value and the
 * row is whatever landed, which is why [flush] is called on every exit path and not just one.
 */
interface CharacterRepository {
    /** The S0 list, most recently touched first. */
    suspend fun list(limit: Int = CharacterLimits.MAX_CHARACTERS): List<CharacterSummaryRow>

    /**
     * The whole character, or null when there is no such id.
     *
     * A stored document that will not decode raises — `RulesException` for a version this build cannot
     * read, a serialization error for a malformed one — and the failure is not swallowed. A character that
     * will not decode is a bug or a corrupted row, and an error line the player can read out is worth more
     * than a blank sheet that silently loses everything they wrote on it.
     */
    suspend fun load(id: String): Character?

    /** How many characters are stored — what S0 checks before offering to add another. */
    suspend fun count(): Int

    /**
     * Store a new character and return it as stored (its id minted here when the caller left it blank).
     * Written through immediately rather than debounced: creation is a commitment the player made on a
     * confirm screen, with no burst of keystrokes behind it to coalesce.
     *
     * `suspend`, unlike [save], because the caller needs the stored character back and needs to be told
     * when it is refused. The implementation runs the write on the store's own scope so a cancelled caller
     * cannot leave it half-done — but a caller that never starts is beyond any signature's reach, so
     * **await this before navigating**. `viewModelScope.launch { create(c) }` followed by a synchronous
     * `goBack()` is how an entire transcribed character gets lost.
     *
     * @throws dev.tyler.grimoire.rules.RulesException when the character is not legal to store, when the id
     * is already taken, or when the store already holds [CharacterLimits.MAX_CHARACTERS] characters.
     */
    suspend fun create(character: Character): Character

    /**
     * Save [character], debounced. Returns as soon as the value is held.
     *
     * @throws dev.tyler.grimoire.rules.RulesException when the character is not legal to store — checked on
     * the calling thread, before anything is enqueued.
     */
    fun save(character: Character)

    /** Write every pending save now. Called from `onScreenHide` and `onAppPause`; see the class KDoc. */
    fun flush()

    /** Remove a character, and any save of it that had not landed yet. Await it before navigating — see [create]. */
    suspend fun delete(id: String)
}
