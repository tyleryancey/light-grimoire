package dev.tyler.grimoire.compendium

/**
 * Lowers a parsed [Block.Table] into blocks the reader can render on the LP3's narrow screen
 * (plan D8). PURE: kotlin stdlib only. Three modes by packed width (per-column max cell length
 * after stripping paired emphasis, summed, plus one space between columns):
 *
 * - packed <= [GRID_DETAIL_MAX]: grid of [Block.Mono] lines at the detail size;
 * - packed <= [GRID_COMPACT_MAX]: the same grid, `compact = true` (animate-objects packs to 47,
 *   the widest real grid table — by construction no Mono line exceeds 48 chars);
 * - wider: stacked per data row, header row not emitted — a 2-column table becomes one run-in
 *   [Block.Para] per row (bold "cell0. " then cell1's spans, emphasis kept); 3+ columns become a
 *   bold [Block.Para] for cell0 plus one secondary [Block.Field] "header: cell" per other column.
 */
object TableLayout {
    /** Widest packed table shown as a detail-size grid. */
    const val GRID_DETAIL_MAX = 38

    /** Widest packed table shown as a grid at all (compact size above [GRID_DETAIL_MAX]). */
    const val GRID_COMPACT_MAX = 48

    fun lower(table: Block.Table): List<Block> {
        if (table.rows.isEmpty()) return emptyList()
        val stripped = table.rows.map { row -> row.map(Markdown::plainText) }
        val columns = stripped[0].size
        val widths = IntArray(columns) { j -> stripped.maxOf { it.getOrElse(j) { "" }.length } }
        val packed = widths.sum() + (columns - 1)
        return if (packed <= GRID_COMPACT_MAX) grid(stripped, widths, compact = packed > GRID_DETAIL_MAX)
        else stacked(table.rows, stripped, columns)
    }

    private fun grid(stripped: List<List<String>>, widths: IntArray, compact: Boolean): List<Block> =
        stripped.mapIndexed { i, row ->
            Block.Mono(
                text = row.mapIndexed { j, cell -> cell.padEnd(widths.getOrElse(j) { 0 }) }.joinToString(" "),
                compact = compact,
                secondary = i == 0,
            )
        }

    private fun stacked(raw: List<List<String>>, stripped: List<List<String>>, columns: Int): List<Block> {
        val header = stripped[0]
        val out = ArrayList<Block>()
        for (i in 1 until raw.size) {
            if (columns == 2) {
                val label = Span.Text(stripped[i].first() + ". ", bold = true)
                out.add(Block.Para(listOf(label) + Markdown.spans(raw[i].getOrElse(1) { "" })))
            } else {
                out.add(Block.Para(listOf(Span.Text(stripped[i].first(), bold = true))))
                for (j in 1 until columns) {
                    out.add(Block.Field("${header.getOrElse(j) { "" }}: ${stripped[i].getOrElse(j) { "" }}", secondary = true))
                }
            }
        }
        return out
    }
}
