package dev.tyler.grimoire.ui.hp

import dev.tyler.grimoire.rules.Event

/**
 * The natural d20 a tapped **success** pip records — the manual path for a player who rolled a physical
 * die and only wants the outcome logged (docs/UI-SPEC.md S3).
 *
 * 10 and [PIP_FAILURE_D20] are the two values nearest `Ledger.deathSave`'s `d20 >= 10` threshold that are
 * neither a natural 1 nor a natural 20, so a pip tap can never trigger the two special outcomes by
 * accident: a nat 20's "back at 1 HP" and a nat 1's two failures stay reachable only through `ROLL`, by
 * construction rather than by a guard someone could later remove.
 */
const val PIP_SUCCESS_D20 = 10

/** The natural d20 a tapped **failure** pip records — see [PIP_SUCCESS_D20]. */
const val PIP_FAILURE_D20 = 9

/** Three successes stabilise, three failures kill (SRD 5.1), so each strip is three pips long. */
const val DEATH_SAVE_PIPS = 3

/**
 * S3's verb × sign table, as the one pure function that turns a pad button (or a wheel detent) into a
 * rules event (docs/UI-SPEC.md S3):
 *
 * | Chip | `+n` | `−n` |
 * |---|---|---|
 * | DAMAGE | give it back — [Event.Heal] | take `n` — [Event.Damage] |
 * | HEAL | heal — [Event.Heal] | damage — [Event.Damage] |
 * | TEMP | grant `n`, the 2014 don't-stack rule — [Event.Temp] | lower by `n`, floored at 0 — [Event.TempDelta] |
 *
 * **[Verb.DAMAGE] and [Verb.HEAL] share a branch because the spec's table gives them the same mapping**,
 * and that is the design rather than an oversight: the sign decides which function is reached, so the two
 * chips differ only in which row of the pad the player reaches for first. TEMP is the verb that actually
 * changes the mapping, because its two signs reach two different engine functions.
 *
 * That pairing is also why a correction is not the inverse of its own verb. `DAMAGE +n` calls `heal`,
 * which clears the death saves outright once HP is back above 0; `HEAL −n` calls `damage`, which at 0 HP
 * adds a *failure* rather than subtracting HP. Undoing a mis-tap at 0 HP therefore has a death-save side
 * effect — which is what `UNDO` is for.
 *
 * `TEMP +n` grants ([Event.Temp], keeping the higher of the old and new numbers, so it can only raise);
 * `TEMP −n` corrects ([Event.TempDelta], signed and clamped at 0, and it stacks). A grant can never take
 * temporary hit points back down, which is the whole reason the pair stays distinct.
 *
 * A [delta] of 0 is treated as positive — the pad never produces one (`padMagnitudes` drops zeroes) and
 * the wheel is always ±1, and the resulting `Heal(0)`/`Temp(0)` is a no-op in the engine either way.
 */
fun eventFor(verb: Verb, delta: Int): Event = when (verb) {
    Verb.DAMAGE, Verb.HEAL -> if (delta < 0) Event.Damage(magnitude(delta)) else Event.Heal(delta)
    Verb.TEMP -> if (delta < 0) Event.TempDelta(delta) else Event.Temp(delta)
}

/**
 * The positive size of a negative [delta]. Through `Long` and clamped for the same reason `Signs.mod`
 * takes that route: negating `Int.MIN_VALUE` in place wraps back to itself, and a negative amount would
 * throw out of `Ledger.damage` on a screen the player is mid-combat on.
 */
private fun magnitude(delta: Int): Int =
    (-delta.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
