package dev.tyler.grimoire.ui.sheet

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.data.Summaries
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Concentration
import dev.tyler.grimoire.rules.Derive
import dev.tyler.grimoire.rules.Derived
import dev.tyler.grimoire.rules.Model
import dev.tyler.grimoire.ui.common.Layout
import dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS
import dev.tyler.grimoire.ui.common.SHEET_HEADER_HEIGHT_GRID_UNITS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S1's pure layer, over all three fixture characters — the cleric of the wireframe, the paladin/warlock who
 * is at 0 HP with two slot pools, and the rogue who casts nothing (docs/UI-SPEC.md S1).
 *
 * Every string asserted here is one the screen draws verbatim, and every number behind it is the engine's:
 * the AC comes from `Derive.derive` against the bundled armor table, not from a figure typed into this file.
 */
class SheetTextTest {
    private val armor = Fixtures.armorTable()

    private fun character(name: String): Character = Model.decode(Fixtures.character(name))

    private fun derived(character: Character): Derived = Derive.derive(character, armor)

    private fun rows(name: String): List<SheetRow> = character(name).let { sheetRows(it, derived(it)) }

    private fun sheetRows(character: Character, derived: Derived) = SheetText.sheetRows(character, derived)

    private fun navRow(rows: List<SheetRow>, destination: SheetDestination): SheetRow.Nav =
        rows.filterIsInstance<SheetRow.Nav>().first { it.destination == destination }

    // ---- the header ------------------------------------------------------------------------------------------

    @Test
    fun theWireframesOwnHeaderIsWhatTheClericDraws() {
        val aldric = character("cleric-5-life")
        assertEquals("BROTHER ALDRIC", SheetText.title(aldric), "the top bar's centre, uppercased whole")
        assertEquals("Cleric 5 · Hill Dwarf", SheetText.identity(aldric), "the identity line")
        assertEquals(
            "AC 18  INIT +0  SPD 25  PB +3",
            SheetText.statLine(derived(aldric), aldric.speed),
            "the stat line, character for character from the S1 frame",
        )
    }

    @Test
    fun theStatLineTakesSpeedFromTheCharacterAndEverythingElseFromTheEngine() {
        // Vessa is the one fixture with a non-zero initiative and a proficiency bonus of 2, so a stat line
        // that hard-coded either would pass on the cleric and fail here.
        val vessa = character("rogue-3-thief")
        assertEquals("AC 14  INIT +3  SPD 25  PB +2", SheetText.statLine(derived(vessa), vessa.speed), "Vessa's stats")
        // Speed is not a Derived field at all — the paladin's 30 has to come off the character.
        val maelis = character("paladin-6-warlock-2")
        assertEquals("AC 19  INIT +0  SPD 30  PB +3", SheetText.statLine(derived(maelis), maelis.speed), "Ser Maelis")
    }

    @Test
    fun aModifierOfZeroIsStillSigned() {
        // `Signs.mod` writes +0, never 0: an unsigned entry in a column of signed ones reads as a missing value.
        val aldric = character("cleric-5-life")
        assertTrue(SheetText.statLine(derived(aldric), aldric.speed).contains("INIT +0"), "+0, not 0")
    }

    @Test
    fun theIdentityLineIsTheSameStringHomeStores() {
        // One formatter, so a character cannot be named one way on S0 and another on its own sheet.
        for (name in listOf("cleric-5-life", "paladin-6-warlock-2", "rogue-3-thief")) {
            val c = character(name)
            assertEquals(Summaries.summaryOf(c), SheetText.identity(c), "$name's identity line is Summaries.summaryOf")
        }
        assertEquals("Paladin 6 / Warlock 2 · Half-Elf", SheetText.identity(character("paladin-6-warlock-2")), "multiclass")
        assertEquals("Rogue 3 · Lightfoot Halfling", SheetText.identity(character("rogue-3-thief")), "the race as transcribed")
    }

    @Test
    fun theWholeNameGoesToTheBarEvenWhenTheSdkWillEllipsizeIt() {
        // 22 characters against the bar's ~21: the SDK's own ellipsis decides, not a heuristic here.
        assertEquals("SER MAELIS OF THE PACT", SheetText.title(character("paladin-6-warlock-2")), "untruncated")
    }

    // ---- the HP row ------------------------------------------------------------------------------------------

    @Test
    fun theHpRowDrawsTheNumbersAndTheTempColumn() {
        val row = SheetText.hpRow(character("cleric-5-life"))
        assertEquals("31 / 43", row.numbers, "43 max less 12 damage")
        assertEquals("TEMP 0", row.suffix, "the temp column is drawn at zero too, as the frame does")
        assertFalse(row.bloodied, "31 of 43 is above half")
        assertEquals(SheetDestination.HP, row.destination, "and it is the HP row")
    }

    @Test
    fun bloodiedIsWhatTurnsTheNumbersBold() {
        val aldric = character("cleric-5-life")
        val hurt = aldric.copy(hp = aldric.hp.copy(damage = 22))
        val row = SheetText.hpRow(hurt)
        assertEquals("21 / 43", row.numbers, "21 is at or below half of 43")
        assertTrue(row.bloodied, "so the numbers go bold")
    }

