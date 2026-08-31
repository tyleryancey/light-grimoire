package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.compendium.Block
import dev.tyler.grimoire.compendium.CompendiumReader
import dev.tyler.grimoire.compendium.FakeCompendiumDao
import dev.tyler.grimoire.compendium.ImportContext
import dev.tyler.grimoire.compendium.JsonArraySplit
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.compendium.Rows
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S10's view model over the real bundle: [FakeCompendiumDao] answers from the rows [Rows.of] derives, so
 * every expectation below is the sha256-pinned assets rather than a sample. The tests call the internal
 * `load()` directly — `onScreenShow` takes a `SimpleLightScreen`, which needs a real activity and a Main
 * dispatcher neither of which exists on the JVM — which is also where the idempotence guard lives, so the
 * relaunch case is exercised where it is implemented.
 */
class ReaderViewModelTest {
    private companion object {
        val built: List<Rows.Built> by lazy {
            var ctx = ImportContext.EMPTY
            Kind.entries.flatMap { kind ->
                val text = Fixtures.compendium(kind.file)
                val slices = JsonArraySplit.elements(text)
                val records = kind.decodeAll(text)
                if (kind == Kind.RULES) ctx = ImportContext.from(records)
                records.indices.map { Rows.of(kind, it, slices[it], records[it], ctx) }
            }
        }
    }

    private fun dao(rows: List<Rows.Built> = built) = FakeCompendiumDao(rows)

    private fun vm(kind: Kind, key: String, dao: FakeCompendiumDao = dao(), name: String = "") =
        ReaderViewModel(CompendiumReader(dao), kind, key, name)

    private fun fields(blocks: List<Block>): List<String> =
        blocks.filterIsInstance<Block.Field>().map { it.text }

    // ---- loading ---------------------------------------------------------------------------------------------

    @Test
    fun fireballLoadsItsHeaderLinesAndBody() = runBlocking {
        val model = vm(Kind.SPELLS, "fireball", name = "Fireball")
        assertTrue(model.state.value.loading, "the reader starts loading")
        assertEquals("Fireball", model.state.value.title, "the row's name titles the bar from the first frame")
        model.load()
        val state = model.state.value
        assertEquals("Fireball", state.title, "the title is the record's name")
        assertFalse(state.loading, "loading clears once the record is composed")
        assertFalse(state.missing, "fireball is in the compendium")
        assertEquals(
            listOf(
                "3rd-level evocation",
                "1 action · 150 feet · V S M",
                "Instantaneous",
                "M: A tiny ball of bat guano and sulfur.",
                "Sorcerer, Wizard",
            ),
            fields(state.blocks),
            "the S10 header lines reach the state in composition order",
        )
        assertTrue(state.blocks.any { it is Block.Para }, "the body prose is rendered too")
    }

    @Test
    fun anUnknownKeyIsMissingAndNotLoading() = runBlocking {
        val model = vm(Kind.SPELLS, "no-such-spell", name = "Wish")
        model.load()
        val state = model.state.value
        assertTrue(state.missing, "an unresolvable key is the missing branch")
        assertFalse(state.loading, "the wait ends even when nothing was found")
        assertEquals(emptyList(), state.blocks, "nothing to render")
        assertEquals("Wish", state.title, "the row's name still titles the bar over the missing branch")
    }

    // ---- links -----------------------------------------------------------------------------------------------

    @Test
    fun aClassResolvesSubclassesThenFeaturesInReaderContentOrder() = runBlocking {
        val model = vm(Kind.CLASSES, "cleric")
        model.load()
        val links = model.state.value.links
        assertEquals(listOf("SUBCLASSES", "FEATURES"), links.map { it.label }, "the class footer, in composition order")
        assertTrue(links[0].refs.any { it.key == "life" }, "the Life domain is among the cleric's subclasses")
        assertTrue(links[1].refs.any { it.key == "spellcasting-cleric" }, "the cleric's own features are resolved")
        assertTrue(links[1].refs.all { it.kind == "features" }, "the FEATURES rows are features")
    }

    @Test
    fun aRuleSectionResolvesItsChapterThroughTheOwnerColumn() = runBlocking {
        val dao = dao()
        val model = vm(Kind.RULE_SECTIONS, "multiclassing", dao)
        model.load()
        val chapter = model.state.value.links.single { it.label == "CHAPTER" }
        assertEquals(listOf("beyond-1st-level"), chapter.refs.map { it.key }, "multiclassing belongs to Beyond 1st Level")
        assertEquals(listOf("Beyond 1st Level"), chapter.refs.map { it.name }, "the chapter row carries its name")
        assertTrue("chapterOfSection" in dao.calls, "the CHAPTER link goes through the new query")
    }

