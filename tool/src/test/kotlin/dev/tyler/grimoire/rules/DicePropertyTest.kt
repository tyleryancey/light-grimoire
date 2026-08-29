package dev.tyler.grimoire.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Properties of the dice engine over generated expressions: every total lies within the bounds, the
 * average does too, rendering is a fixed point of parsing, and the critical rewrite doubles every dice
 * term. Expressions are built from the engine's own Mulberry32, so a failing seed replays exactly.
 */
class DicePropertyTest {
    private val sides = Dice.VALID_SIDES.toList()

    private fun Mulberry32.term(): Term = if (die(4) == 1) {
        ConstTerm(value = die(20) - 1, sign = if (die(5) == 1) -1 else 1)
    } else {
        val count = die(8)
        val keep = if (die(3) == 1) die(count) else null
        // keepHigh only exists in the text when there is a keep; keep the canonical form so render/parse is a fixed point.
        val keepHigh = keep == null || die(2) == 1
        DiceTerm(count = count, sides = sides[die(sides.size) - 1], keep = keep, keepHigh = keepHigh, sign = if (die(6) == 1) -1 else 1)
    }

    private fun Mulberry32.expression(): String = Dice.render(List(die(4)) { term() })

    @Test
    fun everyTotalAndAverageLieWithinTheBounds() {
        val rng = Mulberry32(2024)
        repeat(1000) { index ->
            val expression = rng.expression()
            val seed = rng.nextU32()
            val roll = Dice.roll(expression, seed)
            val bounds = Dice.bounds(expression)
            assertTrue(roll.total in bounds, "$expression@$seed total ${roll.total} outside $bounds ($index)")
            val average = Dice.average(expression)
            assertTrue(average >= bounds.first && average <= bounds.last, "$expression average $average outside $bounds")
        }
    }

    @Test
    fun renderingIsAFixedPointOfParsing() {
        val rng = Mulberry32(99)
        repeat(1000) {
            val expression = rng.expression()
            assertEquals(expression, Dice.render(Dice.parse(expression)), "render(parse) of $expression")
            assertEquals(expression, Dice.roll(expression, 1).expression, "roll.expression of $expression")
        }
    }

    @Test
    fun everyDieIsWithinItsSidesAndTheKeptDiceComeFromTheRolls() {
        val rng = Mulberry32(5)
        repeat(1000) {
            val expression = rng.expression()
            val roll = Dice.roll(expression, rng.nextU32())
            val diceTerms = roll.terms.filterIsInstance<DiceTerm>()
            assertEquals(diceTerms.size, roll.rolls.size, "$expression: one roll list per dice term")
            for ((term, dice) in diceTerms.zip(roll.rolls)) {
                assertEquals(term.count, dice.size, "$expression: ${term.text()} rolled ${dice.size} dice")
                assertTrue(dice.all { it in 1..term.sides }, "$expression: ${term.text()} rolled $dice")
            }
            for ((term, pair) in diceTerms.zip(roll.rolls.zip(roll.kept))) {
                val (dice, kept) = pair
                assertEquals(term.keep ?: term.count, kept.size, "$expression: ${term.text()} kept ${kept.size}")
                assertTrue(dice.toMutableList().let { pool -> kept.all(pool::remove) }, "$expression: kept $kept not drawn from $dice")
            }
        }
    }

    @Test
    fun aCriticalDoublesEveryDiceTermAndNothingElse() {
        val rng = Mulberry32(31)
        repeat(1000) {
            val terms = List(rng.die(3)) { rng.term() }
            val expression = Dice.render(terms)
            if (terms.filterIsInstance<DiceTerm>().any { it.count * 2 > Dice.MAX_DICE }) return@repeat
            val doubled = Dice.parse(Dice.withCritical(expression))
            assertEquals(terms.size, doubled.size, "$expression: term count after critical")
            for ((before, after) in terms.zip(doubled)) {
                when (before) {
                    is ConstTerm -> assertEquals(before, after, "$expression: constant unchanged")
                    is DiceTerm -> assertEquals(before.copy(count = before.count * 2, keep = before.keep?.let { it * 2 }), after, "$expression: dice doubled")
                }
            }
        }
    }
}
