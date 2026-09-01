package dev.tyler.grimoire.ui.hp

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.data.CharacterRepository
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Event
import dev.tyler.grimoire.rules.Ledger
import dev.tyler.grimoire.rules.RulesException
import dev.tyler.grimoire.ui.common.CharacterViewModel
import dev.tyler.grimoire.ui.keys.WheelEvent
import dev.tyler.grimoire.ui.keys.WheelHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * S3's view model: one character's hit points, its death saves, and the one-deep undo that survives only
 * this visit (docs/UI-SPEC.md S3).
 *
 * **There is one mutation path and everything goes through it.** [apply] snapshots the character, hands
 * the event to `Ledger`, describes the difference and saves — so undo, the last-action line and the write
 * cannot get out of step with each other, whichever control the player touched.
 *
 * **Unlike S10's reader, the load is *not* idempotent.** `onScreenShow` fires on every `onResume`, which
 * includes a LightOS modal handing the screen back, and S3 wants exactly that: a reload discards the undo
 * snapshot, which is the spec's own definition of how long `UNDO` lives. A guard here would quietly make
 * `UNDO` survive a context switch it is documented not to survive.
 *
 * [roll] is the d20 `[ ROLL DEATH SAVE ]` uses, injected so the gate can drive a natural 1 and a natural
 * 20 on demand; [scope] is injected for the same reason `viewModelScope` cannot be touched on the JVM (it
 * dispatches on Main). Neither has a device-flavoured default hiding in this file — `HpScreen` supplies
 * both.
 */
