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
 * ranked, deduped, cut both tiers together at [Search.LIMIT] and grouped each by kind — SearchTest and
 * CompendiumReaderTest pin that over the same assets — so what is pinned here is the screen's half: the
 * kind-group headers cut into the named tier, the one "Also mentioned" header over the flat mention tier, the
 * rows that carry a right detail, and the re-`FIND` that re-queries in place.
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

    private fun ListRow.isMentionsHeader() = this == ListRow.Header(SearchResultsViewModel.MENTIONS_HEADER)

    /** The rows above the one "Also mentioned" header: the named tier, under its kind-group headers. */
    private fun List<ListRow>.named(): List<ListRow> = takeWhile { !it.isMentionsHeader() }

    /** The rows below it: the mention tier, flat. */
    private fun List<ListRow>.mentions(): List<ListRow> = dropWhile { !it.isMentionsHeader() }.drop(1)

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
        // The cap is on hits; the headers standing over them are extra rows. Six kind-group headers over the
        // named tier and the one "Also mentioned" over the rest.
        assertEquals(7, state.rows.headers().size, "the headers 'fire' draws")
        assertEquals(Search.LIMIT + 7, state.rows.size, "so the list is longer than the cap by exactly its headers")
    }

    @Test
    fun headersRunInSpecOrderAndEveryOneOpensABlockOfItsOwn() {
        for (query in listOf("fire", "shield", "rage", "dragon")) {
            val rows = loaded(query).rows.named()
            val headers = rows.headers()
            assertTrue(headers.isNotEmpty(), "'$query' matched a name")
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
        val named = loaded("fire").rows.named()
        // "Fire" is the damage type, and the shortest exact name match in the whole bundle — the ranking puts it
        // first of the name hits and the grouping still sends it to the bottom, because it has no hub row.
        assertEquals("Damage types", named.headers().last(), "the lookup kind's own name heads the last named block")
        assertEquals("Fire", named.entries().last().ref.name, "and the exact match sits under it, last of the names")
    }

    // ---- the disambiguator ------------------------------------------------------------------------------------

    @Test
    fun aFeatureHitCarriesItsClassAndLevel() {
        val rows = loaded("rage").rows.named()
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

    // ---- the mention tier ---------------------------------------------------------------------------------------

    @Test
    fun theMentionsSitUnderOneHeaderOfTheirOwnAndNoTwoOfThemReadAlike() {
        for (query in listOf("fire", "shield")) {
            val rows = loaded(query).rows
            assertEquals(
                1,
                rows.count { it.isMentionsHeader() },
                "'$query': one 'Also mentioned' header, however many kinds are under it",
            )
            val mentions = rows.mentions()
            assertTrue(mentions.isNotEmpty(), "'$query': the bundle mentions it in bodies the name query missed")
            assertEquals(emptyList(), mentions.headers(), "'$query': the mentions are flat — no second level of header")
            // The property the whole tier turns on: with one header over every kind, a row's name and its
            // detail are all the player has to choose by, so no two rows may draw the same pair.
            val drawn = mentions.entries().map { it.ref.name to it.detail }
            assertEquals(drawn.distinct(), drawn, "'$query': no two mentions read alike")
            for (entry in mentions.entries()) {
                val kind = Kind.entries.single { it.id == entry.ref.kind }
                if (kind == Kind.FEATURES) {
                    assertEquals(
                        RefDetail.of(entry.ref, DetailStyle.CLASS_LEVEL),
                        entry.detail,
                        "'$query': ${entry.ref.key} says whose feature it is — every feature reads 'Class feature'",
                    )
                } else {
                    assertEquals(
                        RefDetail.label(kind),
                        entry.detail,
                        "'$query': ${entry.ref.key} says it is a ${kind.id}, since no header above it does",
                    )
                }
            }
            val named = rows.named().entries().map { it.ref.kind to it.ref.key }.toSet()
            assertTrue(
                mentions.entries().none { (it.ref.kind to it.ref.key) in named },
                "'$query': nothing is both named and merely mentioned",
            )
        }
    }

    /**
     * The one exception to "a mention says what kind it is" (M2 step 6b). Three of the seven mentions of
     * "rage" are features *named* "Path feature" — the Berserker's 6th-, 10th- and 14th-level slots — so a
     * tier that drew the kind label on every row drew three identical "Path feature  Class feature" rows and
     * gave the player no way to pick one. Features are the only kind whose names collide: 31 of them in this
     * bundle, "Ability Score Improvement" 63 times.
     *
     * The rows below are measured over the bundle these assets pin, so a regenerated bundle is *supposed* to
     * fail this test: re-measure the details and update them, never loosen the assertion.
     */
    @Test
    fun aFeatureMentionKeepsTheClassAndLevelThatTellsItFromTheNextOne() {
        val mentions = loaded("rage").rows.mentions().entries()
        assertEquals(
            listOf(
                "Berserker" to "Subclass",
                "Feral Instinct" to "Barbarian 7",
                "Frenzy" to "Barbarian 3",
                "Primal Path" to "Barbarian 3",
                "Path feature" to "Barbarian 6",
                // Barbarian 14's Path feature is the sixth feature mention, one past MENTIONS_PER_KIND.
                "Path feature" to "Barbarian 10",
            ),
            mentions.map { it.ref.name to it.detail },
            "every mention of 'rage', each told from the next",
        )
    }

    @Test
    fun aQueryEveryHitOfWhichIsANameMatchDrawsNoMentionsHeader() {
        // No query over the whole bundle answers this: "Monsters: Reading a Stat Block" mentions nearly every
        // creature, so a creature name always drags a mention along. Over the spells alone, "vicious mockery"
        // matches one name and no other body.
        val spellsOnly = FakeCompendiumDao(Bundle.built.filter { it.record.kind == Kind.SPELLS.id })
        val state = runBlocking {
            SearchResultsViewModel(Bundle.reader(spellsOnly), "vicious mockery").let {
                it.load()
                it.state.value
            }
        }
        assertEquals(
            listOf(ListRow.Header("SPELLS"), ListRow.Entry(state.rows.entries().single().ref)),
            state.rows,
            "one header, one hit, and nothing to mention",
        )
        assertEquals("vicious-mockery", state.rows.entries().single().ref.key, "the spell itself")
        assertEquals("RESULTS (1)", state.title, "one hit")
    }

    /**
     * The two-tier regression at row level (M2 step 6b). Blended into one kind-grouped list, S13.4 drew the
     * creature **Fire Giant** — a name match — at row 53 of 57 and the damage type **Fire** dead last at 57,
     * behind 24 body-only spell mentions; the equipment **Shield** was row 23 of 51, a third screenful down.
     *
     * The rows below are measured over the bundle these assets pin, so a regenerated bundle is *supposed* to
     * fail this test: re-measure the positions and update them, never loosen the assertion.
     */
    @Test
    fun theNameMatchesTheBlendedListBuriedNowSitInTheFirstScreenfulsOfTheirOwnTier() {
        val fire = loaded("fire").rows
        assertEquals(57, fire.size, "'fire' draws 50 hits and 7 headers")
        assertEquals(27, 1 + fire.indexOfFirst { it is ListRow.Entry && it.ref.key == "fire-giant" }, "Fire Giant, row 27 of 57 (was 53)")
        assertEquals(31, 1 + fire.indexOfFirst { it is ListRow.Entry && it.ref.kind == Kind.DAMAGE_TYPES.id }, "the Fire damage type, row 31 (was 57, last)")
        assertEquals(32, 1 + fire.indexOfFirst { it.isMentionsHeader() }, "and every row below 32 is a body mention")
        val shield = loaded("shield").rows
        assertEquals(39, shield.size, "'shield' draws 33 hits and 6 headers")
        assertEquals(
            6,
            1 + shield.indexOfFirst { it is ListRow.Entry && it.ref.kind == Kind.EQUIPMENT.id && it.ref.key == "shield" },
            "the equipment Shield, row 6 of 39 (was 23 of 51 when the tiers were blended)",
        )
        assertEquals(18, 1 + shield.indexOfFirst { it.isMentionsHeader() }, "the mentions start below the twelve name matches")
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
