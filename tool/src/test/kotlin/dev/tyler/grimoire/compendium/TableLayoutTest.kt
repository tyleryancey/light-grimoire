package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TableLayout (plan D8) over the real tables: the three modes by packed width, the padEnd grid
 * geometry, the stacked shapes, and a whole-bundle sweep proving no grid line ever exceeds
 * [TableLayout.GRID_COMPACT_MAX]. The `proseFields`/`allProseFields` helpers used below live in
 * ProseFields.kt (same package), shared with [MarkdownSweepTest].
 */
class TableLayoutTest {
    private fun tablesOf(kind: Kind, key: String): List<Block.Table> {
        val record = kind.decodeAll(Fixtures.compendium(kind.file)).first { it.key == key }
        return proseFields(record).flatMap { Markdown.parse(it) }.filterIsInstance<Block.Table>()
    }

    private fun monos(blocks: List<Block>): List<Block.Mono> =
        blocks.map { assertIs<Block.Mono>(it, "grid mode lowers every row to Mono") }

    @Test
    fun multiclassingSlotsTableIsACompactGridOf41CharLines() {
        val slots = tablesOf(Kind.RULE_SECTIONS, "multiclassing").first { it.rows[0].firstOrNull() == "Level" }
        assertEquals(10, slots.rows[0].size, "level column plus nine slot columns")
        val lines = monos(TableLayout.lower(slots))
        assertEquals(21, lines.size, "header plus twenty levels")
        assertEquals("Level 1st 2nd 3rd 4th 5th 6th 7th 8th 9th", lines[0].text, "header line")
        assertTrue(lines[1].text.startsWith("1st   2   -"), "level 1 row under the same columns")
        for (line in lines) {
            assertEquals(41, line.text.length, "every grid line is exactly the packed width")
            assertTrue(line.compact, "41 > $GRID_DETAIL — compact size")
        }
        assertTrue(lines[0].secondary && lines.drop(1).none { it.secondary }, "only the header is secondary")
    }

    @Test
    fun animateObjectsIsTheWidestGridAt47Chars() {
        val table = tablesOf(Kind.SPELLS, "animate-objects").single()
        val lines = monos(TableLayout.lower(table))
        assertEquals(6, lines.size, "header plus five sizes")
        for (line in lines) {
            assertEquals(47, line.text.length, "the packed-width bound witness")
            assertTrue(line.compact, "47 > $GRID_DETAIL — compact size")
        }
    }

    @Test
    fun gridLinesShareIdenticalColumnOffsets() {
        val table = tablesOf(Kind.SPELLS, "animate-objects").single()
        val lines = monos(TableLayout.lower(table))
        val stripped = table.rows.map { row -> row.map(Markdown::plainText) }
        val widths = IntArray(stripped[0].size) { j -> stripped.maxOf { it[j].length } }
        var offset = 0
        for (j in widths.indices) {
            for (i in lines.indices) {
                assertEquals(
                    stripped[i][j],
                    lines[i].text.substring(offset, offset + widths[j]).trimEnd(),
                    "row $i column $j sits padEnd-aligned at offset $offset",
                )
            }
            offset += widths[j] + 1
        }
    }

    @Test
    fun aNarrowTableGetsTheDetailGrid() {
        val charges = tablesOf(Kind.MAGIC_ITEMS, "cube-of-force").first { it.rows[0].firstOrNull() == "Spell or Item" }
        val lines = monos(TableLayout.lower(charges))
        assertEquals("Spell or Item    Charges Lost", lines[0].text, "detail header line")
        for (line in lines) {
            assertEquals(29, line.text.length, "packed width 29")
            assertTrue(!line.compact, "29 <= $GRID_DETAIL — detail size")
        }
    }

