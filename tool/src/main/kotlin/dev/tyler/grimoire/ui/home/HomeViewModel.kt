package dev.tyler.grimoire.ui.home

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.compendium.ImportState
import dev.tyler.grimoire.data.CharacterLimits
import dev.tyler.grimoire.data.CharacterRepository
import dev.tyler.grimoire.data.CharacterSummaryRow
import dev.tyler.grimoire.data.Ids
import dev.tyler.grimoire.data.NewCharacter
import dev.tyler.grimoire.rules.RulesException
import dev.tyler.grimoire.ui.keys.WheelEvent
import dev.tyler.grimoire.ui.keys.WheelHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * S0's view model: the compendium's import state, the character list, and the one write Home makes — a
 * character created from a name and a class (docs/UI-SPEC.md S0, S12 steps 1–2).
 *
 * **Every seam is a parameter, and that is the whole reason this file changed in M3.** The M2 version took
 * a `SealedLightContext` and reached for `CompendiumStore` and `GrimoireStore` itself, which made Home the
 * one screen with no JVM test at all: a context needs an activity. [ensureImported] is the store's own
 * `ensureImported(ctx)` bound by [HomeScreen], [importState] is `CompendiumStore.state`, and [characters]
 * is the process's one repository — so the gate can drive a failed import, a full store and six characters
 * without a phone.
 *
 * **The list is reloaded on every show, with no `loaded` guard.** `LightActivity.goBack()` calls the
 * previous screen's `notifyWillShow()` *before* it delivers the popped screen's result, so Home is asked to
 * draw itself again on the way back from every sheet: a guard would leave a renamed character under its old
 * name and a damaged one at its old hit points. It is one indexed query capped at
 * [CharacterLimits.MAX_CHARACTERS] rows, and it never decodes a character document.
 *
 * [scope] is injected for the reason `HpViewModel`'s is — `viewModelScope` dispatches on Main, which a JVM
 * test has no dispatcher for — and [newId] so a test can name the character it created.
 */
