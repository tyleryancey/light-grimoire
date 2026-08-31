package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The `json` column is the record's raw slice of the asset, never re-encoded (plan D3). The splitter is a
 * pure scanner over depth, quotes and escapes; the sweep proves it is lossless over every bundled file.
 */
class JsonArraySplitTest {
    @Test
    fun splitsScalarsObjectsAndArraysAtTheTopLevelOnly() {
        val text = """[ 1, "two", {"a": [1, 2, {"b": "}"}]}, [ [ ] ], null, true, -3.5e2 ]"""
        assertEquals(
            listOf("1", "\"two\"", """{"a": [1, 2, {"b": "}"}]}""", "[ [ ] ]", "null", "true", "-3.5e2"),
            JsonArraySplit.elements(text),
            "top-level elements",
        )
    }

    @Test
    fun bracketsAndCommasInsideStringsDoNotSplit() {
        val text = """["a,b", "c]d", "{e", "f}", "[g"]"""
        assertEquals(listOf("\"a,b\"", "\"c]d\"", "\"{e\"", "\"f}\"", "\"[g\""), JsonArraySplit.elements(text), "delimiters inside strings")
    }

    @Test
    fun escapedQuotesAndBackslashesInsideStringsAreScannedAsStringContent() {
        val text = "[\"say \\\"hi\\\", ok\", \"back\\\\\", \"\\\\\\\"]\", {\"k\": \"v\\\"\"}]"
        assertEquals(
            listOf("\"say \\\"hi\\\", ok\"", "\"back\\\\\"", "\"\\\\\\\"]\"", "{\"k\": \"v\\\"\"}"),
            JsonArraySplit.elements(text),
            "escapes",
        )
    }

    @Test
    fun whitespaceAroundElementsIsNotPartOfTheSlice() {
        val text = "\n[\n {\n  \"a\": 1\n }\t,\r\n\t\"b\"  ,\n 3\n]\n"
        assertEquals(listOf("{\n  \"a\": 1\n }", "\"b\"", "3"), JsonArraySplit.elements(text), "trimmed slices")
    }

    @Test
    fun emptyArrayHasNoElements() {
        for (text in listOf("[]", " [ ] ", "\n[\n]\n")) {
            assertEquals(emptyList(), JsonArraySplit.elements(text), "empty array ${text.replace("\n", "\\n")}")
        }
    }

    @Test
    fun rejectsTextThatIsNotASingleWellFormedArray() {
        for (text in listOf("", "   ", "{}", "1", "[1", "[1,", "[1,]", "[,1]", "[1] x", "[1]]", "[\"open", "[{\"a\": 1]", "[1}")) {
            assertFailsWith<IllegalArgumentException>("rejects ${text.ifEmpty { "<empty>" }}") { JsonArraySplit.elements(text) }
        }
    }

    @Test
    fun everyBundledFileSplitsIntoItsIndexedCountAndReassemblesLosslessly() {
        val index = Fixtures.compendiumIndex()
        for (kind in Kind.entries) {
            val meta = index.files.getValue(kind.file)
            val text = Fixtures.compendium(kind.file)
            val slices = JsonArraySplit.elements(text)
            assertEquals(meta.count, slices.size, "${kind.file} slice count")
            for ((i, slice) in slices.withIndex()) {
                assertTrue(slice.startsWith("{") && slice.endsWith("}"), "${kind.file}[$i] is a bare object without surrounding whitespace")
            }
            assertEquals(
                Json.parseToJsonElement(text),
                Json.parseToJsonElement(slices.joinToString(",", "[", "]")),
                "${kind.file} reassembled from slices",
            )
        }
    }
}