    @Test
    fun aRuleSectionWhoseChapterIsNotThereDropsTheChapterLabel() = runBlocking {
        // The documented null branch of chapterOfSection, over a compendium holding the sections but not
        // the chapters: multiclassing's parentKey still reads "beyond-1st-level", and both the fake and the
        // SQL answer null when that key matches no `rules` row — as they do when parentKey is null itself.
        val sectionsOnly = built.filter { it.record.kind == Kind.RULE_SECTIONS.id }
        val dao = dao(sectionsOnly)
        val model = vm(Kind.RULE_SECTIONS, "multiclassing", dao)
        model.load()
        assertTrue("chapterOfSection" in dao.calls, "the CHAPTER query still runs")
        assertEquals("Multiclassing", model.state.value.title, "the section itself still loads")
        assertEquals(
            emptyList(),
            model.state.value.links.filter { it.label == "CHAPTER" },
            "a null chapter is not a footer section",
        )
    }

    @Test
    fun holdPersonSeeLinkResolvesToTheParalyzedCondition() = runBlocking {
        val model = vm(Kind.SPELLS, "hold-person")
        model.load()
        val see = model.state.value.links.single { it.label == "SEE" }
        assertEquals(listOf("paralyzed"), see.refs.map { it.key }, "hold-person points at paralyzed")
        assertEquals(listOf("conditions"), see.refs.map { it.kind }, "the SEE row is a condition")
        assertEquals(listOf("Paralyzed"), see.refs.map { it.name }, "the row reads the condition's name")
    }

    @Test
    fun aSubclassResolvesItsOwnFeatures() = runBlocking {
        val model = vm(Kind.SUBCLASSES, "life")
        model.load()
        val features = model.state.value.links.single { it.label == "FEATURES" }
        assertTrue(features.refs.isNotEmpty(), "the Life domain's features are resolved")
        assertTrue(features.refs.all { it.kind == "features" }, "the FEATURES rows are features")
        assertTrue(features.refs.all { it.classKey == "cleric" }, "and they are the cleric's")
    }

    @Test
    fun aRulesChapterResolvesItsSectionsInReadingOrder() = runBlocking {
        val model = vm(Kind.RULES, "beyond-1st-level")
        model.load()
        val sections = model.state.value.links.single { it.label == "SECTIONS" }
        assertEquals(
            listOf("leveling-up", "multiclassing"),
            sections.refs.map { it.key },
            "the chapter's sections in its own reading order, not alphabetically",
        )
    }

    @Test
    fun aKeysSectionKeepsTheCompositionsOrderNotSortName() = runBlocking {
        val model = vm(Kind.RACES, "dragonborn")
        model.load()
        val traits = model.state.value.links.single { it.label == "TRAITS" }
        assertEquals(
            listOf("Draconic Ancestry", "Breath Weapon", "Damage Resistance"),
            traits.refs.map { it.name },
            "the record's own trait order survives the sortName-ordered query",
        )
    }

    @Test
    fun aLinkSectionThatResolvesToNothingIsDropped() = runBlocking {
        // The same hold-person, read from a compendium holding only spells: its SEE query finds no
        // condition row, so the label never reaches the footer.
        val spellsOnly = built.filter { it.record.kind == Kind.SPELLS.id }
        val model = vm(Kind.SPELLS, "hold-person", dao(spellsOnly))
        model.load()
        assertEquals(emptyList(), model.state.value.links, "an empty resolution is not an empty section")
        assertEquals("Hold Person", model.state.value.title, "the record itself still loads")
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    @Test
    fun aSecondShowDoesNotReload() = runBlocking {
        val dao = dao()
        val model = vm(Kind.SPELLS, "hold-person", dao)
        model.load()
        val first = model.state.value
        val callsAfterFirst = dao.calls.toList()
        model.load()
        assertEquals(callsAfterFirst, dao.calls, "a relaunch's second show runs no query")
        assertEquals(listOf("get", "refs"), callsAfterFirst, "one fetch and one link resolution, once")
        assertEquals(first, model.state.value, "and cannot clobber what is already on screen")
    }

    // ---- keys ------------------------------------------------------------------------------------------------

    @Test
    fun wheelTurnsEmitSignedTicksAndThePressIsConsumedAsANoOp() = runBlocking {
        val model = vm(Kind.SPELLS, "fireball")
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
        assertEquals(before, model.state.value, "no wheel event changes the state")
        collector.cancel()
    }

    @Test
    fun nonWheelKeysAreLeftToLightOs() = runBlocking {
        val model = vm(Kind.SPELLS, "fireball")
        assertFalse(model.handleKey(24), "volume up stays unconsumed")
        assertFalse(model.handleKey(25), "volume down stays unconsumed")
        assertFalse(model.handleKey(80), "camera focus stays unconsumed")
        assertFalse(model.handleKey(27), "the camera key stays unconsumed")
        assertFalse(model.handleKey(4), "back stays unconsumed")
    }
}