class HomeViewModel(
    private val ensureImported: () -> Unit,
    private val importState: StateFlow<ImportState>,
    private val characters: CharacterRepository,
    scope: CoroutineScope? = null,
    private val newId: () -> String = Ids::new,
) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(HomeUiState.of(importState.value, emptyList()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _ticks = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** One wheel detent: -1 toward the top of the phone (key 317), +1 toward the bottom (318). */
    val ticks: Flow<Int> = _ticks.asSharedFlow()

    /** `by lazy` so `viewModelScope` is never touched in a test, which has no Main dispatcher to build it on. */
    private val loadScope: CoroutineScope by lazy { scope ?: viewModelScope }

    private var rows: List<CharacterSummaryRow> = emptyList()

    /** False until the first [reload] answers — see [HomeUiState.listLoaded] for the frame that needs it. */
    private var listLoaded = false

    private var message: String? = null

    private var messageNonce = 0

    init {
        // The import runs on the store's own scope and finishes without anything asking again, so Home
        // watches the state for the whole life of the screen rather than sampling it per show: the 2.3 s
        // first launch has to move its bar and then swap in the list on its own.
        loadScope.launch { importState.collect { render() } }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        loadScope.launch { show() }
    }

    /**
     * What every show does: ask the store to make sure the compendium is imported (free once Ready, a retry
     * after a failure — the state itself is the guard), drop any refusal line, and reload the list.
     *
     * The refusal is dropped because it describes a tap that happened on this screen; a player who has been
     * to a sheet and back is not still being told they have six characters. Driven directly by the gate:
     * `onScreenShow` takes a `SimpleLightScreen`, which needs a real activity.
     *
     * **The clear runs early on purpose, and [requestClass] depends on that.** `goBack()` shows Home before
     * it delivers the popped screen's result, so this method is already in flight when the name editor's
     * result arrives; [requestClass] queues its refusal on the same scope so it lands *after* this clear
     * rather than under it. Moving `message = null` below [reload] would leave that refusal in a race with
     * a suspending query.
     */
    internal suspend fun show() {
        ensureImported()
        // The nonce is not reset with it: it only ever counts refusals, so the screen's "scroll this into
        // view" effect cannot mistake a cleared line for a new one.
        message = null
        reload()
    }

    private suspend fun reload() {
        rows = characters.list()
        listLoaded = true
        render()
    }

    /**
     * `NEW`, before anything is typed: true when the flow may start, false when it may not — and then the
     * refusal is on the screen instead.
     *
     * Refusing here rather than at `create` is the point. The alternative is a player who types a name,
     * picks a class and is told at the end that the store is full, having transcribed for nothing; the
     * repository still refuses a seventh character, in the same words ([CharacterLimits.tooMany]), because
     * this check is a courtesy and that one is the rule.
     *
     * The two silent falses are states the player cannot see `NEW` in (before Ready) or is a frame away
     * from (the list query still in flight): there is nothing true to say about a store that has not
     * answered yet, and a count of zero would put the wrong number in the sentence.
     */
    fun requestNew(): Boolean {
        val current = _state.value
        if (current.canCreate) return true
        if (current.body != HomeBody.LIST || !current.listLoaded) return false
        refuse(CharacterLimits.tooMany(current.characters.size))
        return false
    }

    /**
     * The typed name, before the class picker is pushed: true when the flow may go on, false when the
     * refusal is on the screen instead.
     *
     * **This is the second courtesy check, and it exists because the editor's result cannot be re-read.**
     * `LightTextInputEditor` takes no length parameter, so a 41-character name is typable and submittable;
     * `CharacterLimits.check` inside [create] refuses it either way. The difference is when. Refused here,
     * the player is told with the name still one BACK away in their head; refused after the class step, the
     * typed name is simply gone and has to come off the paper sheet a second time. The rule itself stays in
     * the repository — this only moves the moment it fires.
     *
     * A blank name is *not* refused here: the screen treats it as a cancel, which is what the editor
     * returns for an untouched field, and a cancel earns no sentence.
     *
     * The refusal goes through [loadScope] rather than being set inline, and that is load-bearing:
     * `LightActivity.goBack()` calls Home's `notifyWillShow()` — which queues [show], which clears
     * [message] — *before* it delivers the editor's result, so a message set inline could be wiped by the
     * show that is already in flight. Queued, it lands after it. (Argued from `LightActivity.kt:73-86`, not
     * gated by a test: the JVM gate's scope is unconfined and its store never suspends, so both orderings
     * pass there.)
     */
    fun requestClass(name: String): Boolean {
        if (name.length <= CharacterLimits.MAX_NAME) return true
        loadScope.launch { refuse(CharacterLimits.nameTooLong(name.length)) }
        return false
    }

    /**
     * Create the character S12's first two steps describe and hand its id back for the sheet to open.
     *
     * [onCreated] runs after the write has landed, never beside it: `create` is the one repository call a
     * caller must await before navigating (`CharacterRepository.create`), and on [loadScope] — Main on
     * device — the callback resumes on the thread that may push a screen.
     *
     * A refusal is a sentence, not a crash: `CharacterLimits.check` runs inside `create`, so a name past 40
     * characters or a class key the tables do not know lands on the message line and nothing is created or
     * opened. This is the backstop, not the place the player meets either rule — the cap is refused by
     * [requestNew] before the editor opens and the name's length by [requestClass] as soon as it closes,
     * so nothing that reaches here has been paid for with a transcription.
     */
    fun create(name: String, classKey: String, onCreated: (String) -> Unit) {
        loadScope.launch {
            val created = try {
                characters.create(NewCharacter.of(name, classKey, newId()))
            } catch (refused: RulesException) {
                refuse(refused.message.orEmpty())
                return@launch
            }
            // The list is not reloaded here: the sheet opens on top, and the way back from it is a show.
            onCreated(created.id)
        }
    }

    private fun refuse(text: String) {
        message = text
        messageNonce++
        render()
    }

    private fun render() {
        _state.value = HomeUiState.of(importState.value, rows, listLoaded, message, messageNonce)
    }

    /**
     * The wheel. Turns scroll the character list, one row per detent (S0), and the press is consumed as a
     * no-op: Home has no primary action — a character opens on a tap, `NEW` and `ABOUT` are bar buttons.
     *
     * **Both halves are consumed in every state, including the whole of the import**, which is the spec's
     * own instruction and the one place it matters most: an unconsumed wheel event reaches LightOS, which
     * foregrounds itself and relaunches the tool, and Home during those 2.3 s is the screen a first-time
     * player is most likely to be resting a thumb on.
     */
    fun handleKey(keyCode: Int): Boolean = when (WheelHandler.of(keyCode)) {
        WheelEvent.UP -> {
            scroll(-1)
            true
        }
        WheelEvent.DOWN -> {
            scroll(1)
            true
        }
        WheelEvent.PRESS -> true
        null -> false
    }

    /**
     * Consumed everywhere, acted on only where there is a list — "before Ready … there is no list and no
     * action, so the turn is consumed as a no-op too" (docs/UI-SPEC.md S0).
     *
     * The guard is also what keeps the scroll honest: a `ScrollState` reports `maxValue = Int.MAX_VALUE`
     * until the content it belongs to has been measured, so a detent taken while the progress line is up
     * would move Home to an offset the list has never had.
     */
    private fun scroll(tick: Int) {
        if (_state.value.body == HomeBody.LIST) _ticks.tryEmit(tick)
    }

    /** Every key this screen swallows whatever the action — see [WheelHandler.consumes] for why the halves matter. */
    fun consumesKey(keyCode: Int): Boolean = WheelHandler.consumes(keyCode)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = handleKey(keyCode)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = consumesKey(keyCode)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean = consumesKey(keyCode)
}
