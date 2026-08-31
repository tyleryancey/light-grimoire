package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.compendium.FakeCompendiumDao
import dev.tyler.grimoire.compendium.KindGroup
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S13's nine hub rows over the real bundle (docs/UI-SPEC.md S13). Every count below is the sha256-pinned
 * assets counted through `countsByKind()`, never a literal in the tool (D13): 319 spells, 15 conditions, 237
 * equipment, 362 magic items, 334 creatures — and the four heterogeneous rows carry no count at all.
 *
 * The tests drive the internal `load()` directly — `onScreenShow` needs a real activity and a Main dispatcher
 * that a JVM test has neither of — which is also where the idempotence guard lives.
 */
class CompendiumHubViewModelTest {
    private fun vm(dao: FakeCompendiumDao = Bundle.dao()) = CompendiumHubViewModel(Bundle.reader(dao))

    private fun loadedRows(): List<HubRow> = runBlocking {
        vm().let {
            it.load()
            it.state.value.rows
        }
    }

    // ---- the wireframe ---------------------------------------------------------------------------------------

    @Test
    fun theHubIsTheNineGroupsInTheWireframesOrder() = runBlocking {
        val model = vm()
        assertTrue(model.state.value.loading, "the hub starts loading")
        assertEquals(emptyList(), model.state.value.rows, "with no rows to draw yet")
        model.load()
        assertFalse(model.state.value.loading, "the wait ends with the rows")
        assertEquals(
            listOf(
                "SPELLS",
                "CONDITIONS",
                "RULES",
                "CLASSES & FEATURES",
                "RACES",
                "BACKGROUNDS & FEATS",
                "EQUIPMENT",
                "MAGIC ITEMS",
                "CREATURES",
            ),
            model.state.value.rows.map { it.label },
            "the S13 wireframe, row for row",
        )
        assertEquals(
            KindGroup.entries.filter { it != KindGroup.LOOKUP },
            model.state.value.rows.map { it.group },
            "each row pushes its own group, in KindGroup order",
        )
    }

    @Test
    fun theCountedRowsCarryTheBundlesOwnNumbers() {
        assertEquals(
            listOf(
                "SPELLS" to 319,
                "CONDITIONS" to 15,
                "RULES" to null,
                "CLASSES & FEATURES" to null,
                "RACES" to null,
                "BACKGROUNDS & FEATS" to null,
                "EQUIPMENT" to 237,
                "MAGIC ITEMS" to 362,
                "CREATURES" to 334,
            ),
            loadedRows().map { it.label to it.count },
            "five rows count one kind each; the four that mix kinds show nothing (S13)",
        )
    }

    @Test
    fun anEquipmentRowCountsItsOwnKindAndNotTheGroup() {
        val rows = loadedRows()
        val equipment = rows.single { it.group == KindGroup.EQUIPMENT }
        // The one row where "the group's count" and "the kind's count" differ: S13.1 draws the 11 weapon
        // properties under their own section in the same group, and calls the gap "not a discrepancy".
        assertEquals(237, equipment.count, "the equipment records alone, not the 248 rows S13.1 lists")
    }

    @Test
    fun theCountIsDrawnInsideTheNameNotTheDetailColumn() {
        val rows = loadedRows()
        assertEquals("SPELLS (319)", rows.first().rowText, "the wireframe writes the count in the row's name")
        assertEquals(
            "CLASSES & FEATURES",
            rows.single { it.group == KindGroup.CLASSES_AND_FEATURES }.rowText,
            "and an uncounted row is its bare label, with no empty brackets",
        )
    }

    @Test
    fun spellsGetExactlyOneRowAndTheLookupKindsNone() {
        val rows = loadedRows()
        assertEquals(9, rows.size, "nine rows")
        assertEquals(1, rows.count { it.group == KindGroup.SPELLS }, "SPELLS is one row, which pushes S13.2")
        assertTrue(
            rows.none { it.group == KindGroup.LOOKUP },
            "the six lookup kinds are reached only through FIND and reader cross-links (S13)",
        )
        assertEquals(rows.size, rows.map { it.group }.toSet().size, "and no group is offered twice")
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    @Test
    fun aSecondShowDoesNotRebuildTheHub() = runBlocking {
        val dao = Bundle.dao()
        val model = vm(dao)
        model.load()
        val first = model.state.value
        assertEquals(listOf("countsByKind"), dao.calls, "one grouped COUNT answers all five counted rows")
        model.load()
        assertEquals(listOf("countsByKind"), dao.calls, "a relaunch's second show runs no query")
        assertEquals(first, model.state.value, "and cannot rebuild the hub under the reader's finger")
    }

    // ---- keys ------------------------------------------------------------------------------------------------

    @Test
    fun wheelTurnsEmitSignedTicksAndThePressIsConsumedAsANoOp() = runBlocking {
        val model = vm()
        model.load()
        val before = model.state.value
        val seen = ArrayList<Int>()
        val collector = launch { model.ticks.collect { seen += it } }
        yield()
        assertTrue(model.handleKey(317), "a turn toward the top of the phone is consumed")
        assertTrue(model.handleKey(318), "a turn toward the bottom is consumed")
        assertTrue(model.handleKey(319), "the press is consumed so LightOS never relaunches the tool")
        yield()
        assertEquals(listOf(-1, 1), seen, "317 scrolls back, 318 scrolls on, the press emits nothing")
        assertEquals(before, model.state.value, "no wheel event changes the hub")
        collector.cancel()
    }

    @Test
    fun nonWheelKeysAreLeftToLightOs() {
        val model = vm()
        assertFalse(model.handleKey(24), "volume up stays unconsumed")
        assertFalse(model.handleKey(25), "volume down stays unconsumed")
        assertFalse(model.handleKey(80), "camera focus stays unconsumed")
        assertFalse(model.handleKey(4), "back stays unconsumed")
    }

    @Test
    fun theReleaseHalfOfEveryDetentIsSwallowedWithoutScrolling() = runBlocking {
        val model = vm()
        model.load()
        val seen = ArrayList<Int>()
        val collector = launch { model.ticks.collect { seen += it } }
        yield()
        // One detent is a DOWN/UP pair, and `LightKeyHandler` defaults the UP half to false, so a screen that
        // consumed only the DOWN half would still be relaunched by LightOS on every turn.
        assertTrue(model.consumesKey(317), "the release of a turn toward the top is consumed")
        assertTrue(model.consumesKey(318), "and of a turn toward the bottom")
        assertTrue(model.consumesKey(319), "and of the press")
        assertFalse(model.consumesKey(24), "volume up is still LightOS's")
        assertFalse(model.consumesKey(25), "so is volume down")
        assertFalse(model.consumesKey(80), "so is camera focus")
        assertFalse(model.consumesKey(4), "and so is back")
        yield()
        assertEquals(emptyList<Int>(), seen, "and not one of them scrolled the hub")
        collector.cancel()
    }

    @Test
    fun consumingAndActingAgreeOnEveryKey() {
        val model = vm()
        for (keyCode in listOf(317, 318, 319, 24, 25, 80, 4)) {
            assertEquals(model.handleKey(keyCode), model.consumesKey(keyCode), "key $keyCode is judged once")
        }
    }
}
