package dev.tyler.grimoire.data

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.ClassEntry
import dev.tyler.grimoire.rules.Model
import dev.tyler.grimoire.rules.Race
import dev.tyler.grimoire.rules.Tables
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The denormalised S0 line (docs/UI-SPEC.md S0, S1). The three fixture characters are the real cases; the
 * rest pin the decisions [Summaries] makes that a later edit could quietly reverse — the stored class
 * order, the separator, an absent race, and the guard on length.
 */
class SummariesTest {
    private fun character(ref: String) = Model.decode(Fixtures.character(ref))

    @Test
    fun theThreeFixtureCharactersReadAsTheirSheetsDo() {
        assertEquals("Cleric 5 · Hill Dwarf", Summaries.summaryOf(character("cleric-5-life")), "Brother Aldric")
        assertEquals("Rogue 3 · Lightfoot Halfling", Summaries.summaryOf(character("rogue-3-thief")), "Vessa Quickfinger")
        assertEquals(
            "Paladin 6 / Warlock 2 · Half-Elf",
            Summaries.summaryOf(character("paladin-6-warlock-2")),
            "Ser Maelis of the Pact",
        )
    }

    /** The subrace is shown as transcribed — "Lightfoot Halfling", never shortened to the race key. */
    @Test
    fun theRaceIsTheNameOnTheSheetNotTheCompendiumKey() {
        val vessa = character("rogue-3-thief")
        assertEquals("halfling", vessa.race?.key, "the key is the short one")
        assertTrue(Summaries.summaryOf(vessa).endsWith("· Lightfoot Halfling"), "the summary shows the transcribed name")
    }

    /** Stored order, not sorted by level or name: a multiclass reads the way the player entered it. */
    @Test
    fun multiclassKeepsTheStoredOrder() {
        val maelis = character("paladin-6-warlock-2")
        assertEquals(
            "Warlock 2 / Paladin 6 · Half-Elf",
            Summaries.summaryOf(maelis.copy(classes = maelis.classes.reversed())),
            "reversing the classes reverses the summary",
        )
    }

    @Test
    fun aMissingOrBlankRaceLeavesNoTrailingSeparator() {
        val aldric = character("cleric-5-life")
        assertEquals("Cleric 5", Summaries.summaryOf(aldric.copy(race = null)), "no race at all")
        assertEquals("Cleric 5", Summaries.summaryOf(aldric.copy(race = Race())), "Race.name defaults to empty")
        assertEquals("Cleric 5", Summaries.summaryOf(aldric.copy(race = Race(name = "   "))), "whitespace is not a race")
        assertEquals(
            "Cleric 5 · Hill Dwarf",
            Summaries.summaryOf(aldric.copy(race = Race(name = "  Hill Dwarf  "))),
            "surrounding whitespace is a transcription artefact",
        )
    }

    /**
     * The claim [Summaries.title] rests on: every SRD class key title-cases to the display name the
     * bundle itself uses, so the tool needs no lookup map that could drift from the compendium.
     */
    @Test
    fun everySrdClassKeyTitleCasesToTheBundledDisplayName() {
        val names = Json.parseToJsonElement(Fixtures.compendium("classes.json")).jsonArray
            .map { it.jsonObject }
            .associate { it.getValue("key").jsonPrimitive.content to it.getValue("name").jsonPrimitive.content }
        assertEquals(names.keys, Tables.HIT_DIE.keys, "the engine's class keys are the bundle's class keys")
        for (key in Tables.HIT_DIE.keys) {
            assertEquals(names.getValue(key), Summaries.title(key), "'$key' title-cases to its bundled name")
        }
    }

    @Test
    fun aCustomClassSlugRendersSensibly() {
        val aldric = character("cleric-5-life")
        assertEquals("Blood Hunter", Summaries.title("blood-hunter"), "a hyphenated homebrew slug")
        assertEquals(
            "Blood Hunter 4 · Hill Dwarf",
            Summaries.summaryOf(aldric.copy(classes = listOf(ClassEntry(classKey = "blood-hunter", level = 4)))),
            "a non-SRD class needs no table entry to be named",
        )
    }

    @Test
    fun aVeryLongRaceNameIsCutToTheGuard() {
        val aldric = character("cleric-5-life")
        val summary = Summaries.summaryOf(aldric.copy(race = Race(name = "Wandering ".repeat(12) + "Dwarf")))
        assertEquals(Summaries.MAX_LENGTH, summary.length, "cut to the guard")
        assertTrue(summary.startsWith("Cleric 5 · Wandering "), "the front of the line survives")
    }

    /** A cut that lands inside " · " would leave a dangling separator; the class part is a whole line. */
    @Test
    fun aCutInsideTheSeparatorDoesNotLeaveOneDangling() {
        val aldric = character("cleric-5-life")
        val longSlug = "x".repeat(76)
        val classPart = Summaries.title(longSlug) + " 1"
        assertEquals(78, classPart.length, "the class part lands two characters short of the guard")
        assertEquals(
            classPart,
            Summaries.summaryOf(aldric.copy(classes = listOf(ClassEntry(classKey = longSlug, level = 1)), race = Race(name = "Elf"))),
            "no trailing separator survives the cut",
        )
    }
}
