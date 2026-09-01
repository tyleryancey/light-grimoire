package dev.tyler.grimoire.rules

import dev.tyler.grimoire.Fixtures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Replays fixtures/events.json: every in-play scenario is run on its sample character and the end state
 * compared with the oracle's; the error cases must throw with the oracle's exact message. Decoding is
 * strict, so a new event type or end-state field in the fixture fails here, loudly.
 */
class EventsFixtureTest {
    @Serializable
    private data class SpentSlots(val slotsUsed: List<Int>, val pactUsed: Int?)

    /** The projection of a character the generator writes as `end` (see pipeline/reference/fixtures.py). */
    @Serializable
    private data class EndState(
        val hp: Hp,
        val deathSaves: DeathSaves,
        val hitDice: List<HitDicePool>,
        val counters: List<Counter>,
        val spellcasting: SpentSlots?,
        val exhaustion: Int,
        val conditions: List<String>,
        val derivedHp: DerivedHp,
    )

    @Serializable
    private data class Scenario(val name: String, val start: String, val events: List<Event>, val end: EndState)

    @Serializable
    private data class ErrorCase(val start: String, val events: List<Event>, val error: String)

    @Serializable
    private data class Fixture(
        @SerialName("\$comment") val comment: String,
        val scenarios: List<Scenario>,
        val errors: List<ErrorCase>,
    )

    private val fixture = Json.decodeFromString(Fixture.serializer(), Fixtures.text("events.json"))

    private fun endState(c: Character) = EndState(
        hp = c.hp,
        deathSaves = c.deathSaves,
        hitDice = c.hitDice,
        counters = c.counters,
        spellcasting = c.spellcasting?.let { SpentSlots(it.slotsUsed, it.pactUsed) },
        exhaustion = c.exhaustion,
        conditions = c.conditions,
        derivedHp = Derive.hpState(c),
    )

    private fun start(ref: String) = Model.decode(Fixtures.character(ref))

    @Test
    fun everyScenarioEndsInTheOracleState() {
        assertEquals(28, fixture.scenarios.size, "scenario count")
        for (scenario in fixture.scenarios) {
            val end = Ledger.run(start(scenario.start), scenario.events)
            assertEquals(scenario.end, endState(end), "scenario ${scenario.name}")
        }
    }

    @Test
    fun everyErrorCaseThrowsWithTheOracleMessage() {
        assertEquals(4, fixture.errors.size, "error count")
        for (case in fixture.errors) {
            val error = assertFailsWith<RulesException>("error ${case.error}") { Ledger.run(start(case.start), case.events) }
            assertEquals(case.error, error.message, "message for ${case.error}")
        }
    }

    @Test
    fun eventsNeverMutateTheirInput() {
        val before = start("fixture-cleric-5-life")
        val snapshot = before.copy()
        Ledger.run(before, listOf(Event.Damage(5), Event.SpendSlot(1), Event.CounterDelta("channel-divinity", 1), Event.LongRest))
        assertEquals(snapshot, before, "input character")
    }

    @Test
    fun longRestRegainsTheLargestDiceFirstWhateverTheListOrder() {
        // Six dice in all, so three come back; the d10 pool is listed second but is served first.
        val base = start("fixture-rogue-3-thief")
        val tired = base.copy(hitDice = listOf(HitDicePool(die = 6, total = 2, used = 2), HitDicePool(die = 10, total = 4, used = 4)))
        val rested = Ledger.longRest(tired)
        assertEquals(listOf(HitDicePool(6, 2, 2), HitDicePool(10, 4, 1)), rested.hitDice, "hit dice after long rest")
    }

    @Test
    fun negativeAmountsAreRejected() {
        val rogue = start("fixture-rogue-3-thief")
        assertEquals("damage must be ≥ 0", assertFailsWith<RulesException>("damage") { Ledger.damage(rogue, -1) }.message, "damage message")
        assertEquals("healing must be ≥ 0", assertFailsWith<RulesException>("heal") { Ledger.heal(rogue, -1) }.message, "heal message")
    }

    @Test
    fun unknownCountersAndEventsAreErrors() {
        val cleric = start("fixture-cleric-5-life")
        assertEquals("unknown counter rage", assertFailsWith<RulesException>("counter") { Ledger.counter(cleric, "rage", 1) }.message, "counter message")
    }

    @Test
    fun aCharacterWithoutASpellcastingBlockHasNoSlotsToSpend() {
        val rogue = start("fixture-rogue-3-thief")
        assertEquals("no level-1 slots left", assertFailsWith<RulesException>("slot") { Ledger.spendSlot(rogue, 1) }.message, "message")
    }

    @Test
    fun pactSlotsSpendFromZeroWhenPactUsedIsUnset() {
        val paladin = start("fixture-paladin-6-warlock-2")
        val fresh = paladin.copy(spellcasting = paladin.spellcasting!!.copy(pactUsed = null))
        assertEquals(1, Ledger.spendPactSlot(fresh).spellcasting?.pactUsed, "pactUsed after one spend")
        assertEquals("no pact slots left", assertFailsWith<RulesException>("pact") { Ledger.spendPactSlot(paladin) }.message, "warlock 2 has two pact slots, both spent")
    }

    @Test
    fun damageWhileStableResumesDeathSaves() {
        val paladin = start("fixture-paladin-6-warlock-2")
        val stable = paladin.copy(deathSaves = DeathSaves(stable = true))
        val hit = Ledger.damage(stable, 1)
        assertEquals(DeathSaves(successes = 0, failures = 1, stable = false, dead = false), hit.deathSaves, "death saves after damage while stable")
    }

    @Test
    fun theDeadStayDeadThroughHealingAndRest() {
        val paladin = start("fixture-paladin-6-warlock-2")
        val dead = paladin.copy(deathSaves = DeathSaves(dead = true))
        assertEquals(dead, Ledger.heal(dead, 20), "healing the dead")
        assertEquals(dead, Ledger.longRest(dead), "resting the dead")
        assertEquals(dead, Ledger.deathSave(dead, 20), "death save when dead")
        // The d10 pool has two dice left: without the guard this would heal and clear the death saves.
        assertEquals(dead, Ledger.spendHitDie(dead, 10, 6), "hit die when dead")
        // The d8 pool is spent, so this pins that the dead check runs before the "no dice left" error.
        assertEquals(dead, Ledger.spendHitDie(dead, 8, 4), "exhausted hit die when dead")
        // tempDelta is the deliberate exception: a correction is bookkeeping, so it applies while dead and
        // still revives nobody. Guarding it for symmetry with the four above fails this and its fixture.
        assertEquals(dead.copy(hp = dead.hp.copy(temp = 6)), Ledger.tempDelta(dead, 6), "temp correction when dead")
    }
}
