package dev.tyler.grimoire.data

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Model
import dev.tyler.grimoire.rules.RulesException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The test double held against the thing it doubles.
 *
 * [FakeCharacterRepository] has no caller yet — the view models arrive in M3 task 2 — and that is precisely
 * when a divergence is cheapest to fix and most expensive to leave. A fake that refuses less than
 * `DbCharacterRepository` does not fail a screen's test, it **passes** one: a wizard that never handles "six
 * already" would be green against a fake with no cap, and a Home list that forgot to re-sort around a save
 * that has not landed would be green against a fake that sorted by the stored stamp. Both bugs would then
 * ship, because a JVM test is the only place either screen is exercised at all.
 *
 * So every case below is run twice, once through each repository, and asserted to agree. The real one is
 * driven over [FakeCharacterDao] and a [FakeSaver] whose pending saves are landed by hand, which is the same
 * split the fake has between its queue and its stored map.
 */
class FakeCharacterRepositoryTest {
    private fun character(ref: String) = Model.decode(Fixtures.character(ref))

    private val aldric = character("cleric-5-life")
    private val vessa = character("rogue-3-thief")

    /**
     * One case, run against both repositories: [check] gets a repository, the clock behind it, and the way to
     * make its pending saves land — the fake's `land()` and the real one's save loop draining.
     */
    private fun bothRepositories(check: suspend (CharacterRepository, ManualClock, suspend () -> Unit) -> Unit) {
        val fakeClock = ManualClock()
        val fake = FakeCharacterRepository(fakeClock::now) { "minted-1" }

        val realClock = ManualClock()
        val dao = FakeCharacterDao()
        val saver = FakeSaver()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val real = DbCharacterRepository(dao, saver, scope, realClock::now) { "minted-1" }

        runBlocking {
            check(fake, fakeClock) { fake.land() }
            check(real, realClock) { saver.land(dao) }
        }
    }

    @Test
    fun aPendingSaveIsOnTheListAtItsOwnStampAndResortsTheList() {
        bothRepositories { repo, clock, _ ->
            repo.create(aldric)
            clock.advance(1_000)
            repo.create(vessa)
            clock.advance(1_000)

            repo.save(aldric.copy(name = "Aldric the Grey"))
            val list = repo.list()
            assertEquals(
                listOf("Aldric the Grey", "Vessa Quickfinger"),
                list.map { it.name },
                "the rename shows at once and re-sorts the list it changed the order of",
            )
            assertEquals(clock.now(), list.first().updatedAt, "stamped when the player saved, not when the write lands")
        }
    }

    @Test
    fun aLandedSaveKeepsTheStampItWasMadeWith() {
        bothRepositories { repo, clock, land ->
            repo.create(aldric)
            clock.advance(1_000)
            repo.save(aldric.copy(name = "Aldric the Grey"))
            val stampedAt = clock.now()
            clock.advance(5_000)
            land()

            assertEquals(stampedAt, repo.list().first().updatedAt, "the debounce does not move the stamp")
        }
    }

    @Test
    fun theSeventhCharacterIsRefusedWithTheSameSentence() {
        bothRepositories { repo, clock, _ ->
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
    fun anIdThatIsAlreadyStoredIsRefusedWithTheSameSentence() {
        bothRepositories { repo, _, _ ->
            repo.create(aldric)
            val e = assertFailsWith<RulesException>("the same id twice") { repo.create(aldric.copy(name = "Someone")) }
            assertEquals("that character is already stored", e.message, "the message names what happened")
            assertEquals("Brother Aldric", repo.load(aldric.id)?.name, "the stored character is untouched")
        }
    }

    @Test
    fun aBlankIdIsMinted() {
        bothRepositories { repo, _, _ ->
            val created = repo.create(aldric.copy(id = ""))
            assertEquals("minted-1", created.id, "from the injected id source")
            assertEquals(created, repo.load("minted-1"), "and it is what was stored")
        }
    }

    @Test
    fun anIllegalCharacterIsRefusedBeforeAnythingIsHeld() {
        bothRepositories { repo, _, _ ->
            repo.create(aldric)
            val overloaded: Character = aldric.copy(attacks = List(13) { aldric.attacks.first() })
            val e = assertFailsWith<RulesException>("thirteen attacks") { repo.save(overloaded) }
            assertEquals("13 attacks (at most 12)", e.message, "the sentence CharacterLimits wrote")
            assertEquals("Brother Aldric", repo.load(aldric.id)?.name, "and nothing illegal was held")
        }
    }

    @Test
    fun aDeletedCharacterIsGoneToAReaderAndToTheCount() {
        bothRepositories { repo, _, land ->
            val created = repo.create(aldric)
            repo.save(created.copy(name = "Aldric the Grey"))
            repo.delete(created.id)
            land()

            assertNull(repo.load(created.id), "gone to a reader")
            assertEquals(0, repo.count(), "and gone from the count S0 checks")
            assertEquals(emptyList(), repo.list(), "and off the Home list")
        }
    }
}
