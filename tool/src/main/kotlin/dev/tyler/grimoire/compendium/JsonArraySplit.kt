package dev.tyler.grimoire.compendium

/**
 * Splits the text of a JSON array into the raw text of its top-level elements, each exactly as it appears in
 * the source (no whitespace either side, never re-encoded). The `json` column of every record is one of
 * these slices, so a read-back is byte-identical to the asset (plan D3) and the M0 spike's
 * `JsonElement.toString()` pass disappears.
 *
 * Pure scanner: tracks nesting depth outside strings, string state and backslash escapes inside them. It
 * validates only the array structure it walks (one array, balanced brackets, no dangling commas, nothing
 * after the closing bracket); element contents are the decoder's job. Throws [IllegalArgumentException] on
 * malformed input.
 */
object JsonArraySplit {
    fun elements(text: String): List<String> {
        var i = skipWhitespace(text, 0)
        require(i < text.length && text[i] == '[') { "expected a JSON array at offset $i" }
        i = skipWhitespace(text, i + 1)
        val out = ArrayList<String>()
        if (i < text.length && text[i] == ']') {
            requireOnlyWhitespace(text, i + 1)
            return out
        }
        while (true) {
            require(i < text.length) { "unterminated JSON array" }
            val start = i
            var depth = 0
            var inString = false
            var end = -1
            while (i < text.length) {
                val c = text[i]
                if (inString) {
                    when (c) {
                        '\\' -> i++
                        '"' -> inString = false
                    }
                } else {
                    when (c) {
                        '"' -> inString = true
                        '{', '[' -> depth++
                        '}', ']' -> {
                            if (depth == 0) {
                                require(c == ']') { "unbalanced '}' at offset $i" }
                                end = i
                                break
                            }
                            depth--
                        }
                        ',' -> if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
                i++
            }
            require(end >= 0) { "unterminated JSON array" }
            val slice = trimTrailingWhitespace(text, start, end)
            require(slice.isNotEmpty()) { "empty array element at offset $start" }
            out += slice
            if (text[end] == ']') {
                requireOnlyWhitespace(text, end + 1)
                return out
            }
            i = skipWhitespace(text, end + 1)
        }
    }

    private fun isWhitespace(c: Char): Boolean = c == ' ' || c == '\n' || c == '\r' || c == '\t'

    private fun skipWhitespace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && isWhitespace(text[i])) i++
        return i
    }

    private fun requireOnlyWhitespace(text: String, from: Int) {
        val i = skipWhitespace(text, from)
        require(i == text.length) { "unexpected content after the array at offset $i" }
    }

    private fun trimTrailingWhitespace(text: String, start: Int, end: Int): String {
        var stop = end
        while (stop > start && isWhitespace(text[stop - 1])) stop--
        return text.substring(start, stop)
    }
}
