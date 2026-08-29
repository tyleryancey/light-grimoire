package dev.tyler.grimoire.rules

import dev.tyler.grimoire.Fixtures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Replays fixtures/dice.json: every expression rolled with three seeds (dice consumed left to right, term
 * by term), the rejected strings, the advantage and critical rewrites, and the attack+damage pairs that
 * share one stream. Decoding is strict (every fixture key is declared) so a new field fails here, loudly.
 */
class DiceFixtureTest {
    @Serializable
    private data class Case(
        val expr: String,
        val normalized: String,
        val seed: Int,
        val rolls: List<List<Int>>,
        val kept: List<List<Int>>,
        val total: Int,
        val natural: Int?,
        val min: Int,
        val max: Int,
        val average: Double,
    )

    @Serializable
    private data class AdvantageCase(val expr: String, val mode: String, val result: String)

    @Serializable
    private data class CriticalCase(val expr: String, val result: String)

    @Serializable
    private data class PairCase(val seed: Int, val expressions: List<String>, val rolls: List<List<List<Int>>>, val totals: List<Int>)

    @Serializable
    private data class Fixture(
        @SerialName("\$comment") val comment: String,
        val cases: List<Case>,
        val invalid: List<String>,
        val advantage: List<AdvantageCase>,
        val critical: List<CriticalCase>,
        val pairs: List<PairCase>,
    )

    private val fixture = Json.decodeFromString(Fixture.serializer(), Fixtures.text("dice.json"))

    @Test
    fun everyCaseRollsTheOracleDiceAndTotal() {
        for (case in fixture.cases) {
            val roll = Dice.roll(case.expr, case.seed)
            val tag = "${case.expr}@${case.seed}"
            assertEquals(case.normalized, roll.expression, "normalized $tag")
            assertEquals(case.rolls, roll.rolls, "rolls $tag")
            assertEquals(case.kept, roll.kept, "kept $tag")
            assertEquals(case.total, roll.total, "total $tag")
            assertEquals(case.natural, roll.natural, "natural $tag")
            assertEquals(case.seed, roll.seed, "seed $tag")
        }
    }

    @Test
    fun everyCaseHasTheOracleBoundsAndAverage() {
        for (case in fixture.cases) {
            assertEquals(case.min..case.max, Dice.bounds(case.expr), "bounds ${case.expr}")
            assertEquals(case.average, Dice.average(case.expr), "average ${case.expr}")
        }
    }

    @Test
    fun everyInvalidExpressionIsRejected() {
        for (bad in fixture.invalid) {
            assertFailsWith<DiceException>("invalid '$bad'") { Dice.parse(bad) }
        }
    }

    @Test
    fun advantageRewritesTheFirstPlainD20() {
        for (case in fixture.advantage) {
            val mode = Advantage.valueOf(case.mode.uppercase())
            assertEquals(case.result, Dice.withAdvantage(case.expr, mode), "advantage ${case.expr} ${case.mode}")
        }
    }

    @Test
    fun advantageNeedsAPlainD20Term() {
        assertFailsWith<DiceException>("2d6 adv") { Dice.withAdvantage("2d6", Advantage.ADV) }
    }

    @Test
    fun criticalDoublesEveryDiceTermAndKeepsConstants() {
        for (case in fixture.critical) {
            assertEquals(case.result, Dice.withCritical(case.expr), "critical ${case.expr}")
        }
    }

    @Test
    fun pairsRollBothExpressionsFromOneStream() {
        for (case in fixture.pairs) {
            val rolls = Dice.rollMany(case.expressions, case.seed)
            assertEquals(case.rolls, rolls.map { it.rolls }, "pair rolls @${case.seed}")
            assertEquals(case.totals, rolls.map { it.total }, "pair totals @${case.seed}")
        }
    }

    @Test
    fun oversizedNumbersAreRejectedNotCrashed() {
        assertFailsWith<DiceException>("huge constant") { Dice.parse("1d20+99999999999") }
        assertFailsWith<DiceException>("huge count") { Dice.parse("99999999999d6") }
    }
}
