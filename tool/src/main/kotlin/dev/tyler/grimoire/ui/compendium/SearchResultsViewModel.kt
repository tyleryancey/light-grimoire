package dev.tyler.grimoire.ui.compendium

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.compendium.CompendiumReader
import dev.tyler.grimoire.compendium.CompendiumRef
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.compendium.KindGroup
import dev.tyler.grimoire.ui.keys.WheelEvent
import dev.tyler.grimoire.ui.keys.WheelHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * S13.4's view model: one query's hits, grouped under kind-group headers (docs/UI-SPEC.md S13.4).
 *
 * All the ranking already happened — [CompendiumReader.search] merges the name query with the FTS query, drops
 * duplicates, cuts at `Search.LIMIT` and groups what survives in S13 order — so the only job left here is to
 * cut that flat list into sections and choose each row's disambiguator.
 *
 * [setQuery] is the re-`FIND` path: the editor pops back to *this* screen instance, which re-queries in place
 * and never pushes a second results screen. That makes the ordering inside `LightActivity.goBack` load-bearing:
 * the previous screen is shown (`notifyWillShow` → [load]) *before* the popped screen's result is delivered
 * (→ [setQuery]), so [load] must be a no-op on the second show or every re-FIND would run the old query first.
 *
 * [scope] is the seam that keeps this testable, the same shape [SpellLevelViewModel] uses: the tool leaves it
 * null and gets `viewModelScope`, and a JVM test passes its own — `viewModelScope` dispatches on
 * `Dispatchers.Main`, which does not exist off-device.
 */
class SearchResultsViewModel(
    private val reader: CompendiumReader,
    initialQuery: String,
    scope: CoroutineScope? = null,
) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(SearchResultsUiState(query = initialQuery))
    val state: StateFlow<SearchResultsUiState> = _state.asStateFlow()

    private val _ticks = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** One wheel detent: -1 toward the top of the phone (key 317), +1 toward the bottom (318). */
    val ticks: Flow<Int> = _ticks.asSharedFlow()

    /** Resolved on first use so the tool's `viewModelScope` is never touched by a test that supplied its own. */
    private val loads: CoroutineScope by lazy { scope ?: viewModelScope }

    /** The query in flight, cancelled by the next one — nothing here writes, so a cancelled read costs nothing. */
    private var running: Job? = null

    private var loaded = false

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (loaded) return
        running = loads.launch { load() }
    }

    /** Runs the query the screen was pushed with; a second call is a no-op (see the class KDoc's ordering note). */
    internal suspend fun load() {
        if (loaded) return
        loaded = true
        show(_state.value.query)
    }

    /**
     * The re-`FIND` path: replace the results in place. Marks the screen loaded, so the re-show that follows a
     * later editor round trip cannot re-run the query this one replaced.
     */
    fun setQuery(query: String) {
        loaded = true
        running?.cancel()
        running = loads.launch { show(query) }
    }

    /**
     * Search [query] and rebuild the whole state from it. The waiting state is a fresh [SearchResultsUiState],
     * not a copy: a re-query must drop the old rows and clear `empty`, or "No matches." would sit over the next
     * query's load.
     */
    internal suspend fun show(query: String) {
        _state.value = SearchResultsUiState(query = query, loading = true)
        val rows = rowsOf(reader.search(query))
        _state.value = SearchResultsUiState(query = query, rows = rows, loading = false, empty = rows.isEmpty())
    }

    /**
     * The flat ranked list cut into sections. A header opens wherever the *label* changes, not wherever the
     * kind does: S13.4 draws one "CLASSES & FEATURES" over a class, a subclass and a feature alike, and the
     * three kinds are adjacent in `Kind` declaration order — which is the order `Search.merge` groups by — so a
     * group's hits are always one contiguous run. The six LOOKUP kinds have no hub row to be named by and get a
     * header each, after the nine (S13.4).
     */
    private fun rowsOf(hits: List<CompendiumRef>): List<ListRow> = buildList {
        var header: String? = null
        for (hit in hits) {
            // `Kind.byId` throws; a row whose kind this build does not know could be neither labelled nor
            // opened, so it is left out rather than drawn as a dead end.
            val kind = Kind.entries.firstOrNull { it.id == hit.kind } ?: continue
            val label = headerLabel(kind)
            if (label != header) {
                add(ListRow.Header(label))
                header = label
            }
            add(ListRow.Entry(hit, RefDetail.of(hit, detailStyle(kind))))
        }
    }

    /**
     * Wheel turns scroll the results; the press is consumed as a no-op because S13.4 has no primary action — an
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

    internal companion object {
        /**
         * The header a kind's hits sit under: its hub row's label, or the kind's own name for the six LOOKUP
         * kinds, which have no hub row. Sentence case is fine — `SectionHeaderRow` upper-cases what it draws —
         * so this is the same shape S13.1's own section labels are in.
         */
        fun headerLabel(kind: Kind): String =
            if (kind.group == KindGroup.LOOKUP) Slug.humanize(kind.id) else GroupLabel.of(kind.group)

        /**
         * The right-hand disambiguator, which S13.4 draws for a feature and nothing else: "Rage" alone under
         * CLASSES & FEATURES could be any class's, and the wireframe reads "Rage  Barbarian 1". Every other
         * row's name is its own answer, and repeating the kind on every row under a header that already names
         * it would be noise.
         */
        fun detailStyle(kind: Kind): DetailStyle =
            if (kind == Kind.FEATURES) DetailStyle.CLASS_LEVEL else DetailStyle.NONE
    }
}
