package dev.tyler.grimoire.rules

/** Thrown for a malformed or out-of-range dice expression (the oracle's `DiceError`). */
class DiceException(message: String) : IllegalArgumentException(message)

/**
 * mulberry32, bit-for-bit with pipeline/reference/dice.py and the JavaScript original (ADR-0006):
 *
 *     a = (a + 0x6D2B79F5) | 0
 *     t = imul(a ^ (a >>> 15), 1 | a)
 *     t = (t + imul(t ^ (t >>> 7), 61 | t)) ^ t
 *     return (t ^ (t >>> 14)) >>> 0
 *
 * Kotlin `Int` addition and multiplication wrap exactly like `| 0` and `imul`; `ushr` is `>>>`. A die is
 * `floor(u32 / 2^32 * sides) + 1`, computed with one `Long` multiply so no floating point is involved.
 * fixtures/rng.json pins the first outputs for six seeds.
 */
class Mulberry32(seed: Int) {
    /** Only the low 32 bits of a seed matter (the oracle masks with 0xFFFFFFFF). */
    constructor(seed: Long) : this(seed.toInt())

    private var state: Int = seed

    /** The next raw 32-bit word; read it as unsigned with `toLong() and 0xFFFFFFFFL`. */
    fun nextU32(): Int {
        state += 0x6D2B79F5
        var t = (state xor (state ushr 15)) * (1 or state)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return t xor (t ushr 14)
    }

    fun die(sides: Int): Int = ((nextU32().toLong() and 0xFFFFFFFFL) * sides ushr 32).toInt() + 1
}

/** One term of a parsed expression; [sign] is +1 or -1. */
sealed interface Term {
    val sign: Int
}

data class DiceTerm(
    val count: Int,
    val sides: Int,
    /** Number of dice kept for `kh`/`kl`, or null to keep them all. */
    val keep: Int? = null,
    val keepHigh: Boolean = true,
    override val sign: Int = 1,
) : Term {
    fun text(): String = buildString {
        append(count).append('d').append(sides)
        if (keep != null) append(if (keepHigh) "kh" else "kl").append(keep)
    }
}

data class ConstTerm(val value: Int, override val sign: Int = 1) : Term

/**
 * The outcome of one expression: [rolls] holds every die per dice term in the order drawn, [kept] the
 * dice that counted (sorted when a `kh`/`kl` applied), [natural] the kept d20 of a single-d20 check for
 * CRIT / MISS display. [expression] is the normalised text (`d6` renders as `1d6`).
 */
data class Roll(
    val expression: String,
    val seed: Int,
    val terms: List<Term>,
    val rolls: List<List<Int>>,
    val kept: List<List<Int>>,
    val total: Int,
    val natural: Int?,
)

/** Advantage and disadvantage are a rewrite of the d20 term, never tokens of the grammar. */
enum class Advantage { NONE, ADV, DIS }

/**
 * Dice notation — a deliberate subset of Roll20 notation, everything a 5E sheet needs and nothing that
 * invites a keyboard:
 *
 *     expr := term (("+" | "-") term)*
 *     term := dice | INT
 *     dice := [INT] "d" INT [("kh" | "kl") INT]
 *
 * Spaces are ignored and letters are case-insensitive. Sides are one of 2, 3, 4, 6, 8, 10, 12, 20, 100;
 * 1 <= count <= 100; 1 <= keep <= count. Dice are drawn left to right, term by term, from one stream.
 */
object Dice {
    val VALID_SIDES: Set<Int> = setOf(2, 3, 4, 6, 8, 10, 12, 20, 100)
    const val MAX_DICE = 100

    /** Splits "-1d4+3" into "-1d4", "+3"; a leftover character (as in "++1" or "1d20+") means malformed. */
    private val chunk = Regex("[+-]?[^+-]+")
    private val termPattern = Regex("^(?:(\\d+)?d(\\d+)(?:(kh|kl)(\\d+))?|(\\d+))$")

    fun parse(expression: String): List<Term> {
        val text = expression.replace(" ", "").lowercase()
        if (text.isEmpty()) throw DiceException("empty expression")
        val tokens = chunk.findAll(text).map { it.value }.toList()
        if (tokens.isEmpty() || tokens.joinToString("") != text) throw DiceException("malformed expression '$expression'")
        val terms = ArrayList<Term>(tokens.size)
        for (token in tokens) {
            val sign = if (token.startsWith("-")) -1 else 1
            val body = token.trimStart('+', '-')
            val match = termPattern.matchEntire(body) ?: throw DiceException("bad term '$body' in '$expression'")
            val constant = match.groups[5]?.value
            if (constant != null) {
                terms += ConstTerm(number(constant, expression), sign)
                continue
            }
            val count = match.groups[1]?.value?.let { number(it, expression) } ?: 1
            val sides = number(match.groups[2]!!.value, expression)
            if (sides !in VALID_SIDES) throw DiceException("unsupported die d$sides")
            if (count !in 1..MAX_DICE) throw DiceException("dice count $count out of range 1..$MAX_DICE")
            var keep: Int? = null
            var keepHigh = true
            val keepKind = match.groups[3]?.value
            if (keepKind != null) {
                keep = number(match.groups[4]!!.value, expression)
                keepHigh = keepKind == "kh"
                if (keep !in 1..count) throw DiceException("keep $keep out of range 1..$count")
            }
            terms += DiceTerm(count, sides, keep, keepHigh, sign)
        }
        return terms
    }

