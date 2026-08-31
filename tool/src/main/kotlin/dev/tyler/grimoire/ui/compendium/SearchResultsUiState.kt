package dev.tyler.grimoire.ui.compendium

/**
 * Everything S13.4 draws (docs/UI-SPEC.md S13.4): the [query] that produced it — kept because `FIND` re-opens
 * the editor seeded with it — the flattened [rows] (kind-group headers over their hits, the same [ListRow]
 * shape S13.1 uses), [loading] while a query runs and [empty] for the one "No matches." line.
 *
 * [empty] is not `rows.isEmpty()`: the rows are also empty while the first query is still running, and the two
 * states draw different things.
 */
data class SearchResultsUiState(
    val query: String = "",
    val rows: List<ListRow> = emptyList(),
    val loading: Boolean = true,
    val empty: Boolean = false,
) {
    /** Hits, not rows — the headers between them are not results. Never above `Search.LIMIT`. */
    val results: Int get() = rows.count { it is ListRow.Entry }

    /** The top bar centre, `RESULTS (4)` as the wireframe draws it; bare while a query is still running. */
    val title: String get() = if (loading) TITLE else "$TITLE ($results)"

    private companion object {
        const val TITLE = "RESULTS"
    }
}
