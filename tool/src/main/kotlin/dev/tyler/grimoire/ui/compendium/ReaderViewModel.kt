package dev.tyler.grimoire.ui.compendium

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.compendium.CompendiumReader
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.compendium.ReaderContent
import dev.tyler.grimoire.compendium.RefQuery
import dev.tyler.grimoire.ui.keys.WheelEvent
import dev.tyler.grimoire.ui.keys.WheelHandler
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
 * S10's view model: one record, composed and resolved once (docs/UI-SPEC.md S10).
 *
 * `onScreenShow` fires on every `onResume` too — a volume press or a wheel modal relaunches the tool —
 * so the load is guarded by [loaded] and a second show is a no-op that cannot clobber the state already
 * on screen. The guard sits at the top of [load] rather than in `onScreenShow` on purpose: `onScreenShow`
 * takes a `SimpleLightScreen`, which needs a real activity and a Main dispatcher, so no JVM test can call
 * it; ReaderViewModelTest drives [load] directly instead and the guard is exercised where it lives.
 *
 * Nothing here touches `CompendiumStore` — the reader arrives through the constructor, so the whole
 * class runs on the JVM over `FakeCompendiumDao`.
 *
 * [name] is the navigating row's own name, held as the title from the first frame so the top bar is never
 * blank: the fetch replaces it with the record's name, and a record that is not there keeps it rather than
 * putting an empty bar over "Not in the compendium."
 */
class ReaderViewModel(
    private val reader: CompendiumReader,
    private val kind: Kind,
    private val key: String,
    private val name: String = "",
) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(ReaderUiState(title = name))
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _ticks = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** One wheel detent: -1 toward the top of the phone (key 317), +1 toward the bottom (318). */
    val ticks: Flow<Int> = _ticks.asSharedFlow()

    private var loaded = false

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch { load() }
    }

    /**
     * Fetch, compose and resolve — idempotent, so a relaunch's second `onScreenShow` returns at once.
     * The composition runs on [Dispatchers.Default]: `ReaderContent.of` parses the whole record, and the
     * largest of them (reading-a-stat-block, 27 KB) is too much Markdown for the main thread.
     */
    internal suspend fun load() {
        if (loaded) return
        loaded = true
        // Both the decode of the `json` column and the Markdown parse happen inside the one dispatch —
        // reading-a-stat-block is 27 KB of each, far too much for the frame the navigation lands on.
        val composed = withContext(Dispatchers.Default) {
            reader.get(kind, key)?.let { it.name to ReaderContent.of(kind, it) }
        }
        if (composed == null) {
            _state.value = ReaderUiState(title = name, loading = false, missing = true)
            return
        }
        val (title, doc) = composed
        val links = doc.links.mapNotNull { section ->
            val refs = resolve(section.query)
            if (refs.isEmpty()) null else ReaderLink(section.label, refs)
        }
        _state.value = ReaderUiState(title = title, blocks = doc.blocks, links = links, loading = false)
    }

    /** A link section's deferred query, run through the reader; [RefQuery.Keys] is capped at the reader's bound. */
    private suspend fun resolve(query: RefQuery) = when (query) {
        // `refs` answers in sortName order; the UI-SPEC's footer keeps the composition's order instead —
        // conditions first-occurrence in the prose, a race's traits and an item's variants as written.
        is RefQuery.Keys -> {
            val wanted = query.keys.take(CompendiumReader.MAX_KEYS)
            val byKey = reader.refs(query.kind, wanted).associateBy { it.key }
            wanted.mapNotNull { byKey[it] }
        }
        is RefQuery.SubclassesOf -> reader.subclassesOf(query.classKey)
        is RefQuery.ClassFeatures -> reader.classFeatures(query.classKey, MAX_CLASS_LEVEL)
        is RefQuery.SubclassFeatures -> reader.subclassFeatures(query.subclassKey)
        is RefQuery.SectionsOf -> reader.children(Kind.RULE_SECTIONS, query.ruleKey)
        is RefQuery.Chapter -> listOfNotNull(reader.chapterOfSection(query.sectionKey))
    }

    /**
     * Wheel turns scroll the body; the press is consumed as a no-op because S10 has no primary action —
     * an unconsumed wheel event reaches LightOS, which relaunches the tool mid-reading. Everything else
     * (volume, camera) stays unconsumed. See `WheelHandler`'s KDoc for the repo-wide convention.
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
        /** Class features are listed to the top of the SRD's progression. */
        const val MAX_CLASS_LEVEL = 20
    }
}
