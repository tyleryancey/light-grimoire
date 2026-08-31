package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.compendium.FakeCompendiumDao
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.compendium.Search
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * S13.4's result model over the real bundle (docs/UI-SPEC.md S13.4). `CompendiumReader.search` has already
 * ranked, deduped, cut at [Search.LIMIT] and grouped the hits — SearchTest pins that over the same assets —
 * so what is pinned here is the screen's half: the kind-group headers cut into the flat list, the one row
 * that carries a disambiguator, and the re-`FIND` that re-queries in place.
 *
 * The tests drive the internal `load()`/`show()` directly; `setQuery` is driven through the constructor's
 * scope seam (`Dispatchers.Unconfined`), because `viewModelScope` dispatches on `Dispatchers.Main`, which does
 * not exist off-device.
 */
class SearchResultsViewModelTest {
    private companion object {
        /** Every header S13.4 can draw, in the order the results are grouped in: the nine hub rows, then the
         * six lookup kinds that have no hub row of their own. */
        val HEADER_ORDER: List<String> = Kind.entries.map { SearchResultsViewModel.headerLabel(it) }.distinct()

        /** A query no record in the bundle can answer — two characters, so neither query is skipped. */
        const val NONSENSE = "qzqzqz"
    }

    private fun vm(query: String, dao: FakeCompendiumDao = Bundle.dao(), driven: Boolean = false) =
        SearchResultsViewModel(
            Bundle.reader(dao),
            query,
            if (driven) CoroutineScope(Dispatchers.Unconfined) else null,
        )

    private fun loaded(query: String): SearchResultsUiState = runBlocking {
        vm(query).let {
            it.load()
            it.state.value
        }
    }

    private fun List<ListRow>.headers() = filterIsInstance<ListRow.Header>().map { it.label }

    private fun List<ListRow>.entries() = filterIsInstance<ListRow.Entry>()

    /** The rows under each header, in order. */
    private fun List<ListRow>.sections(): List<Pair<String, List<ListRow.Entry>>> {
        val out = ArrayList<Pair<String, MutableList<ListRow.Entry>>>()
        for (row in this) when (row) {
            is ListRow.Header -> out += row.label to ArrayList()
            is ListRow.Entry -> out.last().second += row
        }
        return out.map { it.first to it.second.toList() }
    }

    // ---- the ranking, through the screen's own model ----------------------------------------------------------

    @Test
    fun fireOpensOnFireball() {
        val state = loaded("fire")
        assertFalse(state.loading, "the wait ends with the rows")
        assertFalse(state.empty, "and there are matches")
        assertEquals("fire", state.query, "the query is kept for the re-FIND")
        assertEquals(ListRow.Header("SPELLS"), state.rows.first(), "spells lead: they are first in S13 order")
        assertEquals("Fireball", state.rows.entries().first().ref.name, "and Fireball leads the spells")
    }

    @Test
    fun aBroadQueryIsCutAtTheSearchLimit() {
        val state = loaded("fire")
        assertEquals(Search.LIMIT, state.rows.entries().size, "the bundle has far more 'fire' text than this")
        assertEquals("RESULTS (${Search.LIMIT})", state.title, "the bar counts hits, not the headers between them")
    }

    @Test
    fun headersRunInSpecOrderAndEveryOneOpensABlockOfItsOwn() {
        for (query in listOf("fire", "shield", "rage", "dragon")) {
            val rows = loaded(query).rows
            val headers = rows.headers()
            assertTrue(headers.isNotEmpty(), "'$query' matched something")
            assertEquals(headers.distinct(), headers, "'$query': a group's hits are one block, so no header repeats")
            assertEquals(HEADER_ORDER.filter { it in headers }, headers, "'$query': the blocks run in S13 order")
            for ((header, entries) in rows.sections()) {
                assertTrue(entries.isNotEmpty(), "'$query': the $header header is followed by at least one hit")
                val labels = entries.map { entry ->
                    SearchResultsViewModel.headerLabel(Kind.entries.single { it.id == entry.ref.kind })
                }
                assertEquals(List(entries.size) { header }, labels, "'$query': every hit under $header belongs to it")
            }
        }
    }

    @Test
    fun aLookupKindIsGroupedAfterTheNineHubRows() {
        val rows = loaded("fire").rows
        // "Fire" is the damage type, and the shortest exact name match in the whole bundle — the ranking puts it
        // first of the name hits and the grouping still sends it to the bottom, because it has no hub row.
        assertEquals("Damage types", rows.headers().last(), "the lookup kind's own name heads the last block")
        assertEquals("Fire", rows.entries().last().ref.name, "and the exact match sits under it, last of all")
    }