    @Test
    fun aCharacterAtZeroIsNotDrawnBold() {
        // `Derive.hpState` requires current > 0 for bloodied: down is a state S3 says in words, and bolding it
        // here would spend the tool's one weight cue on the state hardest to miss.
        val row = SheetText.hpRow(character("paladin-6-warlock-2"))
        assertEquals("0 / 68", row.numbers, "68 damage on a 68 maximum")
        assertFalse(row.bloodied, "at zero, not bloodied")
    }

    @Test
    fun temporaryHitPointsShowInTheSuffix() {
        assertEquals("TEMP 5", SheetText.hpRow(character("rogue-3-thief")).suffix, "Vessa is carrying 5 temp")
    }

    // ---- the slot row ----------------------------------------------------------------------------------------

    @Test
    fun theClericsSlotRowIsThreeBandsOfWhatIsLeft() {
        val slots = rows("cleric-5-life").filterIsInstance<SheetRow.Slots>().single()
        assertEquals(
            listOf("1st 2/4", "2nd 2/3", "3rd 2/2"),
            slots.strip.groups.map { "${it.label} ${it.available}/${it.total}" },
            "filled = still castable",
        )
        assertFalse(slots.strip.more, "a cleric 5 has nothing deeper than 3rd")
    }

    @Test
    fun theSpentPaladinWarlockKeepsBothPools() {
        val slots = rows("paladin-6-warlock-2").filterIsInstance<SheetRow.Slots>().single()
        assertEquals(
            listOf("1st 0/4", "2nd 1/2", "Pact 1st 0/2"),
            slots.strip.groups.map { "${it.label} ${it.available}/${it.total}" },
            "the pact band is drawn last and budgeted first",
        )
        assertEquals(8, slots.strip.pips, "eight pips wide whatever the fill")
    }

    @Test
    fun aNonCasterHasNoSlotRowAtAll() {
        val rows = rows("rogue-3-thief")
        assertTrue(rows.filterIsInstance<SheetRow.Slots>().isEmpty(), "an empty strip is a read-out of nothing")
        assertEquals(8, rows.size, "so Vessa's hub is eight rows")
        // The SPELLS row stays: the spec drops the strip and nothing else.
        assertEquals(SheetDestination.SPELLS, navRow(rows, SheetDestination.SPELLS).destination, "SPELLS survives")
    }

    @Test
    fun theSlotRowsPipBudgetCanNeverBind() {
        // Levels 1-3 hold at most 4 + 3 + 3 and Pact Magic at most 4: 14 pips is the rules' ceiling, and the
        // row's width affords 21 — the 21 usable columns of a scrolling lazy list, not the 25 an unscrolled
        // row gets. `more` is therefore driven by depth alone — a 4th-level slot, which is on S5.
        val ceiling = 4 + 3 + 3 + 4
        for (name in listOf("cleric-5-life", "paladin-6-warlock-2")) {
            val slots = rows(name).filterIsInstance<SheetRow.Slots>().single()
            assertTrue(slots.strip.pips <= ceiling, "$name draws ${slots.strip.pips} pips, inside the ceiling of $ceiling")
            assertFalse(slots.strip.more, "$name's strip is the whole story")
        }
    }

    // ---- the conditions row ----------------------------------------------------------------------------------

    @Test
    fun theConditionsRowNamesTheConcentrationSpell() {
        val row = navRow(rows("cleric-5-life"), SheetDestination.CONDITIONS)
        assertEquals("Bless (C)", row.detail, "the wireframe's own line — Aldric has no conditions, only a spell")
    }

    @Test
    fun aConditionIsDrawnUnderItsOwnName() {
        assertEquals("Prone", navRow(rows("paladin-6-warlock-2"), SheetDestination.CONDITIONS).detail, "Ser Maelis is prone")
        assertEquals("Poisoned", navRow(rows("rogue-3-thief"), SheetDestination.CONDITIONS).detail, "Vessa is poisoned")
    }

    @Test
    fun exhaustionIsNotOneOfTheConditions() {
        // S7 draws exhaustion as a stepper, not one of its 14 toggles; S1's sentence is "active conditions and
        // the concentration spell". Vessa is at exhaustion 1 and her row says only what she is.
        val vessa = character("rogue-3-thief")
        assertEquals(1, vessa.exhaustion, "the fixture really is exhausted")
        assertEquals("Poisoned", SheetText.conditionsDetail(vessa), "and the row does not say so")
    }

    @Test
    fun conditionsAreSortedSoTheLineDoesNotReshuffleAsTheyAreToggled() {
        val vessa = character("rogue-3-thief")
        val many = vessa.copy(
            conditions = listOf("restrained", "blinded", "prone"),
            concentration = Concentration(spellKey = "bless", name = "Bless"),
        )
        assertEquals("Blinded · Prone · Restrained · Bless (C)", SheetText.conditionsDetail(many), "alphabetical, spell last")
    }

