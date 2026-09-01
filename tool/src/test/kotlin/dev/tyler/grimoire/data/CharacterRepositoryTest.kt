package dev.tyler.grimoire.data

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.Model
import dev.tyler.grimoire.rules.RulesException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The repository over a fake DAO — every line of `DbCharacterRepository` on the JVM, which is what shaping
 * `CharacterDao` as a Kotlin interface bought (Room itself cannot run here).
 *
 * Three of these cases are the ones the milestone turns on. **The pending save is visible to a read** before
 * it lands: `onAppPause` flushes and `onResume` reloads back to back, and a repository that answered from
 * the row would hand the screen back the character from before the player's last edit and undo it in front
 * of them. **A delete drops the pending save first**, or a debounced write lands afterwards and the deleted
 * character reappears on S0. And **the summary and `updatedAt` columns are written with the document**, so
 * the Home list can never describe a character that no longer exists.
 */
class CharacterRepositoryTest {
    private val clock = ManualClock()
    private val dao = FakeCharacterDao()
    private val saver = FakeSaver()
    private var minted = 0

    /**
     * Stands in for `GrimoireStore.scope` — the process scope `create` and `delete` run their writes on so a
     * cancelled caller cannot tear them in half. `Unconfined` because the fake DAO never really suspends: the
     * whole write runs on the calling thread before `await` returns, so every case below stays as
     * deterministic as it was when the two were plain suspend functions.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val repo = DbCharacterRepository(dao, saver, scope, clock::now) { "minted-${++minted}" }

    private fun character(ref: String) = Model.decode(Fixtures.character(ref))

    private val aldric = character("cleric-5-life")
    private val vessa = character("rogue-3-thief")
    private val maelis = character("paladin-6-warlock-2")

    @Test
    fun theListIsTheHomeProjectionAndNoQueryOfItReadsADocument() {
        runBlocking {
            repo.create(aldric)
            clock.advance(1_000)
            repo.create(vessa)
            dao.calls.clear()

            val list = repo.list()
            assertEquals(listOf("Vessa Quickfinger", "Brother Aldric"), list.map { it.name }, "most recently touched first")
            assertEquals(
                listOf("Rogue 3 · Lightfoot Halfling", "Cleric 5 · Hill Dwarf"),
                list.map { it.summary },
                "the summary column, written at create",
            )
            assertEquals(listOf("summaries"), dao.calls, "one query, and it is the projection that has no json in it")
        }
    }

    @Test
    fun theListStopsAtTheLimitItIsGiven() {
        runBlocking {
            for (character in listOf(aldric, vessa, maelis)) {
                repo.create(character)
                clock.advance(1_000)
            }
            assertEquals(3, repo.list().size, "all three by default")
            assertEquals(listOf("Ser Maelis of the Pact"), repo.list(1).map { it.name }, "the newest one")
        }
    }

    @Test
    fun aPendingSaveIsOnTheListBeforeItLands() {
        runBlocking {
            repo.create(aldric)
            clock.advance(1_000)
            repo.create(vessa)
            clock.advance(1_000)

            repo.save(aldric.copy(name = "Aldric the Grey"))
            val list = repo.list()
            assertEquals(
                listOf("Aldric the Grey", "Vessa Quickfinger"),
                list.map { it.name },
                "the rename shows at once, and re-sorts the list it changed the order of",
            )
            assertEquals(clock.now(), list.first().updatedAt, "stamped when the player saved, not when the write lands")
        }
    }

    @Test
    fun loadDecodesTheStoredDocument() {
        runBlocking {
            val created = repo.create(aldric)
            assertEquals(created, repo.load(created.id), "what was stored is what comes back")
            assertNull(repo.load("no-such-character"), "an unknown id is null, not an error")
        }
    }

    /** A blank sheet would silently lose everything on it; S1 renders the message instead. */
    @Test
    fun aDocumentThatWillNotDecodeIsRaisedAndNotSwallowed() {
        runBlocking {
            dao.rows["broken"] = CharacterRow("broken", "Brother Aldric", "Cleric 5", 1L, """{"schemaVersion": 7}""")
            val e = assertFailsWith<RulesException>("a row that will not decode") { repo.load("broken") }
            assertEquals("unsupported schemaVersion 7 (expected 1)", e.message, "the sentence a screen can show")
        }
    }

