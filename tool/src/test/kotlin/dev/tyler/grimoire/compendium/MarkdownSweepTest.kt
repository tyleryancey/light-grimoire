package dev.tyler.grimoire.compendium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The parser over the whole bundle: totality, a line-accounting invariant, table shape, and an
 * exact census. The swept inventory itself lives in ProseFields.kt (same package), shared with
 * [TableLayoutTest]. The census numbers are pins measured over the 29 Aug 2026 bundle (index.json
 * hash-stamped); a future bundle regeneration that changes the prose is SUPPOSED to fail them —
 * re-measure and update the pins alongside the new assets, never loosen them to ranges.
 */
class MarkdownSweepTest {
    private val fields = allProseFields()
    private val parsed = fields.map { (kind, key, text) -> Triple(kind, key, Markdown.parse(text)) to text }

    private val separatorCell = Regex("^:?-+:?$")

    private fun footprint(block: Block, where: String): Int = when (block) {
        is Block.Heading, is Block.Bullet, is Block.Numbered -> 1
        is Block.Table -> block.rows.size
        is Block.Para -> 1 + block.spans.count { it is Span.LineBreak }
        is Block.Field, is Block.Mono -> fail("$where: parse() emitted a composition-only block $block")
    }

    @Test
    fun everyNonBlankLineLandsInExactlyOneBlock() {
        for ((triple, text) in parsed) {
            val (kind, key, blocks) = triple
            val where = "${kind.id}/$key"
            val lines = text.split('\n')
            val nonBlank = lines.count { it.isNotBlank() }
            val separators = lines.count { line ->
                line.isNotBlank() && line.trim().startsWith("|") && Markdown.pipeCells(line).all(separatorCell::matches)
            }
            val accounted = separators + blocks.sumOf { footprint(it, where) }
            assertEquals(nonBlank, accounted, "$where: non-blank lines vs lines accounted in blocks (+$separators separator rows)")
        }
    }

    @Test
    fun everyTableIsUniformWithAHeaderAndAtLeastOneDataRow() {
        for ((triple, _) in parsed) {
            val (kind, key, blocks) = triple
            for (table in blocks.filterIsInstance<Block.Table>()) {
                val width = table.rows[0].size
                assertTrue(table.rows.size >= 2, "${kind.id}/$key: table has a header and at least one data row")
                assertTrue(table.rows.all { it.size == width }, "${kind.id}/$key: every row has $width cells")
            }
        }
    }

    @Test
    fun censusOverTheWholeBundleMatchesTheMeasuredPins() {
        assertEquals(3724, fields.size, "prose fields swept")
        assertEquals(657, parsed.count { (_, text) -> text.isBlank() }, "blank fields (mostly empty higherLevel and creature text)")
        val blocks = parsed.flatMap { (triple, _) -> triple.third }
        assertEquals(77, blocks.count { it is Block.Table }, "tables in the bundle")
        assertEquals(314, blocks.count { it is Block.Bullet }, "bullets in the bundle")
        assertEquals(8, blocks.count { it is Block.Numbered }, "numbered items in the bundle")
        assertEquals(5281, blocks.count { it is Block.Para }, "paragraphs in the bundle")
        val headingLevels = blocks.filterIsInstance<Block.Heading>().groupingBy { it.level }.eachCount()
        assertEquals(mapOf(1 to 12, 2 to 67, 3 to 120, 4 to 111, 5 to 8), headingLevels, "headings by level (318 total)")
    }
}
