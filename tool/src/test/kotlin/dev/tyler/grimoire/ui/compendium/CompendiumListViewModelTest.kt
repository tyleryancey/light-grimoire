package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.compendium.KindGroup
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S13.1's section model over the real bundle (docs/UI-SPEC.md S13.1's per-group table). Every count below was
 * measured against the sha256-pinned assets, not copied from the spec's prose: 15 conditions, 9 chapters over
 * 40 sections, 12 classes and 12 subclasses, 9 races and 4 subraces, one background and one feat, 237
 * equipment rows in 5 categories plus 11 weapon properties, 239 base magic items, 334 creatures.
 *
 * The tests drive the internal `load()` directly — `onScreenShow` needs a real activity and a Main dispatcher
 * that a JVM test has neither of — which is also where the idempotence guard lives.
 */
class CompendiumListViewModelTest {
    private fun vm(group: KindGroup) = CompendiumListViewModel(Bundle.reader(), group)

    private fun List<ListRow>.headers() = filterIsInstance<ListRow.Header>().map { it.label }

    private fun List<ListRow>.entries() = filterIsInstance<ListRow.Entry>()

    /** The rows under each header, in order — the shape every sectioned group is asserted through. */
    private fun List<ListRow>.sections(): List<Pair<String, List<ListRow.Entry>>> {
        val out = ArrayList<Pair<String, MutableList<ListRow.Entry>>>()
        for (row in this) when (row) {
            is ListRow.Header -> out += row.label to ArrayList()
            is ListRow.Entry -> out.last().second += row
        }
        return out.map { it.first to it.second.toList() }
    }

    private fun loadedRows(group: KindGroup): List<ListRow> = runBlocking {
        vm(group).let {
            it.load()
            it.state.value.rows
        }
    }

    // ---- the nine hub rows -----------------------------------------------------------------------------------

    @Test
    fun conditionsAreOneFlatBoundedList() = runBlocking {
        val model = vm(KindGroup.CONDITIONS)
        assertEquals("CONDITIONS", model.state.value.title, "the hub row's own label titles the bar")
        assertTrue(model.state.value.loading, "the list starts loading")
        model.load()
        val state = model.state.value
        assertFalse(state.loading, "the wait ends with the rows")
        assertEquals(emptyList(), state.rows.headers(), "a flat list has no sections")
        assertEquals(15, state.rows.size, "the bundle's 15 conditions")
        assertEquals("Blinded", state.rows.entries().first().ref.name, "in the SRD's own order, not alphabetical")
        assertEquals("Unconscious", state.rows.entries().last().ref.name, "through to the last")
        assertTrue(state.rows.entries().all { it.detail == null }, "conditions carry no right detail")
    }

    @Test
    fun rulesAreNineChapterHeadersEachOverItsOwnSections() {
        val rows = loadedRows(KindGroup.RULES)
        val sections = rows.sections()
        assertEquals(9, sections.size, "the bundle's 9 chapters are headers, never rows of their own")
        assertEquals(40, rows.entries().size, "and the 40 sections are the tappable rows")
        assertEquals(49, rows.size, "49 rows in all, the S13.1 total")
        assertEquals(
            listOf(
                "Adventuring",
                "Appendix",
                "Beyond 1st Level",
                "Characters",
                "Combat",
                "Equipment",
                "Monsters and NPCs",
                "Spellcasting",
                "Using Ability Scores",
            ),
            sections.map { it.first },
            "the chapters in the bundle's own order",
        )
        for ((chapter, entries) in sections) {
            assertTrue(entries.isNotEmpty(), "the $chapter header is followed by at least one section")
            assertTrue(entries.all { it.ref.kind == "rule_sections" }, "$chapter's rows are its sections")
        }
        assertEquals(
            listOf("Leveling Up", "Multiclassing"),
            sections.single { it.first == "Beyond 1st Level" }.second.map { it.ref.name },
            "a chapter's sections keep its reading order",
        )
        assertTrue(rows.entries().all { it.detail == null }, "rule sections carry no right detail")
    }

