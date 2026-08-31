package dev.tyler.grimoire.ui.compendium

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.compendium.CompendiumReader
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.compendium.KindGroup
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
 * S13's view model: the nine hub rows and the counts beside five of them (docs/UI-SPEC.md S13).
 *
 * The rows are [KindGroup] itself minus LOOKUP, so "the wireframe's order" is the enum's order rather than a
 * list that can drift from it, and every count comes from `countsByKind()` at runtime — never a literal, which
 * is the whole point of D13: the bundle's numbers live in the bundle.
 *
 * Like S13.1's, the load is guarded so a relaunch's second `onScreenShow` cannot rebuild the hub under the
 * reader's finger, and the guard sits in the internal [load] the tests drive — `onScreenShow` needs a real
 * activity and a Main dispatcher, neither of which a JVM test has.
 */
class CompendiumHubViewModel(private val reader: CompendiumReader) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(CompendiumHubUiState())
    val state: StateFlow<CompendiumHubUiState> = _state.asStateFlow()

    private val _ticks = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** One wheel detent: -1 toward the top of the phone (key 317), +1 toward the bottom (318). */
    val ticks: Flow<Int> = _ticks.asSharedFlow()

    private var loaded = false

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch { load() }
    }

    /** Builds the nine rows and their counts once; a second call is a no-op. */
    internal suspend fun load() {
        if (loaded) return
        loaded = true
        // One grouped COUNT over the whole table, read as a map: five rows want a number and the query is the
        // same query whichever of them ask.
        val counts = reader.countsByKind().associate { it.kind to it.n }
        val rows = HUB_GROUPS.map { group ->
            HubRow(
                group = group,
                label = GroupLabel.of(group),
                count = countedKind(group)?.let { counts[it.id] },
            )
        }
        _state.value = CompendiumHubUiState(rows = rows, loading = false)
    }

    /**
     * Wheel turns scroll the hub; the press is consumed as a no-op because S13 has no primary action — an
     * unconsumed wheel event reaches LightOS, which relaunches the tool. Volume and camera stay unconsumed.
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
        /** The wireframe's nine rows: every group but LOOKUP, in `KindGroup` order, which is S13's order. */
        val HUB_GROUPS: List<KindGroup> = KindGroup.entries.filter { it != KindGroup.LOOKUP }

        /**
         * The one kind a hub row counts, or null where the spec shows no count at all.
         *
         * Four groups stay uncounted because each mixes more than one kind and no single number describes the
         * row (S13): RULES is chapters over sections, CLASSES & FEATURES is classes, subclasses and 407
         * features, RACES is races, subraces and traits, BACKGROUNDS & FEATS is one of each.
         *
         * The counted five are one kind apiece, not a sum over the group — EQUIPMENT is the case that proves
         * it: the hub reads 237, the equipment records alone, while the group also holds the 11 weapon
         * properties the S13.1 list draws under their own section ("not a discrepancy", S13.1).
         */
        fun countedKind(group: KindGroup): Kind? = when (group) {
            KindGroup.SPELLS -> Kind.SPELLS
            KindGroup.CONDITIONS -> Kind.CONDITIONS
            KindGroup.EQUIPMENT -> Kind.EQUIPMENT
            KindGroup.MAGIC_ITEMS -> Kind.MAGIC_ITEMS
            KindGroup.CREATURES -> Kind.CREATURES
            KindGroup.RULES,
            KindGroup.CLASSES_AND_FEATURES,
            KindGroup.RACES,
            KindGroup.BACKGROUNDS_AND_FEATS,
            KindGroup.LOOKUP,
            -> null
        }
    }
}
