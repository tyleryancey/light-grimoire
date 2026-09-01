package dev.tyler.grimoire.ui.about

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.compendium.Block
import dev.tyler.grimoire.compendium.Span
import dev.tyler.grimoire.ui.common.maxLinesOf
import dev.tyler.grimoire.ui.common.plain
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S16's view model. The read is a lambda, so the asset comes from a fake here and from the real bundled
 * `assets/legal/ATTRIBUTION.md` in [theBundledAttributionSurvivesTheScreenVerbatim] — the licensing
 * regression test: docs/LICENSING.md requires the SRD 5.1 sentence and the modification notice to be on
 * this screen, and no edit to the screen may quietly drop either.
 *
 * The obligation runs past this class: the attribution is drawn by `ui/common/MarkdownBlocks`, shared with
 * S10's compendium prose, so [theSharedRendererNeverClipsProse] pins the renderer's truncation policy here
 * too — the licensing test is where a change that would clip the licence sentence has to fail.
 *
 * The tests call the internal `load()` directly — `onScreenShow` takes a `SimpleLightScreen`, which needs a
 * real activity and a Main dispatcher — which is also where the idempotence guard lives.
 */
class AboutViewModelTest {
    /** Records what was asked for, so a second `onScreenShow` can be shown to read nothing. */
    private class FakeAssets(private val answer: (String) -> String) : (String) -> String {
        val paths = ArrayList<String>()

        override fun invoke(path: String): String {
            paths += path
            return answer(path)
        }
    }

    private fun vm(
        assets: FakeAssets,
        versionName: String = "0.1.0",
        versionCode: Int = 1,
        toolId: String = "dev.tyler.grimoire",
    ) = AboutViewModel(assets, versionName, versionCode, toolId)

    // ---- the attribution -------------------------------------------------------------------------------------

    @Test
    fun theAssetReachesTheStateAsMarkdownBlocks() = runBlocking {
        val assets = FakeAssets { "# Attribution\n\nGrimoire is 5E compatible.\n" }
        val model = vm(assets)
        assertEquals(emptyList(), model.state.value.blocks, "nothing is rendered before the read")
        model.load()
        assertEquals(listOf("legal/ATTRIBUTION.md"), assets.paths, "the bundled legal file, at the assets root")
        assertEquals(
            listOf(Block.Heading(1, listOf(Span.Text("Attribution"))), Block.Para(listOf(Span.Text("Grimoire is 5E compatible.")))),
            model.state.value.blocks,
            "the heading is kept — S16 has no record name to match it against — and the prose follows",
        )
        assertNull(model.state.value.notice, "a successful read leaves no fallback line")
    }

    @Test
    fun aParagraphsHardWrapIsRejoinedIntoOneRunOfProse() = runBlocking {
        // pipeline/legal.py wraps the generated file at its own column width; honouring those breaks on a
        // ~38-character screen would end every third line short. Whitespace only — no word is altered.
        val model = vm(FakeAssets { "The text has been reorganized\nfor display on a small screen.\n" })
        model.load()
        assertEquals(
            listOf("The text has been reorganized for display on a small screen."),
            model.state.value.blocks.map(::text),
            "the source's line breaks reflow into spaces",
        )
        assertTrue(
            model.state.value.blocks.none { it is Block.Para && Span.LineBreak in it.spans },
            "no hard break survives into the rendering",
        )
    }

    // ---- the identity block ----------------------------------------------------------------------------------

    @Test
    fun theFourIdentityLinesAreComposedAsS16Specifies() = runBlocking {
        val model = vm(FakeAssets { "# Attribution\n" }, versionName = "0.1.0", versionCode = 1)
        assertEquals(
            listOf(
                "Grimoire 0.1.0 (1)",
                "dev.tyler.grimoire",
                "5E compatible",
                "github.com/tyleryancey/light-grimoire",
            ),
            model.state.value.lines,
            "version, tool id, the one permitted compatibility claim, the repository as plain text",
        )
        model.load()
        assertEquals(4, model.state.value.lines.size, "and the read adds no fifth line")
    }