    @Test
    fun classesAndFeaturesAreTwoSectionsAndNoFeatures() {
        val rows = loadedRows(KindGroup.CLASSES_AND_FEATURES)
        val sections = rows.sections()
        assertEquals(listOf("Classes", "Subclasses"), sections.map { it.first }, "two sections, classes first")
        assertEquals(12, sections[0].second.size, "the bundle's 12 classes")
        assertEquals(12, sections[1].second.size, "and its 12 subclasses")
        assertEquals("Barbarian", sections[0].second.first().ref.name, "classes in the bundle's own order")
        assertTrue(rows.entries().none { it.ref.kind == "features" }, "63 features would be noise here (S13.1)")
        assertEquals(26, rows.size, "two headers over 24 rows")
    }

    @Test
    fun racesAreTheNineRacesThenTheFourSubraces() {
        val rows = loadedRows(KindGroup.RACES)
        val sections = rows.sections()
        assertEquals(listOf("Races", "Subraces"), sections.map { it.first }, "two sections, races first")
        assertEquals(9, sections[0].second.size, "the bundle's 9 races")
        assertEquals(4, sections[1].second.size, "and its 4 subraces")
        assertTrue(rows.entries().none { it.ref.kind == "traits" }, "traits hang off the race readers, not this list")
        assertEquals(15, rows.size, "two headers over 13 rows")
    }

    @Test
    fun backgroundsAndFeatsAreTwoOneRowSections() {
        val rows = loadedRows(KindGroup.BACKGROUNDS_AND_FEATS)
        assertEquals(
            listOf("Backgrounds" to listOf("Acolyte"), "Feats" to listOf("Grappler")),
            rows.sections().map { section -> section.first to section.second.map { it.ref.name } },
            "SRD 5.1 ships exactly one of each",
        )
    }

    @Test
    fun equipmentIsOneSectionPerCategoryThenTheWeaponProperties() {
        val rows = loadedRows(KindGroup.EQUIPMENT)
        val sections = rows.sections()
        assertEquals(
            listOf("Weapon", "Armor", "Adventuring gear", "Tools", "Mounts and vehicles", "Weapon properties"),
            sections.map { it.first },
            "combat gear leads; `categoriesOf` alphabetical order would bury weapons under 116 rows of gear",
        )
        assertEquals(237, rows.entries().count { it.ref.kind == "equipment" }, "every equipment record has a row")
        assertEquals(11, sections.last().second.size, "the bundle ships 11 weapon properties")
        assertEquals(254, rows.size, "6 headers over 248 rows — the hub's 237 counts equipment only (D13)")
        assertTrue(rows.entries().all { it.detail == null }, "equipment carries no right detail")
    }

    @Test
    fun magicItemsAreTheBaseItemsWithTheirRarity() {
        val rows = loadedRows(KindGroup.MAGIC_ITEMS)
        assertEquals(emptyList(), rows.headers(), "a flat list")
        assertEquals(239, rows.size, "the 239 base items; the 123 variants are reached from their readers")
        assertTrue(rows.entries().all { it.ref.subcategory == "base" }, "no variant is listed")
        assertTrue(rows.entries().all { !it.detail.isNullOrBlank() }, "every row shows a rarity")
        val adamantine = rows.entries().first { it.ref.key == "adamantine-armor" }
        assertEquals("Uncommon", adamantine.detail, "the rarity as the bundle writes it")
    }

    @Test
    fun creaturesAreTheWholeBestiaryWithTheirChallengeRating() {
        val rows = loadedRows(KindGroup.CREATURES)
        assertEquals(emptyList(), rows.headers(), "a flat list")
        assertEquals(334, rows.size, "the whole bestiary, bounded at its own count")
        assertEquals("Aboleth", rows.entries().first().ref.name, "in name order")
        assertTrue(rows.entries().all { !it.detail.isNullOrBlank() }, "every row shows a rating")
        assertEquals("1/4", rows.entries().first { it.ref.name == "Acolyte" }.detail, "a fraction, not a decimal")
        assertEquals("30", rows.entries().first { it.ref.name == "Tarrasque" }.detail, "and a whole number bare")
    }

