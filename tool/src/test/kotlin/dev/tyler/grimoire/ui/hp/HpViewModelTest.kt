package dev.tyler.grimoire.ui.hp

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.data.FakeCharacterRepository
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Model
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S3, one test per user action in docs/UI-SPEC.md.
 *
 * The character store is [FakeCharacterRepository], which refuses and stamps exactly as the real one does,
 * and the d20 is injected so a natural 1 and a natural 20 arrive on demand rather than eventually. The
 * tests call `load()` directly for the reason `ReaderViewModelTest` does: `onScreenShow` takes a
 * `SimpleLightScreen`, which needs a real activity — and `load()` is where the behaviour that matters
 * (a reload discarding the undo snapshot) actually lives.
 */
class HpViewModelTest {
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
    private val paladin: Character by lazy { Model.decode(Fixtures.character("paladin-6-warlock-2")) }

    /** The cleric dropped to exactly 0 with a clean death-save block. */
    private fun downed(c: Character) = c.copy(hp = c.hp.copy(damage = c.hp.max))

    /** One loaded screen over one stored character, with the d20 under the test's control. */
    private class Screen(character: Character?, id: String = character?.id ?: "no-such-character") {
        val repo = FakeCharacterRepository().apply {
            if (character != null) {
                stored[character.id] = character
                updatedAt[character.id] = 1L
            }
        }

        var nextRoll: Int = 10

        val model = HpViewModel(id, repo, { nextRoll })

        val state get() = model.state.value

        /** What the screen has asked the repository to store, most recent last. */
        val saved get() = repo.saves

        init {
            runBlocking { model.load() }
        }

        fun reload() = runBlocking { model.load() }
    }

    // ---- the frame -------------------------------------------------------------------------------------------

    @Test
    fun loadingDrawsTheCharactersOwnNumbers() {
        val s = Screen(cleric)
        assertEquals(HpMode.UP, s.state.mode, "31 of 43 is UP")
        assertEquals("31 / 43", s.state.numbers, "the numbers line")
        assertEquals("", s.state.badge, "UP has no badge")
        assertFalse(s.state.loading, "loading clears once the character is read")
        assertFalse(s.state.missing, "the character is stored")
        assertEquals(Verb.DAMAGE, s.state.verb, "DAMAGE is the verb a fight starts on")
    }

    @Test
    fun aCharacterThatIsNotThereLeavesEveryControlInert() {
        val s = Screen(null)
        assertTrue(s.state.missing, "no such character")
        s.model.pad(-5)
        s.model.revive()
        s.model.rollDeathSave()
        assertTrue(s.saved.isEmpty(), "nothing is written for a character that is not there")
        assertTrue(s.model.handleKey(WHEEL_UP), "the wheel is still consumed, or LightOS relaunches the tool")
    }

    // ---- the verbs -------------------------------------------------------------------------------------------

    @Test
    fun damageMinusTakesHitPointsAndSaves() {
        val s = Screen(cleric)
        s.model.pad(-5)
        assertEquals(26, s.state.current, "31 − 5")
        assertEquals("took 5", s.state.lastAction, "the line says what was taken")
        assertEquals(26, s.saved.last().hp.max - s.saved.last().hp.damage, "and it reached the repository")
    }

    @Test
    fun damagePlusGivesItBack() {
        val s = Screen(cleric)
        s.model.pad(5)
        assertEquals(36, s.state.current, "DAMAGE +5 heals, per the spec's signed table")
    }

    @Test
    fun healMinusDamages() {
        val s = Screen(cleric)
        s.model.select(Verb.HEAL)
        s.model.pad(-5)
        assertEquals(26, s.state.current, "HEAL −5 reaches damage(), not a UI-layer inverse")
    }

    @Test
    fun healPlusHeals() {
        val s = Screen(cleric)
        s.model.select(Verb.HEAL)
        s.model.pad(10)
        assertEquals(41, s.state.current, "31 + 10, under the maximum")
    }

    @Test
    fun tempPlusGrantsAndTempMinusCorrects() {
        val s = Screen(rogue)
        s.model.select(Verb.TEMP)
        s.model.pad(1)
        assertEquals(5, s.state.temp, "a grant of 1 loses to the 5 already there — temp HP never stack")
        assertEquals("temp 1 · kept 5", s.state.lastAction, "and the line says so, since nothing moved")
        s.model.pad(10)
        assertEquals(10, s.state.temp, "a grant of 10 wins")
        s.model.pad(-4)
        assertEquals(6, s.state.temp, "and only a correction brings it down")
    }

