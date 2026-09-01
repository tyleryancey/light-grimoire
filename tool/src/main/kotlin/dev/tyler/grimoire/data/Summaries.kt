package dev.tyler.grimoire.data

import dev.tyler.grimoire.rules.Character

/**
 * The one-line identity of a character — "Cleric 5 · Hill Dwarf", "Paladin 6 / Warlock 2 · Half-Elf".
 *
 * Written into [CharacterRow.summary] at every save so S0 can draw six two-line rows without decoding six
 * documents, and called directly for S1's identity line (docs/UI-SPEC.md S0, S1) so the two can never
 * disagree about how a character is named.
 *
 * The race is the character's own `race.name`, shown as the player transcribed it — "Lightfoot Halfling",
 * not a shortened "Halfling" (UI-SPEC.md S0). Only surrounding whitespace is dropped, which is a
 * transcription artefact and never something the player meant to see.
 */
object Summaries {
    /**
     * The stored summary is a display column and the UI ellipsizes it (`ui/common/Rows.kt`), so this is a
     * guard against an unbounded `race.name` reaching the database, not a layout decision.
     */
    const val MAX_LENGTH = 80

    /** U+00B7 MIDDLE DOT, spaced — the separator the wireframes use. */
    const val SEPARATOR = " · "

    fun summaryOf(character: Character): String {
        val classes = character.classes.joinToString(" / ") { "${title(it.classKey)} ${it.level}" }
        val race = character.race?.name?.trim().orEmpty()
        val text = listOf(classes, race).filter { it.isNotEmpty() }.joinToString(SEPARATOR)
        if (text.length <= MAX_LENGTH) return text
        // Cutting the assembled string can land inside the separator; a dangling " ·" is worse than none.
        return text.take(MAX_LENGTH).trimEnd(' ', '·')
    }

    /**
     * A class key as a display name: each `-`-separated word capitalised. Deliberately **not** a lookup
     * map against the bundled classes. All twelve SRD class keys are single lowercase words, so generic
     * title-casing produces the compendium's own display name for every one of them — SummariesTest pins
     * that against `Tables.HIT_DIE` and `assets/compendium/classes.json` — and a custom slug the player
     * invented for a non-SRD class ("blood-hunter" → "Blood Hunter") renders sensibly with nothing that
     * can drift out of date.
     */
    fun title(slug: String): String =
        slug.split('-').joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}
