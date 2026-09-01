package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Focused Markdown-lite cases over real bundle excerpts (plan D7). Every named record here is the
 * witness the parser rule was designed against; MarkdownSweepTest proves the same rules over all
 * 1 992 records.
 */
class MarkdownTest {
    private fun record(kind: Kind, key: String): CompendiumRecord =
        kind.decodeAll(Fixtures.compendium(kind.file)).first { it.key == key }

    private fun text(kind: Kind, key: String): String = when (val r = record(kind, key)) {
        is SpellRecord -> r.text
        is MagicItemRecord -> r.text
        is TextRecord -> r.text
        is RuleRecord -> r.text
        else -> error("no text accessor wired for ${kind.id}/$key")
    }

    private fun tables(blocks: List<Block>): List<Block.Table> = blocks.filterIsInstance<Block.Table>()

    @Test
    fun paragraphsSplitOnBlankLines() {
        val blocks = Markdown.parse(text(Kind.MAGIC_ITEMS, "armor-of-vulnerability"))
        assertEquals(2, blocks.size, "armor-of-vulnerability is two paragraphs")
        assertTrue(blocks.all { it is Block.Para }, "both blocks are paragraphs")
    }

    @Test
    fun consecutivePlainLinesJoinIntoOneParaWithLineBreaks() {
        val dragon = assertIs<CreatureRecord>(record(Kind.CREATURES, "adult-brass-dragon"), "brass dragon decodes")
        val menu = dragon.actions.first { it.name == "Breath Weapons" }.text
        val blocks = Markdown.parse(menu)
        assertEquals(1, blocks.size, "the three-line breath menu is one block")
        val para = assertIs<Block.Para>(blocks[0], "the menu is a Para")
        assertEquals(2, para.spans.count { it is Span.LineBreak }, "one LineBreak per joined line")
        val first = assertIs<Span.Text>(para.spans[0], "menu starts with text")
        assertEquals("The dragon uses one of the following breath weapons.", first.text, "first joined line")
        val second = assertIs<Span.Text>(para.spans[2], "text after the first LineBreak")
        assertTrue(second.text.startsWith("Fire Breath."), "second joined line is the fire breath")
    }

    @Test
    fun headingsParseAtLevelsOneThroughFive() {
        val h1 = Markdown.parse(text(Kind.RULES, "adventuring"))
        assertEquals(Block.Heading(1, listOf(Span.Text("Adventuring"))), h1.first(), "rules chapter h1")
        val statBlock = Markdown.parse(text(Kind.RULE_SECTIONS, "reading-a-stat-block"))
        assertTrue(Block.Heading(2, listOf(Span.Text("Size"))) in statBlock, "h2 Size")
        assertTrue(Block.Heading(3, listOf(Span.Text("Size Categories"))) in statBlock, "h3 Size Categories")
        assertTrue(Block.Heading(4, listOf(Span.Text("Grapple Rules for Monsters"))) in statBlock, "h4 grapple rules")
        val spell = Markdown.parse(text(Kind.SPELLS, "animate-objects"))
        assertTrue(Block.Heading(5, listOf(Span.Text("Animated Object Statistics"))) in spell, "spells.json h5")
    }

    @Test
    fun dashBulletsAreOneBlockPerLine() {
        val acolyte = assertIs<CreatureRecord>(record(Kind.CREATURES, "acolyte"), "acolyte decodes")
        val blocks = Markdown.parse(acolyte.traits.first { it.name == "Spellcasting" }.text)
        assertEquals(3, blocks.size, "intro Para plus two bullets")
        assertIs<Block.Para>(blocks[0], "intro is a Para")
        val cantrips = assertIs<Block.Bullet>(blocks[1], "first list line is a Bullet")
        assertEquals(listOf<Span>(Span.Text("Cantrips (at will): light, sacred flame, thaumaturgy")), cantrips.spans, "bullet text without the marker")
        assertIs<Block.Bullet>(blocks[2], "second list line is a Bullet")
    }

