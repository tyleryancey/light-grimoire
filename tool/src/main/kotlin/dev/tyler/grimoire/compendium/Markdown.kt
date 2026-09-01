package dev.tyler.grimoire.compendium

/**
 * Markdown-lite parser for the bundled compendium prose (plan D7). PURE: kotlin stdlib only, no
 * Android/Compose types — the reader screens turn [Block]s into rows, this file never renders.
 *
 * The dialect is exactly what the pipeline emits (MarkdownSweepTest sweeps every prose field of all
 * 1 992 records): `#`–`#####` headings, `- ` / `* ` bullets, `N.  ` numbered items, pipe tables in
 * two dialects (contiguous rows in rule_sections; blank-line-separated rows in spells/magic_items),
 * `*italic*` / `**bold**` / `***bold-italic***` star emphasis, and plain paragraphs whose adjacent
 * non-blank lines join with [Span.LineBreak] (dragon breath-weapon menus). Underscore emphasis,
 * links, code and blockquotes are not in the bundle and are not parsed.
 *
 * The parser is total: every line classifies somewhere, so [Markdown.parse] cannot throw — a
 * numbered marker whose digit run overflows Int is not a list item and stays literal paragraph text.
 */
sealed interface Span {
    data class Text(val text: String, val bold: Boolean = false, val italic: Boolean = false) : Span

    /** A newline inside one paragraph — adjacent non-blank plain lines stay one block. */
    data object LineBreak : Span
}

sealed interface Block {
    /** `#`..`#####` as written, level 1..5. */
    data class Heading(val level: Int, val spans: List<Span>) : Block

    data class Para(val spans: List<Span>) : Block

    /** One `- ` or `* ` item; each source line is its own block. */
    data class Bullet(val spans: List<Span>) : Block

    /** One `N.  ` item (the bundle writes two spaces; one or more accepted). */
    data class Numbered(val number: Int, val spans: List<Span>) : Block

    /** rows[0] is the header; cells are raw (emphasis markers kept — TableLayout strips them). */
    data class Table(val rows: List<List<String>>) : Block

    /** Composition-only label/value line ("Space: 5 b 5 ft."); never produced by [Markdown.parse]. */
    data class Field(val text: String, val secondary: Boolean = false) : Block

    /** Composition-only: one column-aligned table line for the grid modes; never produced by parse. */
    data class Mono(val text: String, val compact: Boolean, val secondary: Boolean) : Block
}

object Markdown {
    private val heading = Regex("^(#{1,5}) ")
    private val numbered = Regex("^(\\d+)\\.\\s+")

    /** A table separator cell: dashes with optional alignment colons (`---`, `:---`, `---:`). */
    private val separatorCell = Regex("^:?-+:?$")