    @Test
    fun anEventTheEngineSwallowsIsNeitherSavedNorUndoable() {
        val s = Screen(rogue)
        s.model.select(Verb.TEMP)
        s.model.pad(1)
        assertTrue(s.saved.isEmpty(), "nothing changed, so there is nothing to write")
        assertFalse(s.state.canUndo, "and nothing to undo")
    }

    @Test
    fun theVerbSurvivesAReload() {
        val s = Screen(cleric)
        s.model.select(Verb.TEMP)
        s.reload()
        assertEquals(Verb.TEMP, s.state.verb, "the verb is the player's choice, not the character's state")
    }

    // ---- the wheel -------------------------------------------------------------------------------------------

    @Test
    fun theWheelNudgesTheCurrentVerbByOneWhileUp() {
        val s = Screen(cleric)
        assertTrue(s.model.handleKey(WHEEL_DOWN), "the turn is consumed")
        assertEquals(30, s.state.current, "toward the bottom of the phone is −1, which in DAMAGE is one hit point")
        assertTrue(s.model.handleKey(WHEEL_UP), "the turn is consumed")
        assertEquals(31, s.state.current, "toward the top is +1")
    }

    @Test
    fun theWheelNudgesTheVerbInTheOtherTwoPadStates() {
        val dying = Screen(paladin)
        dying.model.select(Verb.HEAL)
        assertTrue(dying.model.handleKey(WHEEL_UP), "consumed while DYING")
        assertEquals(1, dying.state.current, "HEAL +1 is the whole reason the pad stays on screen at 0 HP")
        assertEquals(HpMode.UP, dying.state.mode, "and one hit point is a return to UP")

        val stable = Screen(stabilised())
        stable.model.select(Verb.HEAL)
        assertTrue(stable.model.handleKey(WHEEL_UP), "consumed while STABLE")
        assertEquals(1, stable.state.current, "the pad works in STABLE too")
    }

    @Test
    fun theWheelPressRollsADeathSaveOnlyWhileDying() {
        val s = Screen(paladin)
        s.nextRoll = 15
        assertTrue(s.model.handleKey(WHEEL_PRESS), "the press is consumed")
        assertEquals(2, s.state.successes, "and it rolled")

        val up = Screen(cleric)
        assertTrue(up.model.handleKey(WHEEL_PRESS), "still consumed in UP, or LightOS relaunches the tool")
        assertTrue(up.saved.isEmpty(), "but there is no primary action to take")
    }

    @Test
    fun deadConsumesBothHalvesOfTheWheelAndActsOnNeither() {
        val s = Screen(killed())
        assertEquals(HpMode.DEAD, s.state.mode, "the fixture is dead")
        val before = s.state
        assertTrue(s.model.handleKey(WHEEL_UP), "a turn LightOS receives relaunches the tool")
        assertTrue(s.model.handleKey(WHEEL_DOWN), "both halves")
        assertTrue(s.model.handleKey(WHEEL_PRESS), "and the press")
        assertEquals(before, s.state, "no pad to nudge and no action to take, so nothing happened")
        assertTrue(s.saved.isEmpty(), "and nothing was written")
    }

    @Test
    fun keysThatAreNotTheWheelAreLeftAlone() {
        val s = Screen(cleric)
        assertFalse(s.model.handleKey(VOLUME_UP), "volume still reaches LightOS")
        assertFalse(s.model.consumesKey(VOLUME_UP), "including its release")
        assertTrue(s.model.consumesKey(WHEEL_UP), "every wheel half is swallowed")
        assertTrue(s.model.consumesKey(WHEEL_PRESS), "including the press's release")
    }

    // ---- the death states ------------------------------------------------------------------------------------

    @Test
    fun droppingToZeroBringsUpTheDeathPanelWithoutTakingThePadAway() {
        val s = Screen(cleric)
        s.model.pad(-31)
        assertEquals(0, s.state.current, "exactly down")
        assertEquals(HpMode.DYING, s.state.mode, "the death panel is inserted")
        assertEquals("DOWN", s.state.badge, "with the badge beside the numbers")
        assertEquals(0, s.state.failures, "dropping to 0 resets the saves rather than filling one")
        s.model.select(Verb.HEAL)
        s.model.pad(1)
        assertEquals(HpMode.UP, s.state.mode, "and HEAL +1 is still reachable, which is why the pad stays")
    }