    @Test
    fun theIdentityLinesTrackTheBuildTheyAreGiven() = runBlocking {
        // The screen passes BuildConfig, which the Light plugin fills from lighttool.toml, so these strings
        // follow a release rather than being retyped.
        val model = vm(FakeAssets { "" }, versionName = "1.2.3", versionCode = 17, toolId = "dev.tyler.other")
        assertEquals("Grimoire 1.2.3 (17)", model.state.value.lines[0], "the version line is name, version, code")
        assertEquals("dev.tyler.other", model.state.value.lines[1], "the tool id is the one it was built with")
    }

    // ---- a read that fails -----------------------------------------------------------------------------------

    @Test
    fun aThrowingReadLeavesAQuietNoticeAndKeepsTheVersionBlock() = runBlocking {
        val model = vm(FakeAssets { error("no such asset") })
        model.load()
        val state = model.state.value
        assertEquals(emptyList(), state.blocks, "nothing was parsed")
        assertEquals(AboutViewModel.ATTRIBUTION_UNAVAILABLE, state.notice, "the fallback line stands in for it")
        assertEquals(
            listOf(
                "Grimoire 0.1.0 (1)",
                "dev.tyler.grimoire",
                "5E compatible",
                "github.com/tyleryancey/light-grimoire",
            ),
            state.lines,
            "a failed read cannot take the identity block down with it",
        )
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    @Test
    fun aSecondShowDoesNotReadAgain() = runBlocking {
        val assets = FakeAssets { "# Attribution\n\nOne paragraph.\n" }
        val model = vm(assets)
        model.load()
        val first = model.state.value
        model.load()
        assertEquals(1, assets.paths.size, "a relaunch's second show reads nothing")
        assertEquals(first, model.state.value, "and cannot clobber what is already on screen")
    }

    // ---- keys ------------------------------------------------------------------------------------------------

    @Test
    fun wheelTurnsEmitSignedTicksAndThePressIsConsumedAsANoOp() = runBlocking {
        val model = vm(FakeAssets { "# Attribution\n" })
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
        val model = vm(FakeAssets { "" })
        assertFalse(model.handleKey(24), "volume up stays unconsumed")
        assertFalse(model.handleKey(25), "volume down stays unconsumed")
        assertFalse(model.handleKey(80), "camera focus stays unconsumed")
        assertFalse(model.handleKey(27), "the camera key stays unconsumed")
        assertFalse(model.handleKey(4), "back stays unconsumed")
        assertTrue(model.consumesKey(317), "the wheel's key-up half is swallowed too")
        assertTrue(model.consumesKey(319), "including the press, whose down half is a no-op")
    }

    // ---- licensing regression --------------------------------------------------------------------------------

    @Test
    fun theBundledAttributionSurvivesTheScreenVerbatim() = runBlocking {
        // The real generated asset, through the real view model. Whitespace is normalized on both sides
        // because the file is hard-wrapped and the screen reflows it (pipeline/legal.py may rewrap at any
        // width); every word must still be there, in order.
        val source = Fixtures.legal("ATTRIBUTION.md")
        val model = vm(FakeAssets { path ->
            assertEquals(AboutViewModel.ATTRIBUTION_PATH, path, "the screen reads the generated legal file")
            source
        })
        model.load()
        val rendered = squeeze(model.state.value.blocks.joinToString(" ", transform = ::text))

        // docs/LICENSING.md: "Attribution (exact; do not reword)".
        val ccBy = squeeze(
            """
            This work includes material taken from the System Reference Document 5.1 ("SRD 5.1") by Wizards
            of the Coast LLC and available at https://dnd.wizards.com/resources/systems-reference-document.
            The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License
            available at https://creativecommons.org/licenses/by/4.0/legalcode.
            """,
        )
        assertTrue(ccBy in rendered, "the exact CC-BY sentence is on the About screen")

        // CC-BY-4.0 3(a)(1)(B): modifications must be indicated.
        val modificationNotice = squeeze(
            """
            The text has been reorganized into a searchable database for display on a small screen: entries
            were split into fields and paragraphs, and cross-references were converted to keys. No rules
            wording was changed.
            """,
        )
        assertTrue(modificationNotice in rendered, "the modification notice is on the About screen")

        assertTrue("5E compatible" in rendered, "the permitted compatibility statement is kept")
        assertNull(model.state.value.notice, "the bundled file reads cleanly")
    }

    @Test
    fun theWholeFileIsRenderedInOrderAndNothingIsAdded() = runBlocking {
        // The other half of the obligation. Nothing may be dropped (a paragraph lost to a parser change
        // would take the licence notice with it) and nothing may be added — the SRD's own legal page asks
        // for no other Wizards attribution, so no trademark line and no Fan Content text.
        //
        // The file is heading-plus-paragraphs, so block-per-paragraph is exact rather than approximate. If
        // pipeline/legal.py ever emits a list or a table this fails by design: a change to the legal text's
        // shape is a licensing review (docs/LICENSING.md), not a test to relax.
        val source = Fixtures.legal("ATTRIBUTION.md")
        val model = vm(FakeAssets { source })
        model.load()
        val blocks = model.state.value.blocks
        val paragraphs = source.split(Regex("\n\\s*\n"))
            .map { squeeze(it.removePrefix("# ")) }
            .filter { it.isNotEmpty() }
        assertTrue(paragraphs.size > 1, "the generated file is a heading and its paragraphs")
        assertEquals(paragraphs.size, blocks.size, "one block per paragraph of ATTRIBUTION.md — no more, no fewer")
        paragraphs.forEachIndexed { i, paragraph ->
            assertEquals(paragraph, squeeze(text(blocks[i])), "paragraph ${i + 1} is rendered whole and in order")
        }
    }

    @Test
    fun theSharedRendererNeverClipsProse() {
        // The other half of the same obligation, and the reason it is asserted here rather than beside the
        // renderer: `MarkdownBlocks` draws S10's compendium prose as well as this screen's attribution, and
        // S10 has a real appetite for a one-line clip (its creature ability grids are drawn that way). Give a
        // prose branch a `maxLines` for the reader's benefit and the CC-BY sentence is cut off on the phone
        // with every assertion above still green, because none of them renders. `maxLinesOf` is the single
        // place the renderer decides, so pinning it is the JVM gate's only reach into that decision — the
        // module has no Robolectric and no compose-ui-test.
        val spans = listOf(Span.Text("A sentence long enough to wrap on a 38-character screen."))
        val prose = listOf(
            Block.Heading(1, spans),
            Block.Heading(2, spans),
            Block.Heading(3, spans),
            Block.Heading(4, spans),
            Block.Heading(5, spans),
            Block.Para(spans),
            Block.Bullet(spans),
            Block.Numbered(1, spans),
            Block.Field("Casting Time: 1 action"),
        )
        for (block in prose) {
            assertEquals(
                Int.MAX_VALUE,
                maxLinesOf(block),
                "prose wraps and is drawn whole — the attribution renders through this: $block",
            )
        }
        // The one clip, and the only one: a table line, whose column alignment a wrap would break.
        // Block.Table never reaches a text component — TableLayout lowers it to Mono lines first.
        assertEquals(1, maxLinesOf(Block.Mono("STR 10  DEX 14", compact = true, secondary = false)), "a table line")
        assertEquals(1, maxLinesOf(Block.Mono("STR 10  DEX 14", compact = false, secondary = true)), "wide too")
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    /**
     * What `MarkdownBlocks` draws for one block, as text. The span-flattening leg calls the renderer's own
     * `plain` (made `internal` for exactly this) rather than a copy, so the licensing assertions above read
     * what the screen reads.
     */
    private fun text(block: Block): String = when (block) {
        is Block.Heading -> plain(block.spans)
        is Block.Para -> plain(block.spans)
        is Block.Bullet -> plain(block.spans)
        is Block.Numbered -> plain(block.spans)
        is Block.Field -> block.text
        is Block.Mono -> block.text
        is Block.Table -> block.rows.joinToString(" ") { row -> row.joinToString(" ") }
    }

    private fun squeeze(text: String): String = text.replace(Regex("\\s+"), " ").trim()
}
