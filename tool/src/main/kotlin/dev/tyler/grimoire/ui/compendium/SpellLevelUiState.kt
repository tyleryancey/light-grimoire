package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.compendium.CompendiumRef

/**
 * Everything S13.2 draws: the [level] on show, the [spells] of that level (`spellsByLevel`, finite by the
 * bundle — the largest level, 2nd, is 54 rows) and [loading], which holds while a level's query runs.
 *
 * The strings are derived here rather than in the screen so the tests read the same text the bar and the
 * stepper draw.
 */
data class SpellLevelUiState(
    val level: Int = MIN_LEVEL,
    val spells: List<CompendiumRef> = emptyList(),
    val loading: Boolean = true,
) {
    /** The top bar's second line (`LightTopBarCenter.TwoLineDetail`): "CANTRIPS" at level 0, "LEVEL n" above it. */
    val subtitle: String
        get() = if (level == MIN_LEVEL) "CANTRIPS" else "LEVEL $level"

    /**
     * The stepper row's own label — the S13.2 wireframe's `LEVEL 3 · 42`. The count joins only once the level's
     * rows are in hand, so a step never shows the new level beside the old level's count or a momentary zero.
     */
    val stepper: String
        get() = if (loading) subtitle else "$subtitle · ${spells.size}"

    /** Whether the `◂` arrow has a level to step down to — the clamp is drawn, not just enforced. */
    val hasLower: Boolean
        get() = level > MIN_LEVEL

    /** Whether the `▸` arrow has a level to step up to. */
    val hasHigher: Boolean
        get() = level < MAX_LEVEL

    companion object {
        /** Cantrips. */
        const val MIN_LEVEL = 0

        /** SRD 5.1 stops at 9th-level spells; the wheel and the arrows clamp here, with no wrap (S13.2). */
        const val MAX_LEVEL = 9
    }
}
