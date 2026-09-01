package dev.tyler.grimoire.data

import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.RulesException

/**
 * Test doubles for the character store's seams, the pattern `compendium/Fakes.kt` established: Room cannot
 * run on the JVM (no Robolectric on the allow-list), so [CharacterDao] is a Kotlin interface and this is a
 * reading of its six queries in Kotlin — the `ORDER BY updatedAt DESC LIMIT`, the json-only projection, the
 * `EXISTS`, the upsert. The device checks cover the real SQL.
 */

/**
 * The `characters` table as a map. [calls] records every query by name, in order, so a test can prove one
 * was *not* made — which is how the S0 list is held to never reading a character document.
 *
 * One deliberate difference from SQLite: ties on `updatedAt` come back in insertion order here, where SQL
 * leaves them unspecified. Nothing may depend on that ordering, which is what the stable sort makes safe to
 * assert.
 */
class FakeCharacterDao(seed: List<CharacterRow> = emptyList()) : CharacterDao {
    val rows: LinkedHashMap<String, CharacterRow> =
        LinkedHashMap<String, CharacterRow>().apply { for (row in seed) put(row.id, row) }

    val calls: MutableList<String> = ArrayList()

    override suspend fun summaries(limit: Int): List<CharacterSummaryRow> {
        calls += "summaries"
        return rows.values.sortedByDescending { it.updatedAt }
            .take(limit)
            .map { CharacterSummaryRow(it.id, it.name, it.summary, it.updatedAt) }
    }

    override suspend fun json(id: String): String? {
        calls += "json"
        return rows[id]?.json
    }

    override suspend fun count(): Int {
        calls += "count"
        return rows.size
    }

    override suspend fun exists(id: String): Boolean {
        calls += "exists"
        return rows.containsKey(id)
    }

    override suspend fun upsert(row: CharacterRow) {
        calls += "upsert"
        rows[row.id] = row
    }

    override suspend fun delete(id: String) {
        calls += "delete"
        rows.remove(id)
    }
}

/** A clock a test moves by hand, so every deadline in [SaveBuffer] is pinned at zero real delay. */
class ManualClock(private var millis: Long = 1_756_000_000_000L) {
    fun now(): Long = millis

    fun advance(by: Long) {
        millis += by
    }
}

/**
 * [CharacterSaver] without the coroutine: an enqueue is held and visible to reads, exactly as the real one
 * holds it, and [land] is the test's stand-in for the save loop draining. Splitting the two is what makes
 * the resurrection test possible — a save can sit enqueued across a `delete` and then be asked to land.
 */
class FakeSaver : CharacterSaver {
    /** `enqueue:<id>`, `drop:<id>`, `flush` — in order. */
    val calls: MutableList<String> = ArrayList()

    private val queue = LinkedHashMap<String, PendingSave>()

    override fun enqueue(save: PendingSave) {
        calls += "enqueue:${save.character.id}"
        queue[save.character.id] = save
    }

    override fun drop(id: String) {
        calls += "drop:$id"
        queue.remove(id)
    }

    /** Records the call and nothing more: on device a flush returns before the write, too. */
    override fun flush() {
        calls += "flush"
    }

    override fun pending(): Map<String, PendingSave> = LinkedHashMap(queue)

    /** What the save loop does: write everything held, through the same writer the device uses. */
    suspend fun land(dao: CharacterDao) {
        for (save in queue.values.toList()) {
            DbCharacterRepository.write(dao, save)
            queue.remove(save.character.id)
        }
    }
}

/**
 * The repository as a view model will fake it from M3 task 2 on: characters in a map, every call recorded,
 * and the same debounce shape — [save] is held until [flush] or [land], so a test can put a view model
 * through the exit paths and see what would have been written.
 *
 * **Every refusal and every stamp [DbCharacterRepository] makes is made here too**, and that is not
 * politeness. A fake that is more permissive than the real thing does not fail a view-model test, it passes
 * one: a screen that never checks `MAX_CHARACTERS`, or a Home list that does not re-sort around a save that
 * has not landed, would be green here and wrong on the phone. `FakeCharacterRepositoryTest` holds the two
 * side by side on exactly the behaviours a screen can get wrong.
 *
 * The one thing a test cannot learn from [load] is whether the view model flushed: reads are served from the
 * pending value in both the fake and the real repository, by design, so a reload looks identical either way.
 * Assert on [calls] for that — `flush` on every exit path is the milestone's kill-and-relaunch requirement,
 * and [calls] is the only place it shows.
 */
class FakeCharacterRepository(
    private val now: () -> Long = { 1_756_000_000_000L },
    private val newId: () -> String = { "minted-character" },
) : CharacterRepository {
    val stored: LinkedHashMap<String, Character> = LinkedHashMap()
    val updatedAt: LinkedHashMap<String, Long> = LinkedHashMap()
    val calls: MutableList<String> = ArrayList()

    /** Every value handed to [save], in order and uncoalesced — what the screen asked to store. */
    val saves: MutableList<Character> = ArrayList()

    private val queue = LinkedHashMap<String, PendingSave>()

    /** The real order of operations: the stored page first, then the pending values over it, then re-sorted. */
    override suspend fun list(limit: Int): List<CharacterSummaryRow> {
        calls += "list"
        val rows = stored.values
            .map { CharacterSummaryRow(it.id, it.name, Summaries.summaryOf(it), updatedAt[it.id] ?: 0L) }
            .sortedByDescending { it.updatedAt }
            .take(limit)
        if (queue.isEmpty()) return rows
        return rows
            .map { row -> queue[row.id]?.let(::summaryOf) ?: row }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun load(id: String): Character? {
        calls += "load:$id"
        return queue[id]?.character ?: stored[id]
    }

    override suspend fun count(): Int {
        calls += "count"
        return stored.size
    }

    override suspend fun create(character: Character): Character {
        calls += "create:${character.id}"
        CharacterLimits.check(character)
        val created = if (character.id.isBlank()) character.copy(id = newId()) else character
        if (stored.containsKey(created.id)) throw RulesException("that character is already stored")
        if (stored.size >= CharacterLimits.MAX_CHARACTERS) {
            throw RulesException(
                "${stored.size} characters already (at most ${CharacterLimits.MAX_CHARACTERS}) — delete one first",
            )
        }
        queue.remove(created.id)
        stored[created.id] = created
        updatedAt[created.id] = now()
        return created
    }

    override fun save(character: Character) {
        calls += "save:${character.id}"
        CharacterLimits.check(character)
        saves += character
        queue[character.id] = PendingSave(character, now())
    }

    override fun flush() {
        calls += "flush"
        land()
    }

    override suspend fun delete(id: String) {
        calls += "delete:$id"
        queue.remove(id)
        stored.remove(id)
        updatedAt.remove(id)
    }

    /**
     * Land every pending save without going through [flush], for a test that wants the two apart. The stamp
     * is the one taken when the save was *made*, as it is on device — never the moment it landed.
     */
    fun land() {
        for ((id, save) in queue) {
            if (stored.containsKey(id)) {
                stored[id] = save.character
                updatedAt[id] = save.updatedAt
            }
        }
        queue.clear()
    }

    private fun summaryOf(save: PendingSave): CharacterSummaryRow = CharacterSummaryRow(
        id = save.character.id,
        name = save.character.name,
        summary = Summaries.summaryOf(save.character),
        updatedAt = save.updatedAt,
    )
}