    /**
     * The revert bug in one test: save, then reload before the debounce has fired, exactly as `onAppPause`
     * and the next `onScreenShow` do — and again after the write lands, because a read that went backwards
     * once it landed would be the same bug a moment later.
     */
    @Test
    fun aSaveIsVisibleToLoadBeforeAndAfterItLands() {
        runBlocking {
            val created = repo.create(aldric)
            saver.calls.clear()
            val renamed = created.copy(name = "Aldric the Grey")
            repo.save(renamed)

            assertEquals(listOf("enqueue:${created.id}"), saver.calls, "still only enqueued — nothing has been written")
            assertEquals(renamed, repo.load(created.id), "the reload reads the save, not the row it has not replaced yet")

            saver.land(dao)
            assertEquals(renamed, repo.load(created.id), "and the same character once the write lands")
        }
    }

    @Test
    fun aSaveWritesTheSummaryAndUpdatedAtWithTheDocument() {
        runBlocking {
            val created = repo.create(aldric)
            clock.advance(5_000)
            val levelled = created.copy(classes = created.classes.map { it.copy(level = 6) })
            repo.save(levelled)
            saver.land(dao)

            val row = dao.rows.getValue(created.id)
            assertEquals("Cleric 6 · Hill Dwarf", row.summary, "the denormalised line is re-derived at every write")
            assertEquals(clock.now(), row.updatedAt, "stamped from the injected clock")
            assertEquals(levelled, Model.decode(row.json), "and the document is the character itself")
        }
    }

    @Test
    fun anIllegalCharacterIsRefusedOnTheCallersThreadAndNeverReachesTheBuffer() {
        runBlocking { repo.create(aldric) }
        saver.calls.clear()
        val overloaded = aldric.copy(attacks = List(13) { aldric.attacks.first() })
        val e = assertFailsWith<RulesException>("thirteen attacks") { repo.save(overloaded) }
        assertEquals("13 attacks (at most 12)", e.message, "the sentence CharacterLimits wrote")
        assertEquals(emptyList(), saver.calls, "nothing illegal is ever held for writing")
    }

    @Test
    fun flushIsHandedStraightToTheSaver() {
        repo.flush()
        assertEquals(listOf("flush"), saver.calls, "the view model's exit path reaches the buffer")
    }

    @Test
    fun createRefusesTheSeventhCharacter() {
        runBlocking {
            for (i in 1..CharacterLimits.MAX_CHARACTERS) {
                repo.create(aldric.copy(id = "character-$i", name = "Character $i"))
                clock.advance(1_000)
            }
            val e = assertFailsWith<RulesException>("the seventh") { repo.create(aldric.copy(id = "character-7")) }
            assertEquals("6 characters already (at most 6) — delete one first", e.message, "what S0 shows")
            assertEquals(CharacterLimits.MAX_CHARACTERS, repo.count(), "and nothing was stored")
        }
    }

    @Test
    fun createRefusesAnIdThatIsAlreadyStored() {
        runBlocking {
            repo.create(aldric)
            val e = assertFailsWith<RulesException>("the same id twice") { repo.create(aldric.copy(name = "Someone Else")) }
            assertEquals("that character is already stored", e.message, "the message names what happened")
            assertEquals("Brother Aldric", dao.rows.getValue(aldric.id).name, "the stored character is untouched")
        }
    }

    @Test
    fun createStampsUpdatedAtAndLeavesTheDocumentExactlyAsGiven() {
        runBlocking {
            val created = repo.create(aldric)
            assertEquals(aldric, created, "an id the caller supplied is kept")
            val row = dao.rows.getValue(aldric.id)
            assertEquals(clock.now(), row.updatedAt, "stamped from the injected clock")
            assertEquals(aldric, Model.decode(row.json), "meta included — the repository has no business rewriting the model")
            assertEquals(
                listOf("drop:${aldric.id}"),
                saver.calls,
                "creation is written through and never enqueued; the drop is it reconciling the pending map",
            )
        }
    }

