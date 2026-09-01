package dev.tyler.grimoire.data

import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Model
import dev.tyler.grimoire.rules.RulesException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * The [CharacterRepository] over the `characters` table.
 *
 * **It imports no Room runtime.** [CharacterDao] is a plain Kotlin interface and [CharacterRow] a plain data
 * class — the annotations on them are Room's business at KSP time, not this file's — so every line below
 * runs in a JVM unit test over a fake DAO, which is the whole reason step 2 shaped the DAO as an interface.
 * Room's own behaviour (the SQL, the upsert, WAL) is checked on the device.
 *
 * Two things are stamped at write time and nowhere else, both through [rowOf]: [PendingSave.updatedAt] and
 * `Summaries.summaryOf`. A summary column that disagreed with the document would be a lie the Home screen
 * tells about a character the player already fixed, and there is no second place that writes a row for it to
 * drift from. `Character.meta` is never touched here: the stored document is what `ModelRoundTripTest` pins
 * against the fixtures, and `rules/` has no clock — a repository that quietly rewrote a field of the model
 * would make the round trip a fiction.
 *
 * [scope] is the store's own — the same one the save loop runs on, a `SupervisorJob` that was started before
 * any screen existed and is never cancelled. [create] and [delete] are the two writes that do not go through
 * the buffer, so this is where they get the survival the buffer gives everything else: the work is a child
 * of [scope], not of the caller, and a caller that is cancelled halfway through cannot tear it in half.
 */
class DbCharacterRepository(
    private val dao: CharacterDao,
    private val saver: CharacterSaver,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val newId: () -> String = Ids::new,
) : CharacterRepository {
    companion object {
        /**
         * The one derivation of a stored row from a character. Every column but [CharacterRow.json] is a
         * copy of something inside it, so they are all written together or the copy is stale.
         */
        fun rowOf(save: PendingSave): CharacterRow = CharacterRow(
            id = save.character.id,
            name = save.character.name,
            summary = Summaries.summaryOf(save.character),
            updatedAt = save.updatedAt,
            json = Model.encode(save.character),
        )

        /**
         * What the save loop does with one pending save, and what [DbCharacterRepository.create] does
         * directly. A free function rather than a method so the loop's writer and the repository can share
         * it without either one having to be built first.
         *
         * The [CharacterDao.exists] check is the second half of the anti-resurrection rule. [delete] drops
         * the pending save before it removes the row, which settles the ordinary case; this one narrows what
         * is left — a delete racing a write already taken out of the buffer. It narrows rather than closes:
         * `exists` and the upsert are two statements, so a delete landing between them still resurrects the
         * row. Making that impossible needs a transaction around both, and it is not worth one — a delete is
         * reachable only from a confirm screen, and the screen that could have had a pending save flushed on
         * its way out.
         *
         * Its cost is that a save for a character that was never created is dropped rather than inserted —
         * deliberate, since `create` is the only door into the table and the only place `MAX_CHARACTERS` is
         * enforced. The caller's log line is the only trace of that, which is why it takes one.
         *
         * @return false when there is no such row and the save was dropped; the caller logs it.
         */
        suspend fun write(dao: CharacterDao, save: PendingSave): Boolean {
            if (!dao.exists(save.character.id)) return false
            dao.upsert(rowOf(save))
            return true
        }
    }

    /**
     * The stored list with pending saves laid over it. The overlay costs one map lookup per row and buys
     * the rule that no read inside this process is older than the last [save]: without it a rename would
     * still show the old name on S0 until something else made the list re-query.
     */
    override suspend fun list(limit: Int): List<CharacterSummaryRow> {
        val rows = dao.summaries(limit)
        val pending = saver.pending()
        if (pending.isEmpty()) return rows
        return rows
            .map { row -> pending[row.id]?.let(::summaryOf) ?: row }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun load(id: String): Character? {
        saver.pending()[id]?.let { return it.character }
        val json = dao.json(id) ?: return null
        return withContext(Dispatchers.Default) { Model.decode(json) }
    }

    override suspend fun count(): Int = dao.count()

    /**
     * On [scope], and awaited: a create is four statements (`exists`, `count`, `drop`, `upsert`) with three
     * suspension points between them, and a caller cancelled at any of them would otherwise leave the store
     * half-written — the pending map reconciled but no row, or a check passed and nothing done with it. As a
     * child of [scope] the whole sequence finishes whatever the caller does; only the `await` is cancellable,
     * and a `RulesException` still reaches the caller through it.
     *
     * What that does **not** buy is the other half of the same hazard, and it cannot: a coroutine that never
     * started never calls this at all. So a screen must *await* a create before it navigates —
     * `viewModelScope.launch { repo.create(c) }` followed by a synchronous `goBack()` can lose an entire
     * transcribed character, and no signature here can stop it. Only [save] and [flush], which are not
     * `suspend` and need no scope of the caller's, are safe on an exit path.
     */
    override suspend fun create(character: Character): Character = scope.async {
        CharacterLimits.check(character)
        val stored = if (character.id.isBlank()) character.copy(id = newId()) else character
        if (dao.exists(stored.id)) throw RulesException("that character is already stored")
        val count = dao.count()
        if (count >= CharacterLimits.MAX_CHARACTERS) throw RulesException(CharacterLimits.tooMany(count))
        // Every other write reconciles the pending map — save fills it, delete drops it, the loop clears it —
        // and this is the one path that would not. There was no row a moment ago, so anything held for this id
        // is a leftover no write will ever land (a save that preceded its create, or one that ran out of
        // attempts), and leaving it would make the very next load answer with it instead of what was created.
        saver.drop(stored.id)
        dao.upsert(rowOf(PendingSave(stored, now())))
        stored
    }.await()

    override fun save(character: Character) {
        CharacterLimits.check(character)
        saver.enqueue(PendingSave(character, now()))
    }

    override fun flush() = saver.flush()

    /**
     * The pending save goes first, and the order is the point: a debounced write that landed after the
     * delete would put the character back, and the player would find something they deleted still on S0.
     *
     * On [scope] for the same reason [create] is, and the two statements are why: a caller cancelled between
     * them discards the pending save and leaves the row, so the character survives a delete the screen has
     * already navigated away from. The caller must await this before it navigates — see [create].
     */
    override suspend fun delete(id: String) {
        scope.async {
            saver.drop(id)
            dao.delete(id)
        }.await()
    }

    private fun summaryOf(save: PendingSave): CharacterSummaryRow = CharacterSummaryRow(
        id = save.character.id,
        name = save.character.name,
        summary = Summaries.summaryOf(save.character),
        updatedAt = save.updatedAt,
    )
}
