package dev.tyler.grimoire.ui.hp

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Event
import dev.tyler.grimoire.rules.Ledger
import dev.tyler.grimoire.rules.Model
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The quiet line under the pad. Every case runs the event through the real [Ledger] and describes the
 * difference, so a line can never claim something the engine did not do — which is the whole point of the
 * module: it reads the before and after, it does not re-derive the rules.
 */
class LastActionTest {
    private val cleric: Character by lazy { Model.decode(Fixtures.character("cleric-5-life")) }
    private val rogue: Character by lazy { Model.decode(Fixtures.character("rogue-3-thief")) }
    private val paladin: Character by lazy { Model.decode(Fixtures.character("paladin-6-warlock-2")) }

    /** The paladin starts at 0 HP with one failure; two more of these kill. */
    private val deadPaladin: Character by lazy {
        Ledger.run(paladin, listOf(Event.DeathSave(3), Event.DeathSave(3)))
    }

    /** The cleric, dropped to exactly 0 with a clean death-save block — the DYING start with nothing recorded. */
    private val downedCleric: Character by lazy { cleric.copy(hp = cleric.hp.copy(damage = cleric.hp.max)) }

    private fun line(start: Character, event: Event): String =
        LastAction.describe(event, start, Ledger.apply(start, event))

    /** The manual path: the same event, described as a pip tap rather than as a die the tool rolled. */
    private fun tappedLine(start: Character, event: Event): String =
        LastAction.describe(event, start, Ledger.apply(start, event), rolled = false)

    // ---- damage ----------------------------------------------------------------------------------------------

    @Test
    fun damageSaysWhatWasTakenAndWhatTemporaryHitPointsAte() {
        val warded = rogue.copy(hp = rogue.hp.copy(temp = 3))
        assertEquals("took 5 · 3 absorbed", line(warded, Event.Damage(5)), "the absorbed clause is the whole reason the line exists")
    }

    @Test
    fun damageWithNothingAbsorbedSaysOnlyWhatWasTaken() {
        assertEquals("took 5", line(cleric, Event.Damage(5)), "no temp, no second clause")
    }

    @Test
    fun damageAtZeroIsADeathSaveFailure() {
        assertEquals("took 5 · failure", line(paladin, Event.Damage(5)), "a hit at 0 HP costs a save, not hit points")
    }

    @Test
    fun aCriticalAtZeroIsTwoFailuresAndCanKill() {
        assertEquals("took 5 · two failures · dead", line(paladin, Event.Damage(5, critical = true)), "1 + 2 is the third failure")
    }

    @Test
    fun massiveDamageSaysDeadWithoutClaimingAFailure() {
        assertEquals("took 86 · dead", line(cleric, Event.Damage(86)), "instant death resets the saves rather than filling one")
    }

    // ---- healing ---------------------------------------------------------------------------------------------

    @Test
    fun healingSaysHowMuchAndThatTheCharacterIsUp() {
        assertEquals("healed 7 · back up at 7 hp", line(paladin, Event.Heal(7)), "off the floor is the clause that matters")
    }

    @Test
    fun healingTheDeadSaysItDidNothing() {
        assertEquals("healed 5 · no effect", line(deadPaladin, Event.Heal(5)), "a control that appears to do nothing is what needs explaining")
    }

    @Test
    fun healingPastTheMaximumSaysSo() {
        assertEquals("healed 999 · at full", line(cleric, Event.Heal(999)), "the cap is silent in the numbers otherwise")
    }

    @Test
    fun healingWithinTheMaximumSaysOnlyTheAmount() {
        assertEquals("healed 5", line(cleric, Event.Heal(5)), "nothing differed from what was asked")
    }

    // ---- temporary hit points --------------------------------------------------------------------------------

    @Test
    fun aGrantThatLosesToTheHigherNumberSaysWhatItKept() {
        assertEquals("temp 3 · kept 5", line(rogue, Event.Temp(3)), "temp HP never stack, and the player would otherwise see nothing move")
    }

    @Test
    fun aGrantThatWinsSaysOnlyItself() {
        assertEquals("temp 8", line(rogue, Event.Temp(8)), "the higher number stood")
    }

