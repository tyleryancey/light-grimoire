package dev.tyler.grimoire.ui.hp

import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.DeathSaves
import dev.tyler.grimoire.rules.Derive

/**
 * S3's four states (docs/UI-SPEC.md S3). The bars never change between them — only the middle of the
 * screen does — and [badge] is the word each state puts beside (or, for [DEAD], above) the numbers.
 */
enum class HpMode(val badge: String) {
    /** `current > 0`. Numbers, the pad, the verb chips. */
    UP(""),

    /** `current == 0`, not stable, not dead: the death panel is inserted **above** the pad. */
    DYING("DOWN"),

    /** `deathSaves.stable`. The panel stays, but its pips and its roll button are gone. */
    STABLE("STABLE"),

    /** `deathSaves.dead`. No pad, no chips — every control that reached here is a no-op in the engine. */
    DEAD("DEAD");

    companion object {
        /**
         * The state, in the order the flags outrank each other: dead, then stable, then at 0 HP, then up.
         *
         * The order is pinned here rather than derived from `DerivedHp.down` alone because two of the
         * three flags can be true at once in a way `down` cannot see: `Ledger.deathSave` leaves a stable
         * character at 0 HP (so `down` is true and STABLE is the answer), and `Ledger.damage` carries
         * `dead` forward while the character is still at 0. The engine cannot currently produce
         * `stable && current > 0` — `heal` clears the saves the moment HP comes back — but reading the
         * flags first means a future rule that could would draw the state its flags say, not the one its
         * hit points imply.
         */
        fun of(saves: DeathSaves, current: Int): HpMode = when {
            saves.dead -> DEAD
            saves.stable -> STABLE
            current <= 0 -> DYING
            else -> UP
        }

        fun of(character: Character): HpMode = of(character.deathSaves, Derive.hpState(character).current)
    }
}

/**
 * What the `±n` pad and the wheel apply to. Each verb is *signed* (docs/UI-SPEC.md S3's verb table), so
 * the same six buttons cover both directions of every correction — see [eventFor] for the mapping.
 */
enum class Verb(val label: String) {
    DAMAGE("DAMAGE"),
    HEAL("HEAL"),
    TEMP("TEMP"),
}

/**
 * Everything S3 draws, already derived (docs/UI-SPEC.md S3).
 *
 * The numbers and the pips come from the character through [of]; [verb], [lastAction] and [canUndo] are
 * the view model's own, because none of them is a property of the character — the verb is what the player
 * last selected, and the undo snapshot lives only as long as this visit to the screen.
 *
 * [loading] holds until the first load finishes and [missing] is the "no such character" branch. Every
 * other field is meaningless while either is true, which is why the screen branches on them first.
 */
data class HpUiState(
    val mode: HpMode = HpMode.UP,
    val current: Int = 0,
    val max: Int = 0,
    val temp: Int = 0,
    /** At or below half the maximum, and above 0 — drawn as bold numbers, the tool's one weight cue. */
    val bloodied: Boolean = false,
    /** [HpMode.badge]: "", "DOWN", "STABLE", "DEAD". */
    val badge: String = "",
    val successes: Int = 0,
    val failures: Int = 0,
    val verb: Verb = Verb.DAMAGE,
    /** One quiet line under the pad — see [LastAction]. Empty before the first action of the visit. */
    val lastAction: String = "",
    /** Whether `UNDO` has a snapshot to revert to. A tap with nothing to undo is a consumed no-op anyway. */
    val canUndo: Boolean = false,
    val loading: Boolean = true,
    val missing: Boolean = false,
) {
    /** The `31 / 43` of every frame. */
    val numbers: String get() = "$current / $max"

    /**
     * The temporary hit points, drawn **immediately after the numbers** and carrying their own separator:
     * `0 / 43 · temp 6   DOWN` (docs/UI-SPEC.md S3, the *(b)* paragraph).
     *
     * **UP draws `temp 0` on its own line and this is empty; DYING and STABLE put temp *here* instead, and
     * only when it is non-zero.** The wireframes draw the temp line in UP alone, and DYING's vertical
     * budget has no unit to spare for a second line — but temporary hit points absorb damage at 0 HP
     * exactly as they do above it (there is a fixture for it, "temp hp absorbs damage while at zero"), so a
     * screen that hid them at 0 would hide them on the one turn they decide whether a failure is recorded.
     * Trailing the status line costs no vertical units at all.
     *
     * It sits *before* [statusTrailing] because every frame in the spec puts the badge last, after a gap —
     * the DYING and STABLE wireframes (`0 / 43   DOWN`) are simply this line's `temp == 0` case.
     */
    val statusTemp: String
        get() = when (mode) {
            HpMode.UP, HpMode.DEAD -> ""
            HpMode.DYING, HpMode.STABLE -> if (temp > 0) "${LastAction.SEPARATOR}temp $temp" else ""
        }

    /**
     * What trails the status line after a gap: the state's badge, the last thing on the line in every frame
     * the spec draws — `0 / 43   DOWN`, `0 / 43 · temp 6   DOWN`, `0 / 43   STABLE`.
     *
     * UP has no badge at all, and DEAD draws its own above the numbers in `Heading`, so both are empty here.
     */
    val statusTrailing: String
        get() = when (mode) {
            HpMode.UP, HpMode.DEAD -> ""
            HpMode.DYING, HpMode.STABLE -> badge
        }

    companion object {
        fun of(
            character: Character,
            verb: Verb,
            lastAction: String = "",
            canUndo: Boolean = false,
        ): HpUiState {
            val hp = Derive.hpState(character)
            val saves = character.deathSaves
            val mode = HpMode.of(saves, hp.current)
            return HpUiState(
                mode = mode,
                current = hp.current,
                max = hp.max,
                temp = hp.temp,
                bloodied = hp.bloodied,
                badge = mode.badge,
                successes = saves.successes,
                failures = saves.failures,
                verb = verb,
                lastAction = lastAction,
                canUndo = canUndo,
                loading = false,
                missing = false,
            )
        }
    }
}
