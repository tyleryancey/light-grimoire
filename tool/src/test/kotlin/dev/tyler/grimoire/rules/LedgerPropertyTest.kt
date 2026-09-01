package dev.tyler.grimoire.rules

import dev.tyler.grimoire.Fixtures
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ROADMAP M1 property tests: HP never negative, temp never stacks, a long rest never exceeds the maximum,
 * counters clamp — plus every invariant of docs/DATA-MODEL.md after arbitrary event sequences. The
 * sequences are drawn from the engine's own Mulberry32, so a failing seed replays exactly.
 */
class LedgerPropertyTest {
    private val characters = listOf("cleric-5-life", "paladin-6-warlock-2", "rogue-3-thief").map { Model.decode(Fixtures.character(it)) }
    private val sequences = 200
    private val eventsPerSequence = 25

    private fun Mulberry32.pick(n: Int): Int = die(n) - 1

    private fun Mulberry32.event(c: Character): Event = when (die(12)) {
        1 -> Event.Damage(amount = pick(80), critical = die(4) == 1)
        2 -> Event.Heal(amount = pick(60))
        3 -> Event.Temp(amount = pick(20))
        4 -> Event.DeathSave(d20 = die(20))
        5 -> Event.SpendHitDie(die = listOf(6, 8, 10, 12)[pick(4)], roll = die(12))
        6 -> Event.ShortRest
        7 -> Event.LongRest
        8 -> Event.Dawn
        9 -> Event.SpendSlot(level = die(9))
        10 -> Event.SpendPactSlot
        11 -> Event.TempDelta(delta = pick(21) - 10)
        else -> {
            val id = c.counters.getOrNull(pick(c.counters.size + 1))?.id ?: "no-such-counter"
            Event.CounterDelta(id = id, delta = pick(11) - 5)
        }
    }

    /** Applies [event]; a RulesException means the UI would have disabled the control, so the state stands. */
    private fun step(c: Character, event: Event): Character = try {
        Ledger.apply(c, event)
    } catch (rejected: RulesException) {
        c
    }

    private fun assertInvariants(c: Character, tag: String) {
        val hp = Derive.hpState(c)
        assertTrue(c.hp.damage in 0..c.hp.max, "$tag: damage ${c.hp.damage} outside 0..${c.hp.max}")
        assertTrue(c.hp.temp >= 0, "$tag: temp ${c.hp.temp}")
        assertTrue(hp.current >= 0, "$tag: current ${hp.current}")
        assertEquals(hp.current == 0, hp.down, "$tag: down flag")
        for (counter in c.counters) {
            assertTrue(counter.value in 0..counter.max, "$tag: counter ${counter.id} = ${counter.value} outside 0..${counter.max}")
        }
        for (pool in c.hitDice) {
            assertTrue(pool.used in 0..pool.total, "$tag: d${pool.die} used ${pool.used} outside 0..${pool.total}")
        }
        val maxima = Tables.spellSlots(c.classes)
        c.spellcasting?.let { sc ->
            assertEquals(9, sc.slotsUsed.size, "$tag: slotsUsed length")
            for (level in 1..9) {
                assertTrue(sc.slotsUsed[level - 1] in 0..maxima.slots[level - 1], "$tag: level $level used ${sc.slotsUsed[level - 1]} of ${maxima.slots[level - 1]}")
            }
            val pactUsed = sc.pactUsed
            if (pactUsed != null) assertTrue(pactUsed in 0..(maxima.pact?.count ?: 0), "$tag: pactUsed $pactUsed of ${maxima.pact}")
        }
        val saves = c.deathSaves
        assertTrue(saves.successes in 0..3 && saves.failures in 0..3, "$tag: death saves $saves")
        assertTrue(!(saves.stable && saves.dead), "$tag: stable and dead at once $saves")
        assertTrue(c.exhaustion >= 0, "$tag: exhaustion ${c.exhaustion}")
    }

    @Test
    fun everyInvariantHoldsAfterArbitraryEventSequences() {
        for (start in characters) {
            for (seed in 1..sequences) {
                val rng = Mulberry32(seed)
                var c = start
                repeat(eventsPerSequence) { index ->
                    val event = rng.event(c)
                    c = step(c, event)
                    assertInvariants(c, "${start.id} seed $seed event $index $event")
                }
            }
        }
    }