    // ---- the disambiguator ------------------------------------------------------------------------------------

    @Test
    fun aFeatureHitCarriesItsClassAndLevel() {
        val rows = loaded("rage").rows
        val sections = rows.sections()
        val features = sections.single { it.first == "CLASSES & FEATURES" }.second
        val rage = features.single { it.ref.key == "rage" }
        assertEquals("Rage", rage.ref.name, "the barbarian's own feature")
        assertEquals("Barbarian 1", rage.detail, "which the wireframe disambiguates by class and level")
        assertTrue(
            features.filter { it.ref.kind == Kind.FEATURES.id }.all { !it.detail.isNullOrBlank() },
            "every feature under the header says whose it is",
        )
        assertTrue(
            rows.entries().filter { it.ref.kind != Kind.FEATURES.id }.all { it.detail == null },
            "and no other row repeats a kind the header above it already names",
        )
    }

    // ---- nothing found ------------------------------------------------------------------------------------------

    @Test
    fun aQueryNothingAnswersIsEmptyRatherThanBlank() {
        val state = loaded(NONSENSE)
        assertTrue(state.empty, "the screen has its one 'No matches.' line to draw")
        assertEquals(emptyList(), state.rows, "and no header stands over nothing")
        assertFalse(state.loading, "the wait is over either way")
        assertEquals("RESULTS (0)", state.title, "the bar counts nothing found")
    }

    // ---- the re-FIND --------------------------------------------------------------------------------------------

    @Test
    fun aSecondFindReplacesTheResultsInPlace() = runBlocking {
        val model = vm("fire", driven = true)
        model.load()
        val first = model.state.value
        model.setQuery("rage")
        val second = model.state.value
        assertEquals("rage", second.query, "the new query replaces the old one on the same screen")
        assertFalse(second.loading, "and its results are already in")
        assertNotEquals(first.rows, second.rows, "the rows are the new query's")
        assertEquals("Rage", second.rows.entries().first { it.ref.key == "rage" }.ref.name, "which found the feature")
        assertTrue(second.rows.entries().none { it.ref.key == "fireball" }, "and dropped the old query's hits")
    }

    @Test
    fun theReShowThatPrecedesAReFindCannotUndoIt() = runBlocking {
        // `LightActivity.goBack` shows the previous screen (→ load) *before* it delivers the popped screen's
        // result (→ setQuery), so a re-FIND is always framed by a second show; neither may re-run "fire".
        val model = vm("fire", driven = true)
        model.load()
        model.setQuery("rage")
        model.load()
        assertEquals("rage", model.state.value.query, "the guarded load is a no-op once the screen has loaded")
        assertTrue(model.state.value.rows.entries().none { it.ref.key == "fireball" }, "the old query stays gone")
    }

    @Test
    fun aReQueryClearsTheEmptyLineBeforeTheNextSearch() = runBlocking {
        val model = vm(NONSENSE, driven = true)
        model.load()
        assertTrue(model.state.value.empty, "the first query found nothing")
        model.setQuery("fire")
        assertFalse(model.state.value.empty, "and 'No matches.' does not linger over the next one")
        assertEquals(Search.LIMIT, model.state.value.rows.entries().size, "which has its own full list")
    }

    // ---- keys ----------------------------------------------------------------------------------------------------

    @Test
    fun wheelTurnsEmitSignedTicksAndThePressIsConsumedAsANoOp() = runBlocking {
        val model = vm("fire")
        model.load()
        val before = model.state.value
        val seen = ArrayList<Int>()
        val collector = launch { model.ticks.collect { seen += it } }
        yield()
        assertTrue(model.handleKey(317), "a turn toward the top of the phone is consumed")
        assertTrue(model.handleKey(318), "a turn toward the bottom is consumed")
        assertTrue(model.handleKey(319), "the press is consumed so LightOS never relaunches the tool")
        yield()
        assertEquals(listOf(-1, 1), seen, "317 scrolls back, 318 scrolls on, the press emits nothing")
        assertEquals(before, model.state.value, "no wheel event changes the results")
        collector.cancel()
    }

    @Test
    fun theReleaseHalfOfEveryDetentIsSwallowedAndNonWheelKeysAreNot() {
        val model = vm("fire")
        for (keyCode in listOf(317, 318, 319)) {
            assertTrue(model.consumesKey(keyCode), "the release of $keyCode is consumed")
        }
        for (keyCode in listOf(24, 25, 80, 4)) {
            assertFalse(model.consumesKey(keyCode), "key $keyCode is still LightOS's")
            assertFalse(model.handleKey(keyCode), "and this screen does not act on it")
        }
    }
}