    @Test
    fun aClearCharacterSaysNothingOnThatRow() {
        val vessa = character("rogue-3-thief")
        assertNull(
            SheetText.conditionsDetail(vessa.copy(conditions = emptyList(), concentration = null)),
            "no detail rather than an empty string",
        )
        val row = navRow(sheetRows(vessa.copy(conditions = emptyList(), concentration = null), derived(vessa)), SheetDestination.CONDITIONS)
        assertNull(row.detail, "and the row carries that null")
    }

    // ---- the nine rows and their fit -------------------------------------------------------------------------

    @Test
    fun theRowsComeInTheSpecsReorderedOrder() {
        assertEquals(
            listOf(
                SheetDestination.HP,
                SheetDestination.SLOTS,
                SheetDestination.TURN,
                SheetDestination.CHECKS,
                SheetDestination.SPELLS,
                SheetDestination.CONDITIONS,
                SheetDestination.REST,
                SheetDestination.FEATURES,
                SheetDestination.GEAR,
            ),
            rows("cleric-5-life").map { it.destination },
            "the S1 frame, top to bottom",
        )
    }

    @Test
    fun everyOneOfTheNineRowsHasSomethingOfItsOwn() {
        // The reason all nine are built before seven of them navigate: a row with no content is a row whose
        // fit nobody has checked.
        for (row in rows("cleric-5-life")) {
            when (row) {
                is SheetRow.Hp -> assertTrue(row.numbers.isNotEmpty() && row.suffix.isNotEmpty(), "the HP row's text")
                is SheetRow.Slots -> assertTrue(row.strip.pips > 0, "the slot row's pips")
                is SheetRow.Nav -> assertTrue(row.label.isNotEmpty(), "${row.destination}'s label")
            }
            assertTrue(row.label.isNotEmpty(), "${row.destination} has a word of its own")
        }
    }

    @Test
    fun everyOneTapContractRowIsAboveTheFold() {
        // 23 content units less the 4-unit pinned header is 19, and 19 / 2.5 = 7.6 rows visible. Rows 1-7 are
        // HP through REST — every action the one-tap contract names — and only FEATURES and GEAR need a turn.
        val visible = Layout.visibleRows(ROW_HEIGHT_GRID_UNITS, SHEET_HEADER_HEIGHT_GRID_UNITS)
        assertTrue(visible >= 7f, "seven whole rows are visible on open, not six: $visible")
        assertTrue(visible < 8f, "and the eighth is the part-row that says the list scrolls: $visible")
        val order = rows("cleric-5-life").map { it.destination }
        assertEquals(
            listOf(
                SheetDestination.HP,
                SheetDestination.SLOTS,
                SheetDestination.TURN,
                SheetDestination.CHECKS,
                SheetDestination.SPELLS,
                SheetDestination.CONDITIONS,
                SheetDestination.REST,
            ),
            order.take(visible.toInt()),
            "HP change, cast, attack, condition toggle and rest start are all one tap",
        )
        assertEquals(
            listOf(SheetDestination.FEATURES, SheetDestination.GEAR),
            order.drop(visible.toInt()),
            "and only these two are a wheel turn away",
        )
    }

    @Test
    fun aNonCasterKeepsEveryOneTapRowAboveTheFoldToo() {
        // The fold is a claim about every character, and the rogue is the one whose list changes length: with
        // no slot row her eight rows pull up by one, so `FEATURES & RESOURCES` joins the visible seven and
        // `GEAR & COIN` is alone below. Dropping a row can only ever pull rows up, and this is where that is
        // checked rather than assumed.
        val visible = Layout.visibleRows(ROW_HEIGHT_GRID_UNITS, SHEET_HEADER_HEIGHT_GRID_UNITS).toInt()
        val above = rows("rogue-3-thief").map { it.destination }.take(visible)
        val oneTap = listOf(
            SheetDestination.HP,
            SheetDestination.TURN,
            SheetDestination.SPELLS,
            SheetDestination.CONDITIONS,
            SheetDestination.REST,
        )
        for (destination in oneTap) {
            assertTrue(destination in above, "$destination is still one tap for a non-caster: $above")
        }
        assertEquals(
            listOf(SheetDestination.GEAR),
            rows("rogue-3-thief").map { it.destination }.drop(visible),
            "and only inventory is below the fold",
        )
    }

    @Test
    fun theSlotRowIsTheOnlyRowThatCanBeAbsent() {
        val everyone = listOf("cleric-5-life", "paladin-6-warlock-2", "rogue-3-thief").map { rows(it) }
        for (rows in everyone) {
            assertNotNull(rows.filterIsInstance<SheetRow.Hp>().singleOrNull(), "every sheet has one HP row")
            for (destination in SheetDestination.entries.filter { it != SheetDestination.SLOTS }) {
                assertTrue(rows.any { it.destination == destination }, "$destination is always drawn")
            }
        }
    }
}