    @Test
    fun aWideTwoColumnTableStacksAsRunInParagraphs() {
        val table = tablesOf(Kind.MAGIC_ITEMS, "wand-of-wonder").single()
        val lowered = TableLayout.lower(table)
        assertEquals(21, lowered.size, "one Para per data row, header not emitted")
        val first = assertIs<Block.Para>(lowered[0], "stacked two-column rows are Paras")
        assertEquals(Span.Text("01-05. ", bold = true), first.spans[0], "run-in label from cell 0")
        assertEquals(Span.Text("You cast slow."), first.spans[1], "cell 1 spans follow the label")
        assertTrue(lowered.all { it is Block.Para }, "no header labels in the two-column shape")
    }

    @Test
    fun aWideThreeColumnTableStacksWithHeaderLabelsAndKeepsTheUpstreamDefectVerbatim() {
        val sizes = tablesOf(Kind.RULE_SECTIONS, "reading-a-stat-block").first { it.rows[0] == listOf("Size", "Space", "Examples") }
        val lowered = TableLayout.lower(sizes)
        assertEquals(18, lowered.size, "six data rows times one Para plus two Fields")
        assertEquals(Block.Para(listOf(Span.Text("Tiny", bold = true))), lowered[0], "row label Para")
        assertEquals(Block.Field("Space: 2½ by 2½ ft.", secondary = true), lowered[1], "header-labelled Field")
        assertEquals(Block.Field("Examples: Imp, sprite", secondary = true), lowered[2], "second Field")
        assertTrue(
            Block.Field("Space: 5 b 5 ft.", secondary = true) in lowered,
            "the '5 b 5 ft.' upstream defect survives verbatim — the layout never edits cell text",
        )
    }

    @Test
    fun stackedRowsKeepItalicSpans() {
        val purpose = tablesOf(Kind.RULE_SECTIONS, "sentient-magic-items").first { it.rows[0] == listOf("d10", "Purpose") }
        val first = assertIs<Block.Para>(TableLayout.lower(purpose)[0], "special-purpose rows stack as Paras")
        assertEquals(Span.Text("1. ", bold = true), first.spans[0], "run-in label")
        assertEquals(Span.Text("Aligned:", italic = true), first.spans[1], "*Aligned:* stays italic")
        assertTrue(assertIs<Span.Text>(first.spans[2], "body span").text.startsWith(" The item seeks"), "body follows the italic label")
    }

    /**
     * Mode pins measured over the 29 Aug 2026 bundle: 34 detail grids, 15 compact grids,
     * 28 stacked — 77 tables. A regenerated bundle is supposed to fail these; re-measure then.
     */
    @Test
    fun sweepEveryTableInTheBundleLowersWithNoLineOver48() {
        var detail = 0
        var compact = 0
        var stacked = 0
        for ((kind, key, text) in allProseFields()) {
            for (table in Markdown.parse(text).filterIsInstance<Block.Table>()) {
                val lowered = TableLayout.lower(table)
                if (lowered.all { it is Block.Mono }) {
                    val lines = lowered.map { it as Block.Mono }
                    assertEquals(table.rows.size, lines.size, "${kind.id}/$key: one Mono per row")
                    for (line in lines) {
                        assertTrue(line.text.length <= TableLayout.GRID_COMPACT_MAX, "${kind.id}/$key: grid line '${line.text}' fits 48")
                    }
                    if (lines.any { it.compact }) compact++ else detail++
                } else {
                    stacked++
                    assertTrue(lowered.none { it is Block.Mono }, "${kind.id}/$key: stacked mode emits no Mono")
                    assertTrue(lowered.all { it is Block.Para || it is Block.Field }, "${kind.id}/$key: stacked mode is Paras and Fields")
                }
            }
        }
        assertEquals(34, detail, "detail-grid tables")
        assertEquals(15, compact, "compact-grid tables")
        assertEquals(28, stacked, "stacked tables")
    }

    private companion object {
        const val GRID_DETAIL = TableLayout.GRID_DETAIL_MAX
    }
}
