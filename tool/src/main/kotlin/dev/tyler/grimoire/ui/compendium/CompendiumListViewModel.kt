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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * S13.1's view model: one hub row's [KindGroup] turned into the flat list of [ListRow]s that group shows
 * (docs/UI-SPEC.md S13.1's per-group table is the contract — the sections, their order, the query behind each
 * and the right detail all come from there).
 *
 * Every query is kind-scoped and either takes a limit bounded by the bundle's own count or is finite by the
 * bundle itself (`children`, `byCategory`, `bySubcategory` return one chapter's sections, one category's
 * equipment, the 239 base magic items).
 *
 * Like the reader's, the load is guarded so a relaunch's second `onScreenShow` cannot rebuild the list under
 * the reader's finger, and the guard sits in the internal [load] the tests drive — `onScreenShow` needs a real
 * activity and a Main dispatcher, neither of which a JVM test has.
 */
class CompendiumListViewModel(
    private val reader: CompendiumReader,
    private val group: KindGroup,
) : LightViewModel<Unit>() {
    init {
        require(group != KindGroup.SPELLS) { SPELLS_ELSEWHERE }
    }

    private val _state = MutableStateFlow(CompendiumListUiState(title = titleOf(group)))
    val state: StateFlow<CompendiumListUiState> = _state.asStateFlow()

    private val _ticks = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** One wheel detent: -1 toward the top of the phone (key 317), +1 toward the bottom (318). */
    val ticks: Flow<Int> = _ticks.asSharedFlow()

    private var loaded = false

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch { load() }
    }

    /** Builds the group's whole section model once; a second call is a no-op. */
    internal suspend fun load() {
        if (loaded) return
        loaded = true
        _state.value = _state.value.copy(rows = rowsOf(group), loading = false)
    }

    /**
     * The S13.1 table, group by group. Headers carry their label in sentence case — `SectionHeaderRow`
     * uppercases what it draws, so the model stays readable and the screen owns the typography.
     */
    private suspend fun rowsOf(group: KindGroup): List<ListRow> = when (group) {
        // The hub sends SPELLS to S13.2 instead; the constructor already refused, and this branch keeps the
        // `when` exhaustive without inventing an empty list for a routing mistake.
        KindGroup.SPELLS -> throw IllegalArgumentException(SPELLS_ELSEWHERE)

        KindGroup.CONDITIONS -> entries(reader.listInOrder(Kind.CONDITIONS, CONDITIONS_LIMIT))

        // A chapter is a header, not a row: six of the nine chapters' text is only their own title, and a
        // chapter's page is reached from FIND or from a section's CHAPTER link instead (S13.1, D10).
        KindGroup.RULES -> buildList {
            for (chapter in reader.listInOrder(Kind.RULES, RULES_LIMIT)) {
                add(ListRow.Header(chapter.name))
                addAll(entries(reader.children(Kind.RULE_SECTIONS, chapter.key)))
            }
        }

        // Features have no row here — 63 duplicate "Ability Score Improvement" rows would be noise; they are
        // reached from a class or subclass reader's footer and from FIND.
        KindGroup.CLASSES_AND_FEATURES -> section("Classes", reader.listInOrder(Kind.CLASSES, CLASSES_LIMIT)) +
            section("Subclasses", reader.listByName(Kind.SUBCLASSES, SUBCLASSES_LIMIT))

        // Traits, likewise, hang off the race readers' footers.
        KindGroup.RACES -> section("Races", reader.listInOrder(Kind.RACES, RACES_LIMIT)) +
            section("Subraces", reader.listByName(Kind.SUBRACES, SUBRACES_LIMIT))

        // Two one-row sections: SRD 5.1 ships exactly one background and one feat.
        KindGroup.BACKGROUNDS_AND_FEATS -> section("Backgrounds", reader.listInOrder(Kind.BACKGROUNDS, BACKGROUNDS_LIMIT)) +
            section("Feats", reader.listInOrder(Kind.FEATS, FEATS_LIMIT))

        KindGroup.EQUIPMENT -> buildList {
            // `categoriesOf` returns the categories alphabetically, which opens the screen on 116 rows of
            // adventuring gear and buries weapons at row 204. At a table the combat gear is what gets looked
            // up mid-turn, so the sections lead with it; anything the bundle grows that is not listed here
            // keeps its alphabetical place at the end.
            val categories = reader.categoriesOf(Kind.EQUIPMENT).mapNotNull { it.category }
                // `categoriesOf` groups by a nullable column; every bundled item has a category, and one
                // without could not be fetched anyway (`byCategory` matches on equality, never on null).
                .sortedBy { category ->
                    EQUIPMENT_SECTION_ORDER.indexOf(category).let { if (it == -1) EQUIPMENT_SECTION_ORDER.size else it }
                }
            for (name in categories) {
                addAll(section(Slug.humanize(name), reader.byCategory(Kind.EQUIPMENT, name)))
            }
            addAll(section("Weapon properties", reader.listByName(Kind.WEAPON_PROPERTIES, WEAPON_PROPERTIES_LIMIT)))
        }

        // The 123 variants are reached from each base item's reader footer and from FIND.
        KindGroup.MAGIC_ITEMS -> entries(reader.bySubcategory(Kind.MAGIC_ITEMS, MAGIC_ITEM_BASE), DetailStyle.RARITY)

        KindGroup.CREATURES -> entries(reader.listByName(Kind.CREATURES, CREATURES_LIMIT), DetailStyle.CR)

        // Not a hub row: the six lookup kinds are reached through FIND and reader cross-links (S13). Building
        // them anyway is harmless and beats a crash, so a future screen can borrow this one.
        KindGroup.LOOKUP -> buildList {
            for (kind in Kind.entries.filter { it.group == KindGroup.LOOKUP }) {
                addAll(section(Slug.humanize(kind.id), reader.listByName(kind, LOOKUP_LIMIT)))
            }
        }
    }

    /** A header plus its rows, or nothing at all when the section is empty — no header ever stands alone. */
    private fun section(label: String, refs: List<CompendiumRef>, style: DetailStyle = DetailStyle.NONE): List<ListRow> =
        if (refs.isEmpty()) emptyList() else listOf(ListRow.Header(label)) + entries(refs, style)

    private fun entries(refs: List<CompendiumRef>, style: DetailStyle = DetailStyle.NONE): List<ListRow> =
        refs.map { ListRow.Entry(it, RefDetail.of(it, style)) }

    /**
     * Wheel turns scroll the list; the press is consumed as a no-op because S13.1 has no primary action — an
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
        const val SPELLS_ELSEWHERE = "SPELLS is SpellLevelScreen's own list (UI-SPEC S13.2), not a generic group list"

        /** The magic-item rows that are not one of the 123 variants. */
        const val MAGIC_ITEM_BASE = "base"

        // One bound per list, each the bundle's own count (docs/UI-SPEC.md S13.1) — a limit the data cannot
        // grow past without the asset sha256s changing, which is what makes every screen finite by construction.
        const val CONDITIONS_LIMIT = 15
        const val RULES_LIMIT = 9
        const val CLASSES_LIMIT = 12
        const val SUBCLASSES_LIMIT = 12
        const val RACES_LIMIT = 9
        const val SUBRACES_LIMIT = 4
        const val BACKGROUNDS_LIMIT = 1
        const val FEATS_LIMIT = 1
        const val WEAPON_PROPERTIES_LIMIT = 12
        const val CREATURES_LIMIT = 334

        /** Equipment sections, combat gear first; a category the bundle adds later sorts after these. */
        val EQUIPMENT_SECTION_ORDER = listOf("weapon", "armor", "adventuring-gear", "tools", "mounts-and-vehicles")

        /** The largest lookup kind is proficiencies, 117 rows. */
        const val LOOKUP_LIMIT = 120

        /** The hub row's own label, already in the top bar's upper case (S13). */
        fun titleOf(group: KindGroup): String = when (group) {
            KindGroup.SPELLS -> "SPELLS"
            KindGroup.CONDITIONS -> "CONDITIONS"
            KindGroup.RULES -> "RULES"
            KindGroup.CLASSES_AND_FEATURES -> "CLASSES & FEATURES"
            KindGroup.RACES -> "RACES"
            KindGroup.BACKGROUNDS_AND_FEATS -> "BACKGROUNDS & FEATS"
            KindGroup.EQUIPMENT -> "EQUIPMENT"
            KindGroup.MAGIC_ITEMS -> "MAGIC ITEMS"
            KindGroup.CREATURES -> "CREATURES"
            KindGroup.LOOKUP -> "LOOKUP"
        }
    }
}
