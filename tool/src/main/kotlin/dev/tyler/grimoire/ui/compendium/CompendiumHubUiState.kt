package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.compendium.KindGroup

/**
 * One row of the S13 hub (docs/UI-SPEC.md S13): the [group] it pushes, the [label] it is named by and the
 * [count] the spec shows beside that label — null for the four groups it leaves uncounted.
 *
 * The count is drawn *inside* the name, as the wireframe writes it (`SPELLS (319)`), not in the right-aligned
 * `Detail` column S13.1 and S13.4 use for a spell's school or a feature's class. [rowText] is that composition,
 * kept here so the screen cannot spell it one way and the test another.
 */
data class HubRow(val group: KindGroup, val label: String, val count: Int?) {
    /** The `NavRow` name: `CONDITIONS (15)` where there is a count, plain `RULES` where there is not. */
    val rowText: String get() = if (count == null) label else "$label ($count)"
}

/** Everything S13 draws: the nine [rows] and [loading], which holds only until the counts arrive. */
data class CompendiumHubUiState(
    val rows: List<HubRow> = emptyList(),
    val loading: Boolean = true,
)

/**
 * The nine hub labels, in the wireframe's own upper case — the single source for a group's name, because three
 * screens have to agree on it: the S13 row, the S13.1 top bar it pushes ("the top bar centre is that row's own
 * label") and the S13.4 header that gathers a group's search hits.
 *
 * LOOKUP is not a hub row (S13: the six lookup kinds are reached only through FIND and reader cross-links); it
 * is named here anyway so the `when` is total and the generic list screen can still title itself if a future
 * screen borrows it.
 */
object GroupLabel {
    fun of(group: KindGroup): String = when (group) {
        KindGroup.SPELLS -> "SPELLS"
        KindGroup.CONDITIONS -> "CONDITIONS"
        KindGroup.RULES -> "RULES"
        KindGroup.CLASSES_AND_FEATURES -> "CLASSES & FEATURES"
        KindGroup.RACES -> "RACES"
        KindGroup.BACKGROUNDS_AND_FEATS -> "BACKGROUNDS & FEATS"
        KindGroup.EQUIPMENT -> "EQUIPMENT"
        KindGroup.MAGIC_ITEMS -> "MAGIC ITEMS"
        KindGroup.CREATURES -> "CREATURES"
        KindGroup.LOOKUP -> "LOOKUP"
    }
}
