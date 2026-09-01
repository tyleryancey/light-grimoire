package dev.tyler.grimoire.ui.sheet

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.data.FakeCharacterRepository
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Model
import dev.tyler.grimoire.ui.compendium.Bundle
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S1's view model: one loaded character, the star, and the wheel (docs/UI-SPEC.md S1).
 *
 * The character store is [FakeCharacterRepository], which refuses and stamps exactly as the real one does,
 * and the compendium is the whole bundled one through [Bundle] — the same reader `ui/compendium`'s tests
 * build — so the armor table behind every AC below is the shipped `equipment.json` rather than a stub. The
 * tests drive the internal `load()` directly: `onScreenShow` takes a `SimpleLightScreen`, which needs a real
 * activity, and `load()` is where the behaviour that matters (an *ungated* reload) lives.
 */
class SheetViewModelTest {
    private companion object {
        /** LP3 wheel key codes, from `LightDeviceKeys` — 317 toward the top of the phone, 318 down, 319 press. */
        const val WHEEL_UP = 317
        const val WHEEL_DOWN = 318
        const val WHEEL_PRESS = 319

        /** A key the tool must never consume, so LightOS still gets it. */
        const val VOLUME_UP = 24
    }

    private val cleric: Character by lazy { Model.decode(Fixtures.character("cleric-5-life")) }
    private val rogue: Character by lazy { Model.decode(Fixtures.character("rogue-3-thief")) }

    /**
     * One loaded sheet over one stored character, with the real bundle behind the armor table.
     *
     * [name] is what Home's row already holds and hands to `SheetScreen` — the bar's title before anything
     * has loaded — so it is passed here the way the screen passes it rather than left at its default.
     */
    private class Screen(
        character: Character?,
        id: String = character?.id ?: "no-such-character",
        name: String = character?.name ?: "Brother Aldric",
    ) {
        val repo = FakeCharacterRepository().apply {
            if (character != null) {
                stored[character.id] = character
                updatedAt[character.id] = 1L
            }
        }

        val model = SheetViewModel(id, repo, Bundle.reader(), name)

        val state get() = model.state.value

        /** What the screen has asked the repository to store, most recent last. */
        val saved get() = repo.saves

        init {
            runBlocking { model.load() }
        }

        fun reload() = runBlocking { model.load() }
    }

    private fun navRow(state: SheetUiState, destination: SheetDestination): SheetRow.Nav =
        state.rows.filterIsInstance<SheetRow.Nav>().first { it.destination == destination }

    // ---- the load --------------------------------------------------------------------------------------------

    @Test
    fun aFreshViewModelIsLoadingAndDrawsNothing() {
        val model = SheetViewModel(cleric.id, FakeCharacterRepository(), Bundle.reader())
        assertTrue(model.state.value.loading, "the quiet line holds until the character and its Derived land")
        assertFalse(model.state.value.missing, "loading is not missing")
        assertTrue(model.state.value.rows.isEmpty(), "and nothing is drawn before then")
    }

    @Test
    fun loadingDerivesTheWholeHeaderFromTheBundledArmorTable() {
        val s = Screen(cleric)
        assertFalse(s.state.loading, "the wait ends with the sheet")
        assertEquals("BROTHER ALDRIC", s.state.title, "the top bar")
        assertEquals("Cleric 5 · Hill Dwarf", s.state.identity, "the identity line")
        // AC 18 is chain mail (16) plus a shield: it can only come from the compendium's own armor row, so
        // this line is what proves the reader was actually consulted.
        assertEquals("AC 18  INIT +0  SPD 25  PB +3", s.state.stats, "the stat line")
        assertEquals(9, s.state.rows.size, "a caster's nine rows")
    }

    @Test
    fun theRowsAreTheOnesTheHubDraws() {
        val s = Screen(cleric)
        val hp = assertNotNull(s.state.hp, "the HP row")
        assertEquals("31 / 43", hp.numbers, "the numbers")
        assertEquals("TEMP 0", hp.suffix, "and the temp column")
        val slots = assertNotNull(s.state.slots, "the slot row")
        assertEquals(9, slots.strip.pips, "4 + 3 + 2 pips")
        assertEquals("Bless (C)", navRow(s.state, SheetDestination.CONDITIONS).detail, "the concentration spell")
    }