class HpViewModel(
    characterId: String,
    repo: CharacterRepository,
    private val roll: () -> Int,
    scope: CoroutineScope? = null,
) : CharacterViewModel<Unit>(repo, characterId) {
    private val _state = MutableStateFlow(HpUiState())
    val state: StateFlow<HpUiState> = _state.asStateFlow()

    /** `by lazy` so `viewModelScope` is never touched in a test, which has no Main dispatcher to build it on. */
    private val loadScope: CoroutineScope by lazy { scope ?: viewModelScope }

    private var character: Character? = null

    /**
     * The one-deep undo snapshot, in memory and nowhere else. It reverts a mis-tap in the moment; a
     * persisted undo log would be a ledger of the session, which is not something this tool keeps.
     */
    private var undo: Character? = null

    private var verb: Verb = Verb.DAMAGE

    private var lastAction: String = ""

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        loadScope.launch { load() }
    }

    /**
     * Read the character and draw it. Deliberately re-runs on every show — see the class KDoc — and
     * clears the undo snapshot and the last-action line as it goes, because both describe a visit that
     * has just ended.
     *
     * Driven directly by the gate: `onScreenShow` takes a `SimpleLightScreen`, which needs a real
     * activity, so the tests call this instead — the same seam `ReaderViewModel` uses.
     */
    internal suspend fun load() {
        val loaded = repo.load(characterId)
        character = loaded
        undo = null
        lastAction = ""
        render()
    }

    /** Choose what the pad and the wheel apply to. Survives a reload: it is the player's choice, not the character's. */
    fun select(verb: Verb) {
        this.verb = verb
        render()
    }

    /** A `±n` button, or a wheel detent. [delta] is signed; the verb decides which engine function it reaches. */
    fun pad(delta: Int) {
        apply(eventFor(verb, delta))
    }

    /** `[ ROLL DEATH SAVE ]` — the app's own d20, which handles a natural 1 and a natural 20 in the engine. */
    fun rollDeathSave() {
        if (mode() != HpMode.DYING) return
        apply(Event.DeathSave(roll()))
    }

    /**
     * A tap on the success strip's pip [index] — the manual path for a player who rolled a physical d20.
     *
     * A tap on a pip that is already filled is ignored: the wireframe's instruction is to tap a *hollow*
     * pip, and `PipStrip` is deliberately dumb about what a tap means, so the rule lives here where it can
     * be tested. The strip is passed no `onTap` at all in STABLE — the [HpMode.DYING] guard is the second
     * line of the same defence, since `Ledger.deathSave` would swallow the event and leave a live-looking
     * control that does nothing.
     */
    fun tapSuccess(index: Int) {
        tapPip(index, _state.value.successes, PIP_SUCCESS_D20)
    }

    /** A tap on the failure strip's pip [index] — see [tapSuccess]. */
    fun tapFailure(index: Int) {
        tapPip(index, _state.value.failures, PIP_FAILURE_D20)
    }

    /**
     * `[ REVIVE ]` — the death-save block back to its default, hit points untouched, so the character
     * returns to [HpMode.DYING] at 0 HP. A rules event ([Event.Revive]), not a view-model `copy()`:
     * `deathSaves` is a rules field and every mutation of it goes through `Ledger`.
     */
    fun revive() {
        if (mode() != HpMode.DEAD) return
        apply(Event.Revive)
    }

    /**
     * `UNDO` — revert the single most recent action of this visit. One deep: it fixes a mis-tap, and a
     * second level would be a history. A tap with nothing to undo is a consumed no-op.
     */
    fun undo() {
        val previous = undo ?: return
        undo = null
        character = previous
        lastAction = LastAction.UNDONE
        repo.save(previous)
        render()
    }

    /**
     * Snapshot, apply, describe, save. The only place the character changes.
     *
     * An event the engine returns unchanged — healing the dead, granting temp HP below what is already on
     * the sheet — is **not** saved and does not arm `UNDO`: there is nothing to write back and nothing to
     * revert. The line still says what happened ("healed 5 · no effect"), because a control that appears
     * to do nothing is exactly what needs explaining.
     *
     * A [RulesException] means the UI offered a control it should have disabled; the message is put on the
     * line rather than swallowed, so a player can read it out instead of finding a screen that stopped
     * responding.
     *
     * [rolled] is passed through to [LastAction.describe] untouched: it changes nothing about the event the
     * engine receives, only whether the line names the d20 — see [tapPip].
     */
    private fun apply(event: Event, rolled: Boolean = true) {
        val before = character ?: return
        val after = try {
            Ledger.apply(before, event)
        } catch (rejected: RulesException) {
            lastAction = rejected.message.orEmpty()
            render()
            return
        }
        lastAction = LastAction.describe(event, before, after, rolled)
        if (after != before) {
            undo = before
            character = after
            repo.save(after)
        }
        render()
    }

    /**
     * The manual path both pip strips share, and the one caller that tells [LastAction] the die is not real:
     * [PIP_SUCCESS_D20] and [PIP_FAILURE_D20] are threshold sentinels, not a d20 anybody rolled, so the line
     * reads `death save · success` rather than quoting a 10 back at a player who rolled a physical 14.
     */
    private fun tapPip(index: Int, filled: Int, d20: Int) {
        if (mode() != HpMode.DYING) return
        if (index < filled) return
        apply(Event.DeathSave(d20), rolled = false)
    }

    /** Null until the character has loaded, which is what makes every control a no-op until then. */
    private fun mode(): HpMode? = character?.let(HpMode::of)

    private fun render() {
        val c = character
        _state.value = when {
            c == null -> HpUiState(loading = false, missing = true)
            else -> HpUiState.of(c, verb, lastAction, canUndo = undo != null)
        }
    }

    /**
     * The wheel. Turns nudge the current verb by ±1 in the three states that draw a pad — toward the top
     * of the phone is `+1`, the direction the number goes — and the press rolls a death save while DYING,
     * the Dice screen's "press rolls" convention.
     *
     * **DEAD consumes both halves and acts on neither**, which is not laziness: it draws no pad and no
     * chips, so there is no verb to nudge and no primary action to press — and an unconsumed wheel event
     * reaches LightOS, which foregrounds itself and relaunches the tool. Losing the screen is a worse
     * answer than doing nothing. The same is true before the character has loaded.
     */
    fun handleKey(keyCode: Int): Boolean = when (WheelHandler.of(keyCode)) {
        WheelEvent.UP -> {
            turn(1)
            true
        }
        WheelEvent.DOWN -> {
            turn(-1)
            true
        }
        WheelEvent.PRESS -> {
            if (mode() == HpMode.DYING) rollDeathSave()
            true
        }
        null -> false
    }

    private fun turn(delta: Int) {
        when (mode()) {
            HpMode.UP, HpMode.DYING, HpMode.STABLE -> pad(delta)
            HpMode.DEAD, null -> Unit
        }
    }

    /** Every key this screen swallows whatever the action — see `WheelHandler.consumes` for why the halves matter. */
    fun consumesKey(keyCode: Int): Boolean = WheelHandler.consumes(keyCode)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = handleKey(keyCode)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = consumesKey(keyCode)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean = consumesKey(keyCode)
}