    // ---- the two groups that are not hub rows ----------------------------------------------------------------

    @Test
    fun spellsAreRefusedBecauseTheyHaveTheirOwnScreen() {
        val failure = assertFailsWith<IllegalArgumentException> { vm(KindGroup.SPELLS) }
        assertTrue(
            failure.message.orEmpty().contains("SpellLevelScreen"),
            "the refusal names the screen the hub should have pushed: ${failure.message}",
        )
    }

    @Test
    fun theLookupKindsFallBackToOneSectionEach() {
        val rows = loadedRows(KindGroup.LOOKUP)
        assertEquals(
            listOf("Skills", "Languages", "Damage types", "Magic schools", "Alignments", "Proficiencies"),
            rows.sections().map { it.first },
            "the six lookup kinds in Kind declaration order",
        )
        assertEquals(18 + 16 + 13 + 8 + 9 + 117, rows.entries().size, "each kind's whole bundled list")
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    @Test
    fun aSecondShowDoesNotRebuildTheList() = runBlocking {
        val dao = Bundle.dao()
        val model = CompendiumListViewModel(Bundle.reader(dao), KindGroup.RULES)
        model.load()
        val first = model.state.value
        val callsAfterFirst = dao.calls.toList()
        model.load()
        assertEquals(callsAfterFirst, dao.calls, "a relaunch's second show runs no query")
        assertEquals(10, callsAfterFirst.size, "one chapter list and one query per chapter")
        assertEquals(first, model.state.value, "and cannot rebuild the list under the reader's finger")
    }

    // ---- keys ------------------------------------------------------------------------------------------------

    @Test
    fun wheelTurnsEmitSignedTicksAndThePressIsConsumedAsANoOp() = runBlocking {
        val model = vm(KindGroup.CONDITIONS)
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
        assertEquals(before, model.state.value, "no wheel event changes the list")
        collector.cancel()
    }

    @Test
    fun nonWheelKeysAreLeftToLightOs() {
        val model = vm(KindGroup.CONDITIONS)
        assertFalse(model.handleKey(24), "volume up stays unconsumed")
        assertFalse(model.handleKey(25), "volume down stays unconsumed")
        assertFalse(model.handleKey(80), "camera focus stays unconsumed")
        assertFalse(model.handleKey(4), "back stays unconsumed")
    }

    @Test
    fun theReleaseHalfOfEveryDetentIsSwallowedWithoutScrolling() = runBlocking {
        val model = vm(KindGroup.CONDITIONS)
        model.load()
        val seen = ArrayList<Int>()
        val collector = launch { model.ticks.collect { seen += it } }
        yield()
        // One detent is a DOWN/UP pair. `onKeyUp` defaults to false in the SDK's LightKeyHandler and
        // LightActivity forwards any unconsumed key it recognizes to the server with componentToRelaunch set,
        // so a screen that consumed only the DOWN half would still be relaunched by every turn. The release
        // must be swallowed without emitting a tick, or one detent would scroll six rows instead of three.
        assertTrue(model.consumesKey(317), "the release of a turn toward the top is consumed")
        assertTrue(model.consumesKey(318), "and of a turn toward the bottom")
        assertTrue(model.consumesKey(319), "and of the press")
        assertFalse(model.consumesKey(24), "volume up is still LightOS's")
        assertFalse(model.consumesKey(25), "so is volume down")
        assertFalse(model.consumesKey(80), "so is camera focus")
        assertFalse(model.consumesKey(4), "and so is back")
        yield()
        assertEquals(emptyList<Int>(), seen, "and not one of them scrolled the list")
        collector.cancel()
    }

    @Test
    fun consumingAndActingAgreeOnEveryKey() {
        // Two reads of WheelHandler.of that could drift apart: whatever handleKey acts on, consumesKey swallows.
        val model = vm(KindGroup.CONDITIONS)
        for (keyCode in listOf(317, 318, 319, 24, 25, 80, 4)) {
            assertEquals(model.handleKey(keyCode), model.consumesKey(keyCode), "key $keyCode is judged once")
        }
    }
}