    /**
     * `create` is the one write that does not go through the buffer, so it is the one that could leave the
     * pending map disagreeing with the table. A save that preceded its create is the reachable way to get a
     * value held for an id with no row — the writer drops it, but the read-through would keep answering with
     * it, and the character the player just made would be invisible under it.
     */
    @Test
    fun createClearsAnythingLeftPendingForItsId() {
        runBlocking {
            repo.save(aldric)
            assertEquals(aldric, saver.pending()[aldric.id]?.character, "held for an id that has no row")

            val created = repo.create(aldric.copy(name = "Aldric the Grey"))
            assertEquals(
                listOf("enqueue:${aldric.id}", "drop:${aldric.id}"),
                saver.calls,
                "create reconciled the pending map before it wrote",
            )
            assertEquals(created, repo.load(created.id), "a read answers with what was created, not the leftover")
        }
    }

    @Test
    fun createMintsAnIdWhenTheCallerLeftItBlank() {
        runBlocking {
            val created = repo.create(aldric.copy(id = ""))
            assertEquals("minted-1", created.id, "from the injected id source")
            assertEquals(created, repo.load("minted-1"), "and it is what was stored")
        }
    }

    /**
     * The resurrection test. The order in `delete` is the whole defence: drop the pending save, then remove
     * the row. Reversed, the debounced write lands afterwards and the player finds a character they deleted
     * back on S0.
     */
    @Test
    fun deleteDropsThePendingSaveBeforeItRemovesTheRow() {
        runBlocking {
            val created = repo.create(aldric)
            saver.calls.clear()
            repo.save(created.copy(name = "Aldric the Grey"))
            repo.delete(created.id)

            assertEquals(
                listOf("enqueue:${created.id}", "drop:${created.id}"),
                saver.calls,
                "the pending save is dropped, and before the row is removed",
            )
            saver.land(dao)
            assertFalse(dao.rows.containsKey(created.id), "nothing wrote the character back")
            assertNull(repo.load(created.id), "it is gone to a reader too")
            assertEquals(0, repo.count(), "and gone from the count S0 checks")
        }
    }

    /** The second half of the same defence, for a write already taken out of the buffer when the delete ran. */
    @Test
    fun aWriteThatLostTheRaceWithADeleteDoesNotBringTheCharacterBack() {
        runBlocking {
            val created = repo.create(aldric)
            val inFlight = PendingSave(created.copy(name = "Aldric the Grey"), clock.now())
            repo.delete(created.id)

            assertFalse(DbCharacterRepository.write(dao, inFlight), "the writer refuses a row that is gone")
            assertEquals(0, dao.rows.size, "nothing was resurrected")
        }
    }

    /**
     * `create` is four statements with three suspension points between them, and it is the only door into the
     * table — a caller cancelled halfway would lose an entire transcribed character, or reconcile the pending
     * map and then store nothing. So the work is a child of the store's scope rather than the caller's: the
     * `await` is what gets cancelled, and the write finishes anyway.
     *
     * The gate below suspends the write inside `count()`, after `exists` has already run, so cancellation
     * lands exactly where a back press would land it.
     */
    @Test
    fun createFinishesOnTheStoresScopeEvenWhenItsCallerIsCancelled() {
        val rows = FakeCharacterDao()
        val reached = Channel<Unit>(Channel.UNLIMITED)
        val gate = Channel<Unit>(Channel.UNLIMITED)
        val slow = object : CharacterDao {
            override suspend fun summaries(limit: Int): List<CharacterSummaryRow> = rows.summaries(limit)
            override suspend fun json(id: String): String? = rows.json(id)
            override suspend fun exists(id: String): Boolean = rows.exists(id)
            override suspend fun upsert(row: CharacterRow) = rows.upsert(row)
            override suspend fun delete(id: String) = rows.delete(id)
            override suspend fun count(): Int {
                reached.send(Unit)
                gate.receive()
                return rows.count()
            }
        }
        val store = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val slowRepo = DbCharacterRepository(slow, FakeSaver(), store, clock::now)
        val caller = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        runBlocking {
            val call = caller.launch { slowRepo.create(aldric) }
            withTimeout(5_000) { reached.receive() }
            call.cancel()
            withTimeout(5_000) { call.join() }
            assertTrue(rows.rows.isEmpty(), "the caller is gone and the write has not got past the gate yet")

            gate.send(Unit)
            withTimeout(5_000) { while (rows.rows.isEmpty()) delay(5) }
            assertEquals(
                aldric.name,
                rows.rows.getValue(aldric.id).name,
                "the character the player transcribed is stored, though nothing is left waiting for it",
            )
        }
    }