    /** The oracle's integers are unbounded; here a number that does not fit an Int is simply invalid. */
    private fun number(digits: String, expression: String): Int =
        digits.toIntOrNull() ?: throw DiceException("number $digits too large in '$expression'")

    fun render(terms: List<Term>): String = buildString {
        terms.forEachIndexed { index, term ->
            if (index == 0) {
                if (term.sign < 0) append('-')
            } else {
                append(if (term.sign < 0) '-' else '+')
            }
            append(
                when (term) {
                    is DiceTerm -> term.text()
                    is ConstTerm -> term.value.toString()
                },
            )
        }
    }

    /** Rolls [expression] on a fresh generator for [seed], or on a caller-supplied stream. */
    fun roll(expression: String, seed: Int, rng: Mulberry32 = Mulberry32(seed)): Roll {
        val terms = parse(expression)
        val rolls = ArrayList<List<Int>>()
        val kept = ArrayList<List<Int>>()
        var total = 0
        for (term in terms) {
            when (term) {
                is ConstTerm -> total += term.sign * term.value
                is DiceTerm -> {
                    val dice = List(term.count) { rng.die(term.sides) }
                    val counted = when {
                        term.keep == null -> dice
                        term.keepHigh -> dice.sortedDescending().take(term.keep)
                        else -> dice.sorted().take(term.keep)
                    }
                    rolls += dice
                    kept += counted
                    total += term.sign * counted.sum()
                }
            }
        }
        val natural = terms.filterIsInstance<DiceTerm>().zip(kept)
            .firstOrNull { (term, counted) -> term.sides == 20 && counted.size == 1 }
            ?.second?.first()
        return Roll(render(terms), seed, terms, rolls, kept, total, natural)
    }

    /** Rolls several expressions from ONE stream — the Turn screen's attack-then-damage tap. */
    fun rollMany(expressions: List<String>, seed: Int): List<Roll> {
        val rng = Mulberry32(seed)
        return expressions.map { roll(it, seed, rng) }
    }

    /**
     * Rewrites the first plain `1d20` term as `2d20kh1` (advantage) or `2d20kl1` (disadvantage).
     * [Advantage.NONE] returns the expression exactly as given, unparsed.
     */
    fun withAdvantage(expression: String, mode: Advantage): String {
        if (mode == Advantage.NONE) return expression
        var done = false
        val out = parse(expression).map { term ->
            if (!done && term is DiceTerm && term.sides == 20 && term.count == 1 && term.keep == null) {
                done = true
                DiceTerm(2, 20, 1, mode == Advantage.ADV, term.sign)
            } else {
                term
            }
        }
        if (!done) throw DiceException("no 1d20 term to apply advantage to")
        return render(out)
    }

    /**
     * Critical-hit damage: every dice term is rolled twice and the modifiers added once, so the count
     * (and any keep) of every dice term doubles while constants stay: `1d8+3` becomes `2d8+3`.
     */
    fun withCritical(expression: String): String = render(
        parse(expression).map { term ->
            if (term is DiceTerm) {
                if (term.count * 2 > MAX_DICE) throw DiceException("critical rewrite exceeds the dice cap")
                DiceTerm(term.count * 2, term.sides, term.keep?.let { it * 2 }, term.keepHigh, term.sign)
            } else {
                term
            }
        },
    )

    /** Lowest and highest possible totals; a kept term counts only its kept dice. */
    fun bounds(expression: String): IntRange {
        var lo = 0
        var hi = 0
        for (term in parse(expression)) {
            when (term) {
                is ConstTerm -> {
                    lo += term.sign * term.value
                    hi += term.sign * term.value
                }
                is DiceTerm -> {
                    val n = term.keep ?: term.count
                    if (term.sign < 0) {
                        lo -= n * term.sides
                        hi -= n
                    } else {
                        lo += n
                        hi += n * term.sides
                    }
                }
            }
        }
        return lo..hi
    }

    /** Expected total, treating a kept term as that many plain dice (used for "take the average" HP). */
    fun average(expression: String): Double {
        var total = 0.0
        for (term in parse(expression)) {
            when (term) {
                is ConstTerm -> total += term.sign * term.value
                is DiceTerm -> {
                    val n = term.keep ?: term.count
                    total += term.sign * n * (term.sides + 1) / 2.0
                }
            }
        }
        return total
    }
}