    @Test
    fun hitPointsNeverGoNegativeHoweverHardTheHit() {
        for (start in characters) {
            val rng = Mulberry32(7)
            repeat(500) {
                val wounded = step(start, Event.Damage(rng.pick(200), critical = rng.die(2) == 1))
                assertTrue(Derive.hpState(wounded).current >= 0, "${start.id}: current after damage")
                assertTrue(wounded.hp.damage <= wounded.hp.max, "${start.id}: damage capped at max")
            }
        }
    }

    @Test
    fun temporaryHitPointsNeverStack() {
        for (start in characters) {
            val rng = Mulberry32(11)
            repeat(500) {
                val before = start.copy(hp = start.hp.copy(temp = rng.pick(30)))
                val amount = rng.pick(30)
                assertEquals(max(before.hp.temp, amount), Ledger.temp(before, amount).hp.temp, "${start.id}: temp $amount onto ${before.hp.temp}")
            }
        }
    }

    /**
     * The correction is the only way temp HP comes down, so the clamp at 0 is the whole of its lower bound.
     * Half the starts are dead, because tempDelta is deliberately the one HP-adjacent function that does not
     * guard on `dead` — the whole-character equality below is what would go red if a guard were added.
     */
    @Test
    fun aTemporaryHitPointCorrectionClampsAtZeroAndTouchesNothingElse() {
        for (start in characters) {
            val rng = Mulberry32(19)
            repeat(500) {
                val corpse = rng.die(2) == 1
                val saves = if (corpse) DeathSaves(failures = 3, dead = true) else start.deathSaves
                val before = start.copy(hp = start.hp.copy(temp = rng.pick(30)), deathSaves = saves)
                val delta = rng.pick(61) - 30
                val corrected = Ledger.tempDelta(before, delta)
                assertTrue(corrected.hp.temp >= 0, "${start.id}: temp ${before.hp.temp} + $delta went negative")
                // Whole-character equality: the correction moves temp and nothing else — not HP, not the saves.
                val expected = before.copy(hp = before.hp.copy(temp = max(0, before.hp.temp + delta)))
                assertEquals(expected, corrected, "${start.id}: temp ${before.hp.temp} corrected by $delta, dead=$corpse")
            }
        }
    }

    @Test
    fun aLongRestNeverExceedsTheMaximumOrTheDicePool() {
        for (start in characters) {
            val rng = Mulberry32(13)
            repeat(300) {
                // Wander into an arbitrary state first, then rest.
                var c = start
                repeat(rng.die(15)) { c = step(c, rng.event(c)) }
                if (c.deathSaves.dead) return@repeat
                val rested = Ledger.longRest(c)
                val tag = "${start.id} after ${c.hp}"
                assertEquals(0, rested.hp.damage, "$tag: damage after long rest")
                assertEquals(0, rested.hp.temp, "$tag: temp after long rest")
                assertEquals(DeathSaves(), rested.deathSaves, "$tag: death saves after long rest")
                assertEquals(max(0, c.exhaustion - 1), rested.exhaustion, "$tag: exhaustion after long rest")
                for ((before, after) in c.hitDice.zip(rested.hitDice)) {
                    assertTrue(after.used in 0..before.used, "$tag: d${before.die} used ${after.used} was ${before.used}")
                }
                val regained = c.hitDice.sumOf { it.used } - rested.hitDice.sumOf { it.used }
                val total = c.hitDice.sumOf { it.total }
                assertTrue(regained <= max(1, total / 2), "$tag: regained $regained of $total dice")
                rested.spellcasting?.let { sc ->
                    assertEquals(List(9) { 0 }, sc.slotsUsed, "$tag: slots after long rest")
                    assertTrue(sc.pactUsed == null || sc.pactUsed == 0, "$tag: pact after long rest")
                }
                for ((before, after) in c.counters.zip(rested.counters)) {
                    val expected = if (before.reset == ResetTrigger.SHORT || before.reset == ResetTrigger.LONG) before.max else before.value
                    assertEquals(expected, after.value, "$tag: counter ${before.id} after long rest")
                }
            }
        }
    }

    @Test
    fun countersClampAtZeroAndAtTheirMaximum() {
        val start = characters.first { it.counters.size >= 3 }
        val rng = Mulberry32(17)
        repeat(500) {
            val counter = start.counters[rng.pick(start.counters.size)]
            val delta = rng.pick(41) - 20
            val moved = Ledger.counter(start, counter.id, delta)
            val expected = max(0, min(counter.max, counter.value + delta))
            assertEquals(expected, moved.counters.first { it.id == counter.id }.value, "${counter.id} ${counter.value} + $delta")
        }
    }
}
