package dev.tyler.grimoire.ui.compendium

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.compendium.CompendiumReader
import dev.tyler.grimoire.ui.compendium.SpellLevelUiState.Companion.MAX_LEVEL
import dev.tyler.grimoire.ui.compendium.SpellLevelUiState.Companion.MIN_LEVEL
import dev.tyler.grimoire.ui.keys.WheelEvent
import dev.tyler.grimoire.ui.keys.WheelHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * S13.2's view model: the spells of one level, and the level itself as the thing the screen steps through
 * (docs/UI-SPEC.md S13.2). The wheel owns the step here rather than scrolling — 317 (toward the top of the
 * phone) raises the level, 318 lowers it, both clamped with no wrap — and the press is consumed as a no-op.
 *
 * [levelUp] and [levelDown] are the one entry point for that job: the wheel's `handleKey` calls them and so do
 * the stepper row's `◂`/`▸`, which exist because the emulator emits no wheel codes at all — the arrows are not
 * decoration, they are the same function reached by finger.
 *
 * [scope] is the seam that keeps this testable, the same shape `ImportGate` uses: the tool leaves it null and
 * gets `viewModelScope`, and a JVM test passes its own — `viewModelScope` dispatches on `Dispatchers.Main`,
 * which does not exist off-device.
 */
class SpellLevelViewModel(
    private val reader: CompendiumReader,
    scope: CoroutineScope? = null,
) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(SpellLevelUiState())
    val state: StateFlow<SpellLevelUiState> = _state.asStateFlow()

    /** Resolved on first use so the tool's `viewModelScope` is never touched by a test that supplied its own. */
    private val loads: CoroutineScope by lazy { scope ?: viewModelScope }

    /** The query in flight, cancelled by the next step — nothing here writes, so a cancelled read costs nothing. */
    private var running: Job? = null

    private var loaded = false

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        // The level the screen is already on: the first show loads cantrips, a relaunch's second show is a no-op.
        start(_state.value.level)
    }

    /** The `▸` arrow and wheel key 317: one level up, clamped at [MAX_LEVEL]. */
    fun levelUp() = step(1)

    /** The `◂` arrow and wheel key 318: one level down, clamped at [MIN_LEVEL]. */
    fun levelDown() = step(-1)

    private fun step(delta: Int) = start(_state.value.level + delta)

    private fun start(level: Int) {
        if (settled(level)) return
        running?.cancel()
        running = loads.launch { show(level) }
    }

    /**
     * Show [level], clamped into 0..9. A step past either end lands on the level already shown and runs no
     * query at all — the clamp is a wall, not a reload.
     */
    internal suspend fun show(level: Int) {
        if (settled(level)) return
        val target = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        loaded = true
        _state.value = SpellLevelUiState(level = target, loading = true)
        _state.value = SpellLevelUiState(level = target, spells = reader.spellsByLevel(target), loading = false)
    }

    /** True when [level] clamps onto the level already loaded, so there is nothing to fetch. */
    private fun settled(level: Int): Boolean = loaded && level.coerceIn(MIN_LEVEL, MAX_LEVEL) == _state.value.level

    /**
     * The wheel's job on this screen is the level, not the scroll (S13.2's wheel table); the press is consumed
     * as a no-op because the screen has no primary action, and an unconsumed wheel event would send LightOS to
     * the foreground. Volume and camera keys stay unconsumed.
     */
    fun handleKey(keyCode: Int): Boolean = when (WheelHandler.of(keyCode)) {
        WheelEvent.UP -> {
            levelUp()
            true
        }
        WheelEvent.DOWN -> {
            levelDown()
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