    @Test
    fun aFailedSaveFillsAPip() {
        val s = Screen(paladin)
        s.nextRoll = 9
        s.model.rollDeathSave()
        assertEquals(2, s.state.failures, "one more failure")
        assertEquals("death save 9 · failure", s.state.lastAction, "named on the line")
        assertEquals(HpMode.DYING, s.state.mode, "still dying")
    }

    @Test
    fun threeFailuresKill() {
        val s = Screen(paladin)
        s.nextRoll = 3
        s.model.rollDeathSave()
        s.model.rollDeathSave()
        assertEquals(HpMode.DEAD, s.state.mode, "1 + 2 more is three")
        assertEquals("DEAD", s.state.badge, "the state draws its own word")
        assertEquals(0, s.state.current, "still at 0 hit points")
    }

    @Test
    fun threeSuccessesGoStableAndTakeTheRollButtonAway() {
        val s = Screen(paladin)
        s.nextRoll = 15
        s.model.rollDeathSave()
        s.model.rollDeathSave()
        assertEquals(HpMode.STABLE, s.state.mode, "1 + 2 more is three")
        assertEquals(0, s.state.successes, "stabilising zeroes both counts, so both rows draw hollow")
        assertEquals(0, s.state.failures, "both")
        val before = s.state
        s.model.rollDeathSave()
        assertEquals(before, s.state, "the roll button is gone, and the call behind it is a no-op besides")
    }

    @Test
    fun aNaturalTwentyReturnsToUpAtOneHitPointNotToStable() {
        val s = Screen(paladin)
        s.nextRoll = 20
        s.model.rollDeathSave()
        assertEquals(HpMode.UP, s.state.mode, "a nat 20 regains a hit point, which is UP")
        assertEquals(1, s.state.current, "exactly one")
        assertEquals(0, s.state.successes, "and the block is cleared outright")
    }

    @Test
    fun aNaturalOneIsTwoFailures() {
        val s = Screen(downed(cleric))
        s.nextRoll = 1
        s.model.rollDeathSave()
        assertEquals(2, s.state.failures, "a nat 1 counts twice")
        assertEquals(HpMode.DYING, s.state.mode, "two of three")
    }

    @Test
    fun tappingAHollowPipRecordsASave() {
        val s = Screen(paladin)
        s.model.tapSuccess(1)
        assertEquals(2, s.state.successes, "the manual path, for a player who rolled a physical d20")
        assertEquals(
            "death save · success",
            s.state.lastAction,
            "and the line names no die: $PIP_SUCCESS_D20 is a threshold sentinel, not the d20 the player rolled",
        )
        s.model.tapFailure(1)
        assertEquals(2, s.state.failures, "and the same on the failure row")
        assertEquals("death save · failure", s.state.lastAction, "no sentinel on this row either")
        assertEquals(2, s.saved.size, "both reached the repository")
    }

    @Test
    fun aRolledSaveStillNamesItsDieUnlikeATappedPip() {
        val s = Screen(paladin)
        s.nextRoll = 14
        s.model.rollDeathSave()
        assertEquals(
            "death save 14 · success",
            s.state.lastAction,
            "the app rolled this one, so the number is a die the player can check",
        )
    }

    @Test
    fun tappingAPipThatIsAlreadyFilledDoesNothing() {
        val s = Screen(paladin)
        s.model.tapSuccess(0)
        assertEquals(1, s.state.successes, "the wireframe says tap a hollow pip")
        assertTrue(s.saved.isEmpty(), "so a tap on a filled one is not a second save")
    }

    @Test
    fun stablePipsAreNotAPathToARecordedSave() {
        val s = Screen(stabilised())
        val before = s.state
        s.model.tapSuccess(0)
        s.model.tapFailure(0)
        assertEquals(before, s.state, "deathSave returns a stable character unchanged, so the screen draws no live control")
    }

    // ---- revive ----------------------------------------------------------------------------------------------

