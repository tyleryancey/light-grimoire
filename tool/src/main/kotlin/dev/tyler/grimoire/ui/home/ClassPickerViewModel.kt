package dev.tyler.grimoire.ui.home

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.compendium.CompendiumReader
import dev.tyler.grimoire.compendium.CompendiumRef
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.ui.keys.WheelEvent
import dev.tyler.grimoire.ui.keys.WheelHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The class picker's rows, and the one quiet line before them. Small enough to live beside its view model:
 * unlike `SheetUiState` it holds no derived text, only the bundle's twelve classes in the bundle's order.
 */
data class ClassPickerUiState(
    val rows: List<CompendiumRef> = emptyList(),
    val loading: Boolean = true,
)

/**
 * S12 step 2's class list, as much of it as M3 builds: the twelve SRD classes, in the compendium's own
 * order, one tap each (docs/UI-SPEC.md S12).
 *
 * **An M3 interim of S12, not a screen of its own.** The wizard asks nine questions; this build asks two,
 * and level, subclass, race, abilities, saves, skills, vitals, attacks and spells are all M4's. The picker
 * is therefore deliberately thin — the list, and the key it hands back — so that when S12 grows the rest,
 * this screen is replaced rather than adapted, and `data/NewCharacter` is the part that survives.
 *
 * **S12's `Other…` row is not here, and that is a real omission rather than an oversight.** The spec's step
 * 2 is "12 SRD + Other… which asks a name and hit die"; a custom class needs a second editor and a hit-die
 * picker, and every screen that would answer them is M4's. Until then a non-SRD class cannot be
 * transcribed — the model already carries it (`ClassEntry.custom`), so nothing about the data has to
 * change when the row arrives. **Wants ratification.**
 *
 * Twelve rows is the bundle's own count (`assets/compendium/classes.json`), the same bound
 * `CompendiumListViewModel` uses for its Classes section, so the list is finite by construction. The load
 * is guarded like the compendium lists': nothing about it can change while the tool runs, and a relaunch's
 * second `onScreenShow` must not rebuild the list under the player's finger.
 */
class ClassPickerViewModel(private val reader: CompendiumReader) : LightViewModel<String>() {
    private val _state = MutableStateFlow(ClassPickerUiState())
    val state: StateFlow<ClassPickerUiState> = _state.asStateFlow()

    private val _ticks = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** One wheel detent: -1 toward the top of the phone (key 317), +1 toward the bottom (318). */
    val ticks: Flow<Int> = _ticks.asSharedFlow()

    private var loaded = false

    override fun onScreenShow(screen: SimpleLightScreen<String>) {
        super.onScreenShow(screen)
        viewModelScope.launch { load() }
    }

    /** Reads the twelve classes once; a second call is a no-op. Driven directly by the gate. */
    internal suspend fun load() {
        if (loaded) return
        loaded = true
        _state.value = ClassPickerUiState(rows = reader.listInOrder(Kind.CLASSES, CLASSES_LIMIT), loading = false)
    }

    /**
     * Wheel turns scroll the list — twelve rows at 2.5 units is 30 against 23, so it does scroll — three
     * rows per detent, the list default. The press is consumed as a no-op: a row is chosen by tapping it,
     * and there is no wheel-carried focus here to press.
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

    private companion object {
        /** The bundle's own count of SRD classes; the data cannot grow past it without the asset sha256s changing. */
        const val CLASSES_LIMIT = 12
    }
}
