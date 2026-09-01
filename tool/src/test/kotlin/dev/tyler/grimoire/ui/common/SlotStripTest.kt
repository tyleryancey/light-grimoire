package dev.tyler.grimoire.ui.common

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.ClassEntry
import dev.tyler.grimoire.rules.Derive
import dev.tyler.grimoire.rules.DerivedSpellcasting
import dev.tyler.grimoire.rules.Model
import dev.tyler.grimoire.rules.Spellcasting
import dev.tyler.grimoire.rules.Tables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `slotStrip` — S1's slot row and S5's slot line, as a pure function of what the engine derives and
 * what the character has spent. The three fixture characters are used wherever the input is a
 * character, so the strips pinned here are the ones the screens will actually draw.
 */
class SlotStripTest {
    private val armor = Fixtures.armorTable()

    private fun character(name: String): Character = Model.decode(Fixtures.character(name))

    private fun derived(name: String): DerivedSpellcasting =
        Derive.derive(character(name), armor).spellcasting

    private fun strip(name: String, maxPips: Int = 12): SlotStripModel =
        slotStrip(derived(name), character(name).spellcasting, maxPips)

    /** A band as `label available/total`, so a flipped polarity shows up in the failure message. */
    private fun show(model: SlotStripModel) =
        model.groups.joinToString(" ") { "${it.label} ${it.available}/${it.total}" }

    // ---- polarity: filled = available = max − used -------------------------------------------------------------

    @Test
    fun theClericsBandsShowWhatIsLeftNotWhatWasSpent() {
        // slotsMax [4, 3, 2] against slotsUsed [2, 1, 0]: available 2, 2, 2.
        val model = strip("cleric-5-life")
        assertEquals("1st 2/4 2nd 2/3 3rd 2/2", show(model), "the cleric's slot strip")
        // The first band is 2 either way (4 − 2 = 2 used = 2), which is exactly why the second and
        // third are asserted: a strip that filled `used` would read 1/3 and 0/2 here.
        assertEquals(2, model.groups[1].available, "2nd level: 3 max − 1 used")
        assertEquals(2, model.groups[2].available, "3rd level: 2 max − 0 used, untouched")
        assertFalse(model.more, "a cleric 5 has nothing deeper than 3rd")
    }

    @Test
    fun anAllSpentCharacterDrawsAnEmptyStripNotAFullOne() {
        // paladin 6 / warlock 2: slotsMax [4, 2] against slotsUsed [4, 1], pact 2 of 2 spent.
        val model = strip("paladin-6-warlock-2")
        assertEquals("1st 0/4 2nd 1/2 Pact 1st 0/2", show(model), "Ser Maelis has one slot left in all")
        assertEquals(0, model.groups[0].available, "every 1st-level slot is gone")
        assertEquals(0, model.groups.last().available, "and both pact slots")
        assertEquals(8, model.pips, "the strip is still eight pips wide — only the fill changed")
    }

    @Test
    fun aNonCasterHasNoStripAtAll() {
        val model = strip("rogue-3-thief")
        assertTrue(model.isEmpty, "Vessa casts nothing")
        assertFalse(model.more, "and there is nothing deeper to point at")
        assertEquals(0, model.pips, "no pips")
    }

    // ---- the pact band ---------------------------------------------------------------------------------------

    @Test
    fun aPureWarlocksOnlyBandIsThePactOne() {
        // All-zero slotsMax and a non-null pact: without the pact band this strip would be empty.
        val maxima = Tables.spellSlots(listOf(ClassEntry(classKey = "warlock", level = 3)))
        assertTrue(maxima.slots.all { it == 0 }, "a warlock contributes nothing to the regular table")
        val casting = DerivedSpellcasting(ability = null, saveDc = null, attackBonus = null, slotsMax = maxima.slots, pact = maxima.pact)
        val model = slotStrip(casting, Spellcasting(pactUsed = 1), maxPips = 12)
        assertEquals("Pact 2nd 1/2", show(model), "one of two 2nd-level pact slots left")
        assertFalse(model.isEmpty, "the pact band is what keeps the strip non-empty")
        assertFalse(model.more, "and it is the whole story")
    }

    @Test
    fun thePactBandIsDrawnAfterTheRegularLevels() {
        val labels = strip("paladin-6-warlock-2").groups.map { it.label }
        assertEquals(listOf("1st", "2nd", "Pact 1st"), labels, "regular levels first, pact last")
    }

