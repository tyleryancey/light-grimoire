package dev.tyler.grimoire.data

import dev.tyler.grimoire.rules.Abilities
import dev.tyler.grimoire.rules.Ac
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.ClassEntry
import dev.tyler.grimoire.rules.Derive
import dev.tyler.grimoire.rules.HitDicePool
import dev.tyler.grimoire.rules.Hp
import dev.tyler.grimoire.rules.Tables

/**
 * The character a name and a class make — S12's own defaults, as a pure function (docs/UI-SPEC.md S12).
 *
 * **This is the part of the NEW flow that M4 keeps.** The two screens either side of it are an M3 interim:
 * S12 is a nine-step wizard and this build asks two of its questions (step 1's name, step 2's class), so
 * when M4 writes the rest, the screens are replaced and this function is what the last step still calls.
 * Everything it decides is a default the wizard's own steps state, and every default that has a rules
 * source takes it from the engine rather than restating it:
 *
 * - **abilities 10** — step 4's default, "six numbers, wheel per row, default 10". A 10 is a +0 modifier,
 *   so a sheet built here says nothing the player did not tell it.
 * - **hit points** — step 6's "default = average formula", which is [Derive.hpMaxAverage]: at level 1 that
 *   is the die's maximum plus the CON modifier, i.e. the die itself at CON 10.
 * - **hit dice** — one pool of one die, from [Tables.hitDieFor] (the same table `hpMaxAverage` reads, so
 *   the two can never disagree about what a bard rolls).
 * - **AC 10, speed 30** — step 6's manual AC and a speed the player corrects on the paper sheet's own
 *   number. Both are `Character`'s own defaults, restated here so this file reads as the whole decision.
 * - **no race, no background, no subclass, no skills, no spells, no counters** — the steps this flow does
 *   not ask. `Summaries.summaryOf` renders that as "Fighter 1" with no race half, which is the honest
 *   line for a character the player has only half transcribed (S12: "a half-transcribed character is
 *   still useful at the table").
 *
 * The name is trimmed and nothing else: leading and trailing spaces are a keyboard artefact, and every
 * other rule about a name — blank, or past [CharacterLimits.MAX_NAME] — belongs to [CharacterLimits],
 * which `CharacterRepository.create` runs on the way in. This function builds; the store refuses.
 */
object NewCharacter {
    /** Every ability at the wizard's default, which is also the score that adds nothing to anything. */
    const val DEFAULT_ABILITY = 10

    /** S12 step 6's AC default: the transcribed value, starting at unarmored 10. */
    const val DEFAULT_AC = 10

    /** S12 step 6's speed default, and `Character.speed`'s: 30 feet, corrected off the paper sheet. */
    const val DEFAULT_SPEED = 30

    /** The level every character starts at here; S12 step 2's wheel sets a real one in M4. */
    const val LEVEL = 1

    /**
     * A level-1 [character] of [classKey], named [name], stored under [id].
     *
     * [classKey] is a compendium class key — the twelve SRD classes the picker lists — so
     * [Tables.hitDieFor] answers without a `CustomClass`; a key it does not know raises
     * `RulesException`, which is the same refusal a screen shows for any other illegal character.
     */
    fun of(name: String, classKey: String, id: String): Character {
        val classes = listOf(ClassEntry(classKey = classKey, level = LEVEL))
        val die = Tables.hitDieFor(classKey, null)
        val abilities = Abilities(
            str = DEFAULT_ABILITY,
            dex = DEFAULT_ABILITY,
            con = DEFAULT_ABILITY,
            int = DEFAULT_ABILITY,
            wis = DEFAULT_ABILITY,
            cha = DEFAULT_ABILITY,
        )
        return Character(
            id = id,
            name = name.trim(),
            classes = classes,
            abilities = abilities,
            ac = Ac.Manual(DEFAULT_AC),
            speed = DEFAULT_SPEED,
            hp = Hp(max = Derive.hpMaxAverage(classes, abilities.con), damage = 0, temp = 0),
            hitDice = listOf(HitDicePool(die = die, total = LEVEL, used = 0)),
        )
    }
}
