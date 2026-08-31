package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.compendium.CompendiumRef

/**
 * One drawn row of a compendium list — the flattened section model both S13.1's group lists and S13.4's
 * search results are built from (docs/UI-SPEC.md). Flattening the sections into one sequence is what lets the
 * whole screen be a single `LightLazyScrollView`: its scrollbar and drag maths need every row to be the same
 * height, so a header is a row of the same [ROW_HEIGHT_GRID_UNITS][dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS]
 * as an entry, not a differently sized band.
 *
 * Deliberately free of any group- or search-specific concept: a header is a label, an entry is a ref and the
 * string its list chose to show on the right ([RefDetail]).
 */
sealed interface ListRow {
    /** A non-tappable section label — lightened, letter-spaced upper case (`SectionHeaderRow` uppercases it). */
    data class Header(val label: String) : ListRow

    /** A tappable record row: [ref] is what the reader is opened with, [detail] the right-aligned lightened text. */
    data class Entry(val ref: CompendiumRef, val detail: String? = null) : ListRow
}

/**
 * Everything S13.1 draws: the top bar's [title] (already in the bar's upper case — it is a fixed screen name,
 * not data), the flattened [rows], and [loading], which holds only until the first load finishes.
 */
data class CompendiumListUiState(
    val title: String = "",
    val rows: List<ListRow> = emptyList(),
    val loading: Boolean = true,
)
