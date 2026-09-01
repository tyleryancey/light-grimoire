package dev.tyler.grimoire.ui.sheet

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.compendium.CompendiumReader
import dev.tyler.grimoire.data.CharacterRepository
import dev.tyler.grimoire.rules.ArmorStats
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Derive
import dev.tyler.grimoire.rules.Derived
import dev.tyler.grimoire.ui.common.CharacterViewModel
import dev.tyler.grimoire.ui.keys.WheelEvent
import dev.tyler.grimoire.ui.keys.WheelHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S1's view model: one character, its `Derived`, and the single mutation the hub itself makes — the
 * inspiration star (docs/UI-SPEC.md S1).
 *
 * **The load is deliberately ungated, unlike S10's reader and S13.1's group list.** Both of those refuse a
 * second `onScreenShow` so a relaunch cannot rebuild a list under the reader's finger; S1 must do the
 * opposite. Every sheet screen is pushed from here and pops straight back — S3 changes hit points, S7
 * conditions, S9 coin — and `onScreenHide` has already flushed the write by the time this screen is shown
 * again. A `loaded` guard would draw the character as it was before the player went to change it, which is
 * the one failure a hub cannot afford.
 *
 * **The armor table is fetched once and kept.** `Derive.derive` needs it (AC is `base + DEX` off a
 * compendium row), it is a suspend query against Room, and it cannot change while the tool runs: the
 * compendium is imported once from bundled assets and is sticky-Ready thereafter. Re-reading it on every
 * show would put an indexed query in front of every BACK from S3.
 *
 * [name] is the row's own name, held as the title from the first frame so the top bar is never blank — the
 * same contract `ReaderViewModel` states, and here the wait it covers is the longest in the tool: the bar
 * would otherwise stay empty until the character, the armor table *and* the whole derivation had landed.
 * [load] replaces it with the stored name, and a character that is not there keeps it rather than putting an
 * empty bar over "No such character."
 *
 * [scope] is injected for the reason `HpViewModel`'s is: `viewModelScope` dispatches on Main, which a JVM
 * test has no dispatcher for, and `by lazy` means a test never touches it at all.
 */
class SheetViewModel(
    characterId: String,
    repo: CharacterRepository,
    private val reader: CompendiumReader,
    name: String = "",
    scope: CoroutineScope? = null,
) : CharacterViewModel<Unit>(repo, characterId) {
    /** The bar's text before anything is loaded, and all the "No such character." branch ever has. */
    private val initialTitle = SheetText.title(name)

    private val _state = MutableStateFlow(SheetUiState(title = initialTitle))
    val state: StateFlow<SheetUiState> = _state.asStateFlow()

    private val _ticks = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** One wheel detent: -1 toward the top of the phone (key 317), +1 toward the bottom (318). */
    val ticks: Flow<Int> = _ticks.asSharedFlow()

    /** `by lazy` so `viewModelScope` is never touched in a test, which has no Main dispatcher to build it on. */
    private val loadScope: CoroutineScope by lazy { scope ?: viewModelScope }

    private var character: Character? = null

    private var derived: Derived? = null

    /** Read once from the compendium; see the class KDoc for why it is never re-read. */
    private var armor: Map<String, ArmorStats>? = null

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        loadScope.launch { load() }
    }

    /**
     * Read the character, derive it, draw it — every time, see the class KDoc.
     *
     * The state is not put back into [SheetUiState.loading] on a reload: the header and the rows are already
     * on screen and the values that are about to replace them are the same shape, so blanking the screen for
     * the length of a Room read would be a flicker on every BACK. Only the very first load shows the quiet
     * line, and it holds until the derivation is done too — the stat line cannot be drawn without the armor
     * table, and a header that filled in one line at a time would move under the player's eye.
     *
     * `Derive.derive` runs on [Dispatchers.Default]: it is a whole sheet's arithmetic (18 skills, every save,
     * the slot tables, each attack) and it has no business on the frame thread even though it is quick.
     *
     * Driven directly by the gate: `onScreenShow` takes a `SimpleLightScreen`, which needs a real activity,
     * so the tests call this instead — the same seam `HpViewModel` and `ReaderViewModel` use.
     */
    internal suspend fun load() {
        val loaded = repo.load(characterId)
        if (loaded == null) {
            character = null
            derived = null
            _state.value = SheetUiState(title = initialTitle, loading = false, missing = true)
            return
        }
        val table = armor ?: reader.armorTable().also { armor = it }
        val computed = withContext(Dispatchers.Default) { Derive.derive(loaded, table) }
        character = loaded
        derived = computed
        render()
    }

    /**
     * The `★` beside the identity line: 2014 inspiration, held or not.
     *
     * A view-model `copy()` and a save, **not** a `Ledger` event: `Ledger`'s vocabulary is the mutations with
     * rules semantics, and `Ledger.kt:12-13` names inspiration among the trivial setters a screen applies
     * itself (with conditions, currency and what is equipped).
     *
     * The cached [derived] is reused rather than recomputed. Nothing in `Derived` reads `inspiration` — it is
     * a boolean the player spends at the table, not an input to any number on the sheet — and
     * `SheetViewModelTest` asserts the derived stat line is byte-identical across a toggle, so a future field
     * that did depend on it would fail there rather than on the phone.
     */
    fun toggleInspiration() {
        val current = character ?: return
        val next = current.copy(inspiration = !current.inspiration)
        character = next
        repo.save(next)
        render()
    }

    private fun render() {
        val c = character ?: return
        val d = derived ?: return
        _state.value = SheetUiState(
            title = SheetText.title(c),
            identity = SheetText.identity(c),
            stats = SheetText.statLine(d, c.speed),
            inspiration = c.inspiration,
            rows = SheetText.sheetRows(c, d),
            loading = false,
            missing = false,
        )
    }

    /**
     * The wheel. Turns scroll the list — three rows per detent, `WheelScrollEffect`'s default and the figure
     * S1 states — and the press is consumed as a no-op: every row on this screen navigates on a tap, none
     * carries a wheel-selected focus the way S6's counters do, so there is no primary action to press.
     *
     * Consuming the press anyway is the point. An unconsumed wheel event reaches LightOS, which foregrounds
     * itself and relaunches the tool — losing the sheet mid-turn is a far worse answer than doing nothing.
     * The same holds while the character is still loading and on the missing-id branch, both of which reach
     * these same two lines: there is nothing to scroll, and the turn is still swallowed.
     */
    fun handleKey(keyCode: Int): Boolean = when (WheelHandler.of(keyCode)) {
        WheelEvent.UP -> {
            _ticks.tryEmit(-1)
            true
        }
        WheelEvent.DOWN -> {
            _ticks.tryEmit(1)
            true
        }
        WheelEvent.PRESS -> true
        null -> false
    }

    /** Every key this screen swallows whatever the action — see [WheelHandler.consumes] for why the halves matter. */
    fun consumesKey(keyCode: Int): Boolean = WheelHandler.consumes(keyCode)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = handleKey(keyCode)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = consumesKey(keyCode)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean = consumesKey(keyCode)
}