    @Test
    fun thePactBandKeepsItsPipsWhenTheBudgetIsTight() {
        // Budgeted first even though it is drawn last: 2 pact pips reserved, 4 left for the regular
        // levels, so 1st fits and 2nd is what gets dropped.
        val model = strip("paladin-6-warlock-2", maxPips = 6)
        assertEquals("1st 0/4 Pact 1st 0/2", show(model), "the pact slots survive the squeeze")
        assertTrue(model.more, "and the strip says so")
    }

    // ---- the pip budget --------------------------------------------------------------------------------------

    @Test
    fun theStripStopsAtTheFirstBandThatDoesNotFit() {
        assertEquals("1st 2/4 2nd 2/3", show(strip("cleric-5-life", maxPips = 7)), "3rd will not fit in 7 pips")
        assertEquals("1st 2/4", show(strip("cleric-5-life", maxPips = 4)), "nor 2nd in 4")
        assertEquals("", show(strip("cleric-5-life", maxPips = 3)), "nor 1st in 3")
    }

    @Test
    fun aDroppedBandSetsMoreAndAWholeStripDoesNot() {
        assertTrue(strip("cleric-5-life", maxPips = 7).more, "3rd was dropped")
        assertTrue(strip("cleric-5-life", maxPips = 0).more, "everything was dropped")
        assertFalse(strip("cleric-5-life", maxPips = 9).more, "9 pips hold all three bands exactly")
        assertEquals(9, strip("cleric-5-life", maxPips = 9).pips, "4 + 3 + 2")
    }

    @Test
    fun theStripNeverDrawsMorePipsThanItsBudget() {
        for (budget in 0..12) {
            val model = strip("cleric-5-life", maxPips = budget)
            assertTrue(model.pips <= budget, "$budget pips budgeted, ${model.pips} drawn")
        }
    }

    // ---- slots the strip does not draw -------------------------------------------------------------------------

    @Test
    fun slotsDeeperThanThirdLevelSetMoreWithoutBeingDrawn() {
        val casting = DerivedSpellcasting(
            ability = null,
            saveDc = null,
            attackBonus = null,
            slotsMax = listOf(4, 3, 3, 3, 2, 0, 0, 0, 0),
            pact = null,
        )
        val model = slotStrip(casting, Spellcasting(), maxPips = 40)
        assertEquals(SLOT_STRIP_MAX_LEVEL, model.groups.size, "only levels 1..3 are drawn inline")
        assertTrue(model.more, "the 4th- and 5th-level slots are on S5")
    }

    // ---- disagreement between a derived maximum and a stored spend ----------------------------------------------

    @Test
    fun aSpendPastTheMaximumClampsRatherThanDrawingANegativeBand() {
        val casting = DerivedSpellcasting(null, null, null, listOf(2, 0, 0, 0, 0, 0, 0, 0, 0), null)
        val model = slotStrip(casting, Spellcasting(slotsUsed = listOf(9, 0, 0, 0, 0, 0, 0, 0, 0)), maxPips = 12)
        assertEquals("1st 0/2", show(model), "a stale spend empties the band, it does not invert it")
    }

    @Test
    fun aPactSpendPastTheCountClampsToo() {
        val maxima = Tables.spellSlots(listOf(ClassEntry(classKey = "warlock", level = 1)))
        val casting = DerivedSpellcasting(null, null, null, maxima.slots, maxima.pact)
        val model = slotStrip(casting, Spellcasting(pactUsed = 5), maxPips = 12)
        assertEquals("Pact 1st 0/1", show(model), "one slot, however many the sheet says were spent")
    }

    @Test
    fun aMissingSpellcastingBlockMeansNothingSpent() {
        val casting = DerivedSpellcasting(null, null, null, listOf(3, 0, 0, 0, 0, 0, 0, 0, 0), null)
        assertEquals("1st 3/3", show(slotStrip(casting, null, maxPips = 12)), "all three still there")
    }

    // ---- labels --------------------------------------------------------------------------------------------------

    @Test
    fun levelsAreLabelledTheWayTheWireframesDrawThem() {
        assertEquals("1st", slotLevelLabel(1), "S5's first band")
        assertEquals("2nd", slotLevelLabel(2), "its second")
        assertEquals("3rd", slotLevelLabel(3), "its third")
        assertEquals("9th", slotLevelLabel(9), "the deepest slot the game has")
        assertEquals("10", slotLevelLabel(10), "and nothing past it pretends to an ordinal")
    }
}