    /**
     * Line-oriented state machine (not a blank-line splitter). A blank (or whitespace-only) line
     * ends the current block, with one exception: while a [Block.Table] is open and the next
     * non-blank line is another pipe row, the blank is skipped — that single rule merges the
     * blank-line-separated table dialect while leaving contiguous tables untouched.
     */
    fun parse(text: String): List<Block> {
        val blocks = ArrayList<Block>()
        val lines = text.split('\n')
        var paraLines: MutableList<String>? = null
        var tableRows: MutableList<List<String>>? = null

        fun flush() {
            paraLines?.let { joined ->
                val spans = ArrayList<Span>()
                joined.forEachIndexed { i, line ->
                    if (i > 0) spans.add(Span.LineBreak)
                    spans.addAll(spans(line))
                }
                blocks.add(Block.Para(spans))
            }
            paraLines = null
            tableRows?.let { rows -> if (rows.isNotEmpty()) blocks.add(Block.Table(rows)) }
            tableRows = null
        }

        for (index in lines.indices) {
            val line = lines[index]
            if (line.isBlank()) {
                if (tableRows != null && nextNonBlankIsPipeRow(lines, index + 1)) continue
                flush()
                continue
            }
            val headingMatch = heading.find(line)
            val numberedMatch = numbered.find(line)
            // Totality guard: a digit run past Int.MAX_VALUE is no list marker — the line falls
            // through to the paragraph branch as literal text instead of throwing.
            val numberedValue = numberedMatch?.groupValues?.get(1)?.toIntOrNull()
            when {
                line.trim().startsWith("|") -> {
                    val cells = pipeCells(line)
                    if (cells.all(separatorCell::matches)) {
                        // Separator row: dropped as noise, but it keeps (or opens) the table context.
                        if (tableRows == null) {
                            flush()
                            tableRows = ArrayList()
                        }
                    } else {
                        if (tableRows == null) {
                            flush()
                            tableRows = ArrayList()
                        }
                        tableRows?.add(cells)
                    }
                }
                headingMatch != null -> {
                    flush()
                    blocks.add(Block.Heading(headingMatch.groupValues[1].length, spans(line.substring(headingMatch.value.length))))
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flush()
                    blocks.add(Block.Bullet(spans(line.substring(2))))
                }
                numberedMatch != null && numberedValue != null -> {
                    flush()
                    blocks.add(Block.Numbered(numberedValue, spans(line.substring(numberedMatch.value.length))))
                }
                else -> {
                    if (paraLines == null) {
                        flush()
                        paraLines = ArrayList()
                    }
                    paraLines?.add(line)
                }
            }
        }
        flush()
        return blocks
    }

    /**
     * Inline star emphasis, scanned left to right: an opening run of stars takes emphasis length
     * L = min(run length, 3) and pairs with the next run whose own min(length, 3) equals L; the
     * enclosed text becomes one emphasised [Span.Text] (bold when L >= 2, italic when L is 1 or 3).
     * A run with no partner stays literal text ("Vizier*" footnote markers). No nesting.
     */
    fun spans(line: String): List<Span> {
        val out = ArrayList<Span>()
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                out.add(Span.Text(plain.toString()))
                plain.clear()
            }
        }
        var i = 0
        while (i < line.length) {
            if (line[i] != '*') {
                plain.append(line[i])
                i++
                continue
            }
            val runEnd = starRunEnd(line, i)
            val level = minOf(runEnd - i, 3)
            val close = nextRunOfLevel(line, runEnd, level)
            if (close == null) {
                plain.append(line, i, runEnd)
                i = runEnd
            } else {
                flushPlain()
                val inner = line.substring(runEnd, close.first)
                if (inner.isNotEmpty()) {
                    out.add(Span.Text(inner, bold = level >= 2, italic = level == 1 || level == 3))
                }
                i = close.second
            }
        }
        flushPlain()
        return out
    }

    /** Concatenated span text: paired emphasis markers removed, unpaired stars kept literal. */
    fun plainText(line: String): String = buildString {
        for (span in spans(line)) if (span is Span.Text) append(span.text)
    }

    private fun starRunEnd(line: String, start: Int): Int {
        var end = start
        while (end < line.length && line[end] == '*') end++
        return end
    }

    /** The next star run at or after [from] whose min(length, 3) is [level], as (start, end). */
    private fun nextRunOfLevel(line: String, from: Int, level: Int): Pair<Int, Int>? {
        var i = from
        while (i < line.length) {
            if (line[i] == '*') {
                val end = starRunEnd(line, i)
                if (minOf(end - i, 3) == level) return i to end
                i = end
            } else {
                i++
            }
        }
        return null
    }

    private fun nextNonBlankIsPipeRow(lines: List<String>, from: Int): Boolean {
        for (i in from until lines.size) {
            if (lines[i].isBlank()) continue
            return lines[i].trim().startsWith("|")
        }
        return false
    }

    /**
     * Cells of one pipe row: drop the leading `|`, drop the trailing `|` if present (the
     * cube-of-force rows have none), split on `|`, trim each cell.
     */
    fun pipeCells(line: String): List<String> {
        var t = line.trim().removePrefix("|")
        if (t.endsWith("|")) t = t.dropLast(1)
        return t.split('|').map(String::trim)
    }
}
