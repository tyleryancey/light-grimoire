package dev.tyler.grimoire.ui.hp

import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Derive
import dev.tyler.grimoire.rules.Event
import dev.tyler.grimoire.ui.common.Signs

/**
 * The one quiet line under S3's pad: what the player just did, and what the engine did with it.
 *
 * **The rule the whole file follows: say what was asked for, then say where the rules differed.** A pad
 * tap is a sentence about intent ("took 5"), and the interesting part is always the clause after it —
 * that 3 of the 5 were absorbed by temporary hit points, that a heal did nothing because the character is
 * dead, that a grant of 4 lost to a temp of 8 already on the sheet, that a hit at 0 HP was a death-save
 * failure. Those are exactly the outcomes a player would otherwise have to reconstruct by comparing two
 * numbers they were not watching, on the screen where a mis-read costs a character.
 *
 * Every clause is derived from the [before] and [after] characters rather than re-implemented from the
 * rules: this file must never be a second opinion about what `Ledger` does. It reads the difference.
 *
 * Lower case throughout, because it is drawn in the same lightened `Detail` as every other quiet line in
 * the tool, and joined with the `·` the wireframes use as a mid-line separator.
 */
object LastAction {
    /** The mid-line separator every frame in docs/UI-SPEC.md uses. */
    const val SEPARATOR: String = " · "

    /** What `UNDO` leaves on the line — the tap has to be visibly acknowledged, since the numbers may not move. */
    const val UNDONE: String = "undone"

    /**
     * [rolled] says whether the d20 in an [Event.DeathSave] is a die the player actually saw.
     *
     * The default is the honest path: `[ ROLL DEATH SAVE ]` rolls the app's own d20 and the line names it.
     * The one caller that passes `false` is a **pip tap**, whose `9`/`10` are sentinels chosen only to land
     * on the right side of `Ledger.deathSave`'s threshold without being a natural 1 or 20 (see
     * `PIP_SUCCESS_D20`). Printing one would show a player who rolled a physical 3 a die they never rolled,
     * on the one line that exists to explain what the rules did — so the manual path says `death save ·
     * failure` and leaves the number where it belongs, inside the engine.
     */
    fun describe(event: Event, before: Character, after: Character, rolled: Boolean = true): String =
        clauses(event, before, after, rolled).joinToString(SEPARATOR)

    private fun clauses(event: Event, before: Character, after: Character, rolled: Boolean): List<String> = when (event) {
        is Event.Damage -> damage(event, before, after)
        is Event.Heal -> heal(event, before, after)
        is Event.Temp -> temp(event, before, after)
        is Event.TempDelta -> tempDelta(event, before, after)
        is Event.DeathSave -> deathSave(event, before, after, rolled)
        Event.Revive -> listOf("revived", "at ${current(after)}")
        // Exhaustive on purpose: S3 dispatches only the six above, and an event added to the engine should
        // have to decide here rather than be silently described as nothing.
        is Event.SpendHitDie, is Event.SpendSlot, is Event.CounterDelta,
        Event.ShortRest, Event.LongRest, Event.Dawn, Event.SpendPactSlot,
        -> emptyList()
    }

    private fun damage(event: Event.Damage, before: Character, after: Character): List<String> {
        val out = mutableListOf("took ${event.amount}")
        val absorbed = before.hp.temp - after.hp.temp
        if (absorbed > 0) out += "$absorbed absorbed"
        out += failureClauses(before, after)
        return out
    }

    private fun heal(event: Event.Heal, before: Character, after: Character): List<String> {
        val out = mutableListOf("healed ${event.amount}")
        val regained = before.hp.damage - after.hp.damage
        when {
            // A dead character, or one already at full: the engine returned them unchanged.
            regained == 0 -> out += "no effect"
            // The clause that matters most on this screen — the character is off the floor and saving no more.
            Derive.hpState(before).down -> out += "back up at ${current(after)}"
            regained < event.amount -> out += "at full"
        }
        return out
    }

    private fun temp(event: Event.Temp, before: Character, after: Character): List<String> {
        val out = mutableListOf("temp ${event.amount}")
        // A grant keeps the higher number (SRD 5.1: temporary hit points never stack), so a smaller one
        // simply loses — silently, unless it is said here.
        if (after.hp.temp == before.hp.temp && event.amount <= before.hp.temp) out += "kept ${before.hp.temp}"
        return out
    }

    private fun tempDelta(event: Event.TempDelta, before: Character, after: Character): List<String> {
        val out = mutableListOf("temp ${Signs.mod(event.delta)}")
        if (after.hp.temp == 0 && before.hp.temp + event.delta < 0) out += "none left"
        return out
    }

    private fun deathSave(
        event: Event.DeathSave,
        before: Character,
        after: Character,
        rolled: Boolean,
    ): List<String> {
        val out = mutableListOf(if (rolled) "death save ${event.d20}" else "death save")
        val was = before.deathSaves
        val now = after.deathSaves
        // Nothing moved: the character was already stable or dead, and `Ledger.deathSave` returned early.
        if (was == now) return out + "no change"
        val gainedFailures = now.failures - was.failures
        when {
            // A natural 20 regains 1 HP and clears the block outright, which is a return to UP, not STABLE.
            event.d20 == 20 -> out += "back at ${current(after)}"
            now.stable && !was.stable -> {
                out += "success"
                out += "stable"
            }
            now.successes > was.successes -> out += "success"
            // A natural 1 counts two — unless only one was left before three, which is all the engine adds.
            gainedFailures >= 2 -> out += "two failures"
            gainedFailures == 1 -> out += "failure"
        }
        if (now.dead && !was.dead) out += "dead"
        return out
    }

    /** The death-save clauses damage at 0 HP produces: a failure (two on a critical), and possibly death. */
    private fun failureClauses(before: Character, after: Character): List<String> {
        val out = mutableListOf<String>()
        val gained = after.deathSaves.failures - before.deathSaves.failures
        if (gained >= 2) out += "two failures" else if (gained == 1) out += "failure"
        if (after.deathSaves.dead && !before.deathSaves.dead) out += "dead"
        return out
    }

    /**
     * Current hit points as the line writes them — straight from `Derive`, not re-derived here. The whole
     * file's rule is that it reads what the engine did; the floor at 0 is the engine's to define.
     */
    private fun current(c: Character): String = "${Derive.hpState(c).current} hp"
}