    /**
     * The resurrection bug inverted, and the reason `delete` is on the store's scope too. Its two statements
     * are ordered so the pending save goes first; a caller cancelled between them would have discarded the
     * save and left the row, and the player would find a character they deleted still on S0 — from a screen
     * they had already navigated away from. `deleteDropsThePendingSaveBeforeItRemovesTheRow` pins the order
     * and cannot see this, because nothing there is ever cancelled.
     */
    @Test
    fun deleteFinishesOnTheStoresScopeEvenWhenItsCallerIsCancelled() {
        val rows = FakeCharacterDao(listOf(CharacterRow(aldric.id, aldric.name, "Cleric 5 · Hill Dwarf", 1L, "{}")))
        val reached = Channel<Unit>(Channel.UNLIMITED)
        val gate = Channel<Unit>(Channel.UNLIMITED)
        val slow = object : CharacterDao {
            override suspend fun summaries(limit: Int): List<CharacterSummaryRow> = rows.summaries(limit)
            override suspend fun json(id: String): String? = rows.json(id)
            override suspend fun exists(id: String): Boolean = rows.exists(id)
            override suspend fun upsert(row: CharacterRow) = rows.upsert(row)
            override suspend fun count(): Int = rows.count()
            override suspend fun delete(id: String) {
                reached.send(Unit)
                gate.receive()
                rows.delete(id)
            }
        }
        val store = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val slowSaver = FakeSaver()
        val slowRepo = DbCharacterRepository(slow, slowSaver, store, clock::now)
        val caller = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        runBlocking {
            val call = caller.launch { slowRepo.delete(aldric.id) }
            withTimeout(5_000) { reached.receive() }
            call.cancel()
            withTimeout(5_000) { call.join() }
            assertEquals(listOf("drop:${aldric.id}"), slowSaver.calls, "the pending save is already gone")
            assertTrue(rows.rows.containsKey(aldric.id), "and the row is still there, halfway through")

            gate.send(Unit)
            withTimeout(5_000) { while (rows.rows.isNotEmpty()) delay(5) }
            assertFalse(rows.rows.containsKey(aldric.id), "the delete finishes rather than leaving the character behind")
        }
    }

    @Test
    fun countIsWhatIsStored() {
        runBlocking {
            assertEquals(0, repo.count(), "a fresh install")
            repo.create(aldric)
            repo.create(vessa)
            assertEquals(2, repo.count(), "two characters")
            repo.delete(aldric.id)
            assertEquals(1, repo.count(), "one after a delete")
        }
    }

    @Test
    fun theStoredRowIsTheOneDocumentAndItsCopies() {
        runBlocking {
            val created = repo.create(maelis)
            val row = dao.rows.getValue(created.id)
            assertEquals(created.name, row.name, "the name column is a copy of the document's")
            assertEquals("Paladin 6 / Warlock 2 · Half-Elf", row.summary, "the summary column is Summaries.summaryOf")
            assertTrue(row.json.contains("\"schemaVersion\":1"), "and the document carries its own version")
        }
    }
}