    @Test
    fun starBulletsParseLikeDashBullets() {
        val blocks = Markdown.parse(text(Kind.MAGIC_ITEMS, "boots-of-the-winterlands"))
        assertEquals(4, blocks.size, "intro Para plus three star bullets")
        val bullets = blocks.filterIsInstance<Block.Bullet>()
        assertEquals(3, bullets.size, "three '* ' bullets")
        assertEquals(listOf<Span>(Span.Text("You have resistance to cold damage.")), bullets[0].spans, "first star bullet text")
    }

    @Test
    fun numberedItemsParseWithTwoSpacesAsWritten() {
        val blocks = Markdown.parse(text(Kind.RULE_SECTIONS, "making-an-attack"))
        val numbered = blocks.filterIsInstance<Block.Numbered>()
        assertEquals(listOf(1, 2, 3), numbered.map { it.number }, "making-an-attack numbers its three steps")
        val first = numbered[0].spans
        assertEquals(Span.Text("Choose a target.", bold = true), first[0], "step label is bold, marker and spaces gone")
        assertTrue(assertIs<Span.Text>(first[1], "step body").text.startsWith(" Pick a target"), "step body follows")
    }

    @Test
    fun numberedAcceptsASingleSpaceToo() {
        assertEquals(
            listOf<Block>(Block.Numbered(7, listOf(Span.Text("one space")))),
            Markdown.parse("7. one space"),
            "one space after the dot is enough",
        )
    }

    @Test
    fun numberedMarkerPastIntMaxStaysLiteralParagraphText() {
        assertEquals(
            listOf<Block>(Block.Numbered(2147483647, listOf(Span.Text("still a list item")))),
            Markdown.parse("2147483647. still a list item"),
            "Int.MAX_VALUE is the largest parsable marker",
        )
        assertEquals(
            listOf<Block>(Block.Para(listOf(Span.Text("2147483648. not a list item")))),
            Markdown.parse("2147483648. not a list item"),
            "a digit run past Int.MAX_VALUE is no marker — the whole line stays literal text, parse cannot throw",
        )
    }

    @Test
    fun aTwentyDigitNumberedMarkerFallsBackToParagraphTextInsteadOfThrowing() {
        // Twenty digits overflow Long as well as Int, so this case still holds if the marker type
        // ever widens. The claim under test is totality: parse returns, it does not throw.
        val line = "12345678901234567890. still just prose"
        assertEquals(
            listOf<Block>(Block.Para(listOf(Span.Text(line)))),
            Markdown.parse(line),
            "a 20-digit run is not a list marker — the line survives verbatim as one paragraph",
        )
    }

    @Test
    fun contiguousPipeRowsFormOneTable() {
        val blocks = Markdown.parse(text(Kind.RULE_SECTIONS, "reading-a-stat-block"))
        val sizes = tables(blocks).first { it.rows[0] == listOf("Size", "Space", "Examples") }
        assertEquals(7, sizes.rows.size, "header plus six size rows")
        assertEquals(listOf("Small", "5 b 5 ft.", "Giant rat, goblin"), sizes.rows[2], "cells trimmed, upstream text verbatim")
    }

    @Test
    fun blankSeparatedPipeRowsMergeIntoOneTable() {
        val blocks = Markdown.parse(text(Kind.SPELLS, "animate-objects"))
        val tables = tables(blocks)
        assertEquals(1, tables.size, "animate-objects has exactly one Table block")
        assertEquals(listOf("Size", "HP", "AC", "Attack", "Str", "Dex"), tables[0].rows[0], "header row")
        assertEquals(6, tables[0].rows.size, "header plus five data rows — the separator row is dropped")
        assertEquals(listOf("Tiny", "20", "18", "+8 to hit, 1d4 + 4 damage", "4", "18"), tables[0].rows[1], "first data row")
        assertTrue(tables[0].rows.none { row -> row.any { it.contains("---") } }, "no separator cells survive")
    }