    @Test
    fun aCorrectionIsWrittenSigned() {
        assertEquals("temp −2", line(rogue, Event.TempDelta(-2)), "the tool's one signed-number formatter, minus sign and all")
    }

    @Test
    fun aCorrectionClampedAtZeroSaysSo() {
        assertEquals("temp −20 · none left", line(rogue, Event.TempDelta(-20)), "the floor is at 0, not below it")
    }

    // ---- death saves -----------------------------------------------------------------------------------------

    @Test
    fun aSuccessIsNamed() {
        assertEquals("death save 15 · success", line(paladin, Event.DeathSave(15)), "10 or more")
    }

    @Test
    fun aFailureIsNamed() {
        assertEquals("death save 9 · failure", line(paladin, Event.DeathSave(9)), "under 10")
    }

    @Test
    fun aNaturalTwentyReportsTheHitPointBack() {
        assertEquals("death save 20 · back at 1 hp", line(paladin, Event.DeathSave(20)), "a nat 20 is a return to UP, not to STABLE")
    }

    @Test
    fun aNaturalOneCountsTwo() {
        assertEquals("death save 1 · two failures", line(downedCleric, Event.DeathSave(1)), "a nat 1 is two failures")
    }

    @Test
    fun theThirdSuccessSaysStable() {
        val twoSuccesses = Ledger.apply(paladin, Event.DeathSave(15))
        assertEquals("death save 15 · success · stable", line(twoSuccesses, Event.DeathSave(15)), "the third success stabilises")
    }

    @Test
    fun theThirdFailureSaysDead() {
        val once = Ledger.apply(paladin, Event.DeathSave(3))
        assertEquals("death save 3 · failure · dead", line(once, Event.DeathSave(3)), "the third failure kills")
    }

    @Test
    fun aSaveThatTheEngineSwallowsSaysNoChange() {
        assertEquals("death save 15 · no change", line(deadPaladin, Event.DeathSave(15)), "deathSave returns the dead unchanged")
    }

    // ---- the manual path -------------------------------------------------------------------------------------

    @Test
    fun aTappedPipNamesNoDieBecauseNobodyRolledOne() {
        // PIP_SUCCESS_D20 / PIP_FAILURE_D20 are threshold sentinels: they exist to land on the right side
        // of `d20 >= 10` without being a natural 1 or 20. Printing one shows a player who rolled a physical
        // 3 a die they never rolled, on the one line that explains what the rules did.
        assertEquals(
            "death save · success",
            tappedLine(paladin, Event.DeathSave(PIP_SUCCESS_D20)),
            "the outcome is the whole content of a tap",
        )
        assertEquals(
            "death save · failure",
            tappedLine(paladin, Event.DeathSave(PIP_FAILURE_D20)),
            "and the same on the failure row",
        )
    }

    @Test
    fun suppressingTheDieChangesTheLineAndNothingElse() {
        // Only the opening clause differs: every clause after it is read from the engine's before/after and
        // cannot depend on how the value arrived.
        val once = Ledger.apply(paladin, Event.DeathSave(3))
        assertEquals(
            "death save · failure · dead",
            tappedLine(once, Event.DeathSave(PIP_FAILURE_D20)),
            "the third failure still kills, and still says so",
        )
        assertEquals(
            "death save 9 · failure · dead",
            line(once, Event.DeathSave(PIP_FAILURE_D20)),
            "the rolled path over the identical event differs by the number alone",
        )
    }

    // ---- revive ----------------------------------------------------------------------------------------------

    @Test
    fun reviveSaysWhereItLeavesTheCharacter() {
        assertEquals("revived · at 0 hp", line(deadPaladin, Event.Revive), "the saves are cleared; not one hit point comes back")
    }

    // ---- everything S3 does not dispatch ---------------------------------------------------------------------

    @Test
    fun anEventThisScreenNeverSendsHasNoLine() {
        assertEquals("", line(cleric, Event.ShortRest), "S3 dispatches six events; the rest describe nothing here")
        assertEquals("", line(cleric, Event.Dawn), "same for every other rest and resource event")
    }
}