    @Test
    fun reviveClearsTheSavesAndLeavesTheCharacterAtZero() {
        val s = Screen(killed())
        s.model.revive()
        assertEquals(HpMode.DYING, s.state.mode, "back to DYING, ready for saves again")
        assertEquals(0, s.state.current, "resurrection is the DM's call; the tool restores no hit points")
        assertEquals(0, s.state.successes, "the block is back to its default")
        assertEquals(0, s.state.failures, "both counts")
        assertEquals("revived · at 0 hp", s.state.lastAction, "said plainly")
        assertEquals(1, s.saved.size, "and written")
    }

    @Test
    fun reviveIsOnlyOfferedToTheDead() {
        val s = Screen(cleric)
        val before = s.state
        s.model.revive()
        assertEquals(before, s.state, "there is no REVIVE button in the other three states, and no path behind it either")
    }

    // ---- undo ------------------------------------------------------------------------------------------------

    @Test
    fun undoRevertsTheLastActionAndIsOnlyOneDeep() {
        val s = Screen(cleric)
        s.model.pad(-5)
        s.model.pad(-5)
        assertEquals(21, s.state.current, "two hits")
        assertTrue(s.state.canUndo, "and something to take back")
        s.model.undo()
        assertEquals(26, s.state.current, "the second is undone")
        assertEquals(LastAction.UNDONE, s.state.lastAction, "the tap is acknowledged even though it may move no number")
        assertFalse(s.state.canUndo, "one deep — a second level would be a history, which this tool does not keep")
        s.model.undo()
        assertEquals(26, s.state.current, "so a second UNDO is a consumed no-op")
    }

    @Test
    fun undoIsWrittenBackLikeAnyOtherChange() {
        val s = Screen(cleric)
        s.model.pad(-5)
        s.model.undo()
        assertEquals(cleric, s.saved.last(), "the reverted character is the one that must survive the screen pop")
    }

    @Test
    fun undoDoesNotSurviveAReload() {
        val s = Screen(cleric)
        s.model.pad(-5)
        assertTrue(s.state.canUndo, "armed")
        s.reload()
        assertFalse(s.state.canUndo, "an in-memory snapshot, not a stored state — onScreenShow reloads on every resume")
        assertEquals("", s.state.lastAction, "and the line is cleared with it")
        s.model.undo()
        assertEquals(26, s.state.current, "so UNDO after a relaunch is a no-op, not a revert to a stale snapshot")
    }

    @Test
    fun undoWithNothingToUndoIsANoOp() {
        val s = Screen(cleric)
        s.model.undo()
        assertEquals(31, s.state.current, "nothing happened")
        assertTrue(s.saved.isEmpty(), "and nothing was written")
    }

    // ---- persistence -----------------------------------------------------------------------------------------

    @Test
    fun everyActionReachesTheRepository() {
        val s = Screen(cleric)
        s.model.pad(-5)
        s.model.select(Verb.TEMP)
        s.model.pad(4)
        s.model.select(Verb.HEAL)
        s.model.pad(2)
        s.model.handleKey(WHEEL_DOWN)
        s.model.undo()
        assertEquals(5, s.saved.size, "four pad actions and the undo, each written")
        assertEquals(
            listOf("load:${cleric.id}", "save:${cleric.id}", "save:${cleric.id}", "save:${cleric.id}", "save:${cleric.id}", "save:${cleric.id}"),
            s.repo.calls,
            "one load and one save per action — no read the screen does not need",
        )
    }

    @Test
    fun theScreenFlushesOnTheWayOut() {
        val s = Screen(cleric)
        s.model.pad(-5)
        assertEquals(cleric, s.repo.stored[cleric.id], "the save is debounced, so the store is still behind")
        s.model.onCleared()
        assertEquals(26, s.repo.stored[cleric.id]!!.let { it.hp.max - it.hp.damage }, "and the flush every character screen inherits lands it")
    }

    // ---- fixtures --------------------------------------------------------------------------------------------

    /** The paladin after three failures. */
    private fun killed(): Character {
        val s = Screen(paladin)
        s.nextRoll = 3
        s.model.rollDeathSave()
        s.model.rollDeathSave()
        return s.saved.last()
    }

    /** The paladin after three successes: stable, still at 0 HP, both counts zeroed. */
    private fun stabilised(): Character {
        val s = Screen(paladin)
        s.nextRoll = 15
        s.model.rollDeathSave()
        s.model.rollDeathSave()
        return s.saved.last()
    }
}