    @Test
    fun pipeRowsWithoutATrailingPipeStillSplitIntoCells() {
        val tables = tables(Markdown.parse(text(Kind.MAGIC_ITEMS, "cube-of-force")))
        assertEquals(2, tables.size, "cube-of-force has two tables")
        assertEquals(listOf("Face", "Charges", "Effect"), tables[0].rows[0], "faces header")
        assertEquals(
            listOf("1", "1", "Gases, wind, and fog can't pass through the barrier."),
            tables[0].rows[1],
            "a row with no trailing pipe keeps all three cells",
        )
        assertEquals(7, tables[0].rows.size, "faces table rows")
        assertEquals(listOf("Spell or Item", "Charges Lost"), tables[1].rows[0], "charges-lost header")
    }

    @Test
    fun whitespaceOnlyLinesActAsBlank() {
        // reading-a-stat-block line 334 is "   ": it must end the ***Recharge…*** paragraph before the h4.
        val blocks = Markdown.parse(text(Kind.RULE_SECTIONS, "reading-a-stat-block"))
        val heading = blocks.indexOf(Block.Heading(4, listOf(Span.Text("Grapple Rules for Monsters"))))
        assertTrue(heading > 0, "the grapple h4 is present")
        val para = assertIs<Block.Para>(blocks[heading - 1], "the block before the h4 is the Recharge paragraph")
        assertEquals(Span.Text("Recharge after a Short or Long Rest.", bold = true, italic = true), para.spans[0], "recharge label")
        assertTrue(para.spans.none { it is Span.LineBreak }, "the whitespace-only line ended the paragraph, not joined it")
    }

    @Test
    fun tripleStarsParseAsBoldItalic() {
        val curse = assertIs<Block.Para>(Markdown.parse(text(Kind.MAGIC_ITEMS, "armor-of-vulnerability"))[1], "curse paragraph")
        assertEquals(Span.Text("Curse.", bold = true, italic = true), curse.spans[0], "***Curse.*** is bold italic")
        assertTrue(assertIs<Span.Text>(curse.spans[1], "curse body").text.startsWith(" This armor is cursed"), "body follows the label")
    }

    @Test
    fun anUnpairedStarStaysLiteral() {
        val table = tables(Markdown.parse(text(Kind.MAGIC_ITEMS, "deck-of-many-things"))).single()
        assertTrue(table.rows.contains(listOf("Ace of diamonds", "Vizier*")), "the footnote-marked cell is kept raw")
        assertEquals(listOf<Span>(Span.Text("Vizier*")), Markdown.spans("Vizier*"), "the lone star is literal text")
    }

    @Test
    fun emphasisLevelsOneTwoAndThreeMixInOneLine() {
        assertEquals(
            listOf<Span>(
                Span.Text("plain "),
                Span.Text("i", italic = true),
                Span.Text(" "),
                Span.Text("b", bold = true),
                Span.Text(" "),
                Span.Text("bi", bold = true, italic = true),
            ),
            Markdown.spans("plain *i* **b** ***bi***"),
            "star runs pair by length",
        )
    }

    @Test
    fun emptyTextYieldsNoBlocks() {
        assertEquals(emptyList(), Markdown.parse(""), "empty text")
        assertEquals(emptyList(), Markdown.parse("   \n\n  "), "whitespace-only text")
    }

    @Test
    fun trailingNewlinesAreIgnored() {
        val expected = listOf<Block>(Block.Para(listOf(Span.Text("Hello"))))
        assertEquals(expected, Markdown.parse("Hello"), "no trailing newline")
        assertEquals(expected, Markdown.parse("Hello\n"), "one trailing newline")
        assertEquals(expected, Markdown.parse("Hello\n\n"), "two trailing newlines")
    }
}