    @Test
    fun aNonCasterLosesTheSlotRowAndNothingElse() {
        val s = Screen(rogue)
        assertNull(s.state.slots, "Vessa casts nothing")
        assertEquals(8, s.state.rows.size, "eight rows")
        assertEquals("AC 14  INIT +3  SPD 25  PB +2", s.state.stats, "leather armor plus a +3 DEX")
    }

    @Test
    fun aMissingIdSaysSoAndDrawsNoRows() {
        val s = Screen(character = null)
        assertFalse(s.state.loading, "the wait ends either way")
        assertTrue(s.state.missing, "there is no such character")
        assertTrue(s.state.rows.isEmpty(), "so there is nothing to draw — BACK is the screen's own chrome")
        assertEquals(
            "BROTHER ALDRIC",
            s.state.title,
            "and the bar still reads as the row the player tapped, not an empty bar over 'No such character.'",
        )
    }

    @Test
    fun theBarCarriesTheTappedRowsNameFromTheFirstFrame() {
        // The load behind this title is the longest in the tool — the character, the compendium's armor
        // table and the whole derivation — and Home already knows the name at the moment of the tap.
        val model = SheetViewModel(cleric.id, FakeCharacterRepository(), Bundle.reader(), "Brother Aldric")
        assertTrue(model.state.value.loading, "nothing has loaded")
        assertEquals("BROTHER ALDRIC", model.state.value.title, "and the bar is already right")
    }

    @Test
    fun theStoredNameWinsOverTheOneTheRowPassed() {
        // A rename elsewhere, or a row drawn from a stale list: the load is the authority, not the argument.
        val s = Screen(cleric, name = "Aldric of the Vale")
        assertEquals("BROTHER ALDRIC", s.state.title, "the character's own name, once it lands")
    }

    @Test
    fun aStoredCharacterThatDisappearsBetweenShowsFallsBackToMissing() {
        val s = Screen(cleric)
        assertFalse(s.state.missing, "loaded first")
        runBlocking { s.repo.delete(cleric.id) }
        s.reload()
        assertTrue(s.state.missing, "a character deleted elsewhere is not drawn from a stale field")
        assertTrue(s.state.rows.isEmpty(), "the rows go with it")
    }

    // ---- the reload is ungated -------------------------------------------------------------------------------

    @Test
    fun everyShowReloadsBecauseTheScreenBelowJustChangedTheCharacter() {
        // S3 is pushed from the HP row and pops straight back with new hit points. A `loaded` guard — which
        // S10's reader and S13.1's list both keep — would draw the sheet as it was before the player left it.
        val s = Screen(cleric)
        assertEquals("31 / 43", assertNotNull(s.state.hp, "before").numbers, "as stored")
        val hurt = cleric.copy(hp = cleric.hp.copy(damage = 30))
        s.repo.stored[cleric.id] = hurt
        s.reload()
        val after = assertNotNull(s.state.hp, "after")
        assertEquals("13 / 43", after.numbers, "the second show reads the character again")
        assertTrue(after.bloodied, "and re-derives what the numbers mean")
    }

    @Test
    fun theArmorTableIsFetchedOnceAndKept() {
        // The compendium cannot change while the tool runs; re-reading it would put a Room query in front of
        // every BACK from S3. Proved by counting the DAO's own calls, not by inspection.
        val dao = Bundle.dao()
        val repo = FakeCharacterRepository().apply {
            stored[cleric.id] = cleric
            updatedAt[cleric.id] = 1L
        }
        val model = SheetViewModel(cleric.id, repo, Bundle.reader(dao))
        runBlocking {
            model.load()
            val afterFirst = dao.calls.size
            assertTrue(afterFirst > 0, "the first load asked the compendium for the armor rows")
            model.load()
            model.load()
            assertEquals(afterFirst, dao.calls.size, "and no later load asked again")
        }
    }

    // ---- the star --------------------------------------------------------------------------------------------

