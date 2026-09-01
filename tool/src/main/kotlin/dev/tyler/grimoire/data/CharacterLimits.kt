package dev.tyler.grimoire.data

import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.RulesException

/**
 * The cardinality gate on a character, run wherever a character is written — the wizard (S12) and the
 * repository. `Model.decode` deliberately checks shape and not cardinality, so this is where
 * `pipeline/schema/character.schema.json`'s item caps become an enforced rule rather than a document.
 *
 * **Refuses, never clamps.** `Ledger` clamps state silently — damage past `hp.max`, a counter past its
 * maximum — because there a nearest legal value obviously exists and the player meant the extreme. A
 * cardinality cap has no such value: dropping the 13th attack means discarding the line the player just
 * transcribed off their sheet, without ever saying so. So every violation here throws [RulesException],
 * the tool's one "this is not a legal character" signal, with a lowercase message naming what overflowed
 * and by how much, and the screen shows that sentence.
 *
 * [MAX_CHARACTERS] is not checked here: it is a property of the store, not of one character, and belongs
 * to the repository's `create`.
 */
object CharacterLimits {
    /** How many characters the tool holds (docs/PRD.md, CLAUDE.md); enforced in the repository's create. */
    const val MAX_CHARACTERS = 6

    /** schema `name.maxLength`. The schema's `minLength: 1` admits "   "; a blank name is refused here. */
    const val MAX_NAME = 40

    /** schema `classes.minItems` / `maxItems`. */
    const val MIN_CLASSES = 1
    const val MAX_CLASSES = 3

    /** schema `attacks.maxItems`. */
    const val MAX_ATTACKS = 12

    /** schema `items.maxItems`. */
    const val MAX_ITEMS = 60

    /** schema `notes.maxItems`. */
    const val MAX_NOTES = 20

    /** PRD F10 — an invariant of play, not a schema constraint: at most three attuned items. */
    const val MAX_ATTUNED = 3

    /** schema `spellcasting.slotsUsed` is exactly nine long — one entry per spell level. */
    const val SLOT_LEVELS = 9

    /**
     * @throws RulesException on the first violation, in the order below: name, classes, attacks, items,
     * notes, attunement, slots.
     */
    fun check(character: Character) {
        if (character.name.isBlank()) throw RulesException("a character needs a name")
        if (character.name.length > MAX_NAME) {
            throw RulesException("name is ${character.name.length} characters (at most $MAX_NAME)")
        }
        if (character.classes.size < MIN_CLASSES) throw RulesException("a character needs at least one class")
        if (character.classes.size > MAX_CLASSES) {
            throw RulesException("${character.classes.size} classes (at most $MAX_CLASSES)")
        }
        if (character.attacks.size > MAX_ATTACKS) {
            throw RulesException("${character.attacks.size} attacks (at most $MAX_ATTACKS)")
        }
        if (character.items.size > MAX_ITEMS) {
            throw RulesException("${character.items.size} items (at most $MAX_ITEMS)")
        }
        if (character.notes.size > MAX_NOTES) {
            throw RulesException("${character.notes.size} notes (at most $MAX_NOTES)")
        }
        val attuned = character.items.count { it.attuned }
        if (attuned > MAX_ATTUNED) throw RulesException("$attuned attuned items (at most $MAX_ATTUNED)")
        val slots = character.spellcasting?.slotsUsed
        if (slots != null && slots.size != SLOT_LEVELS) {
            throw RulesException("${slots.size} spell slot levels (expected $SLOT_LEVELS)")
        }
    }
}