    @Test
    fun theStarTogglesAndSaves() {
        val s = Screen(cleric)
        assertFalse(s.state.inspiration, "Aldric starts without it")
        s.model.toggleInspiration()
        assertTrue(s.state.inspiration, "the star fills")
        assertEquals(1, s.saved.size, "and the character is handed to the repository")
        assertTrue(s.saved.last().inspiration, "with inspiration held")
        s.model.toggleInspiration()
        assertFalse(s.state.inspiration, "a second tap spends it")
        assertEquals(2, s.saved.size, "and saves that too")
        assertFalse(s.saved.last().inspiration, "back to nothing")
    }

    @Test
    fun theStarSurvivesTheFlushAndTheReload() {
        val s = Screen(cleric)
        s.model.toggleInspiration()
        s.model.flushNow()
        s.reload()
        assertTrue(s.state.inspiration, "what was saved is what comes back")
        assertTrue(s.repo.calls.contains("flush"), "and the exit path really flushed")
    }

    @Test
    fun togglingTheStarChangesNothingElseOnTheSheet() {
        // The cached `Derived` is reused across a toggle. Nothing in `Derived` reads inspiration today; if a
        // field ever did, this is where that would surface rather than on the phone.
        val s = Screen(cleric)
        val before = s.state
        s.model.toggleInspiration()
        val after = s.state
        assertEquals(before.stats, after.stats, "the stat line is untouched")
        assertEquals(before.identity, after.identity, "and the identity line")
        assertEquals(before.rows, after.rows, "and every one of the nine rows")
    }

    @Test
    fun theStarIsANoOpBeforeTheCharacterHasLoaded() {
        val model = SheetViewModel(cleric.id, FakeCharacterRepository(), Bundle.reader())
        model.toggleInspiration()
        assertTrue(model.state.value.loading, "still loading, not drawn")
    }

    @Test
    fun aMissingCharacterHasNoStarToToggle() {
        val s = Screen(character = null)
        s.model.toggleInspiration()
        assertTrue(s.state.missing, "still missing")
        assertTrue(s.saved.isEmpty(), "and nothing was written for an id that is not there")
    }

    // ---- the wheel -------------------------------------------------------------------------------------------

    @Test
    fun turnsScrollAndThePressIsConsumedAsANoOp() = runBlocking {
        val s = Screen(cleric)
        val before = s.state
        val seen = ArrayList<Int>()
        val collector = launch { s.model.ticks.collect { seen += it } }
        yield()
        assertTrue(s.model.handleKey(WHEEL_UP), "317 is claimed")
        assertTrue(s.model.handleKey(WHEEL_DOWN), "318 is claimed")
        assertTrue(s.model.handleKey(WHEEL_PRESS), "the press is claimed too — S1 has no primary action")
        yield()
        assertEquals(listOf(-1, 1), seen, "toward the top of the phone scrolls back, toward the bottom forward")
        assertEquals(before, s.state, "and no wheel event changes the sheet")
        collector.cancel()
    }

    @Test
    fun theWheelIsSwallowedBeforeTheSheetIsEvenDrawn() {
        // An unconsumed wheel event reaches LightOS, which relaunches the tool. The loading and missing
        // branches have nothing to scroll and still must not let one through.
        val loading = SheetViewModel(cleric.id, FakeCharacterRepository(), Bundle.reader())
        assertTrue(loading.handleKey(WHEEL_UP), "a turn while loading")
        assertTrue(loading.handleKey(WHEEL_PRESS), "a press while loading")
        val missing = Screen(character = null)
        assertTrue(missing.model.handleKey(WHEEL_DOWN), "a turn on the missing branch")
        assertTrue(missing.model.handleKey(WHEEL_PRESS), "and a press")
    }

    @Test
    fun bothHalvesOfEveryDetentAreConsumedAndNothingElseIs() {
        // `LightKeyHandler` defaults onKeyUp and onKeyMultiple to false, so the *release* half of every detent
        // would relaunch the tool on its own.
        val s = Screen(cleric)
        for (key in listOf(WHEEL_UP, WHEEL_DOWN, WHEEL_PRESS)) {
            assertTrue(s.model.consumesKey(key), "$key is swallowed whole")
        }
        assertFalse(s.model.consumesKey(VOLUME_UP), "volume still reaches LightOS")
        assertFalse(s.model.handleKey(VOLUME_UP), "and is not claimed on the way down either")
    }
}
