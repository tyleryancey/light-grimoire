package dev.tyler.grimoire.ui.home

import dev.tyler.grimoire.compendium.ImportState
import dev.tyler.grimoire.data.CharacterLimits
import dev.tyler.grimoire.data.FakeCharacterRepository
import dev.tyler.grimoire.data.NewCharacter
import dev.tyler.grimoire.data.Summaries
import dev.tyler.grimoire.rules.Character
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S0, driven through the seams the M3 rewrite gave it: the compendium's state is a `MutableStateFlow` a
 * test moves by hand, the store is [FakeCharacterRepository] (which refuses and stamps exactly as the real
 * one does), and the import call is a counter. None of it needs a phone — which is the whole point of the
 * rewrite, since the M2 view model took a `SealedLightContext` and could not be tested at all.
 *
 * The tests drive the internal `show()` rather than `onScreenShow`, which needs a real activity; `show()`
 * is where both halves of a show live — the import call and the reload.
 */
class HomeViewModelTest {
    private companion object {
        /** LP3 wheel key codes, from `LightDeviceKeys` — 317 toward the top of the phone, 318 down, 319 press. */
        const val WHEEL_UP = 317
        const val WHEEL_DOWN = 318
        const val WHEEL_PRESS = 319

        /** A key the tool must never consume, so LightOS still gets it. */
        const val VOLUME_UP = 24
    }

    /** One Home over one store, with the import state under the test's control. */
    private class Screen(
        imports: ImportState = ImportState.Ready,
        characters: List<Character> = emptyList(),
    ) {
        val importState = MutableStateFlow(imports)

        val repo = FakeCharacterRepository().apply {
            characters.forEachIndexed { index, character ->
                stored[character.id] = character
                // Ascending, so the last one seeded is the most recently touched.
                updatedAt[character.id] = (index + 1).toLong()
            }
        }

        var importCalls = 0

        var opened: String? = null

        /** Unconfined so a flipped import state reaches the view model's collector on the setting thread. */
        private val scope = CoroutineScope(Dispatchers.Unconfined)

        val model = HomeViewModel(
            ensureImported = { importCalls++ },
            importState = importState,
            characters = repo,
            scope = scope,
            newId = { "minted" },
        )

        /** Every detent the view model asked the list to scroll by, in order. */
        val ticks: MutableList<Int> = ArrayList()

        init {
            scope.launch { model.ticks.collect { ticks += it } }
        }

        val state get() = model.state.value

        fun show() = runBlocking { model.show() }

        /** `NEW` all the way through: the name, the class, and the id the sheet would be opened on. */
        fun create(name: String, classKey: String) = runBlocking {
            model.create(name, classKey) { opened = it }
        }
    }

    private fun character(id: String, name: String, classKey: String = "cleric"): Character =
        NewCharacter.of(name, classKey, id)

    // ---- the three bodies ------------------------------------------------------------------------------------

    @Test
    fun theImportIsALineAndABarUntilItFinishes() {
        val screen = Screen(imports = ImportState.Idle, characters = listOf(character("a", "Brother Aldric")))
        screen.show()
        assertEquals(HomeBody.PREPARING, screen.state.body, "Idle draws the preparing line")
        assertEquals(0f, screen.state.progress, "with nothing on the bar yet")

        screen.importState.value = ImportState.Checking
        assertEquals(HomeBody.PREPARING, screen.state.body, "so does Checking")

        screen.importState.value = ImportState.Importing(done = 11, total = 22)
        assertEquals(HomeBody.PREPARING, screen.state.body, "and Importing")
        assertEquals(0.5f, screen.state.progress, "eleven of the bundle's twenty-two kinds")
    }

    @Test
    fun noCharacterIsDrawnBeforeTheCompendiumIsReady() {
        val screen = Screen(imports = ImportState.Checking, characters = listOf(character("a", "Brother Aldric")))
        screen.show()
        assertEquals(
            emptyList(),
            screen.state.characters,
            "S1 needs the compendium's armor table, so a row before Ready would throw out of navigateTo",
        )
        assertFalse(screen.state.canCreate, "and NEW is not offered either — its class picker reads the bundle")
    }

    @Test
    fun aFailedImportShowsItsReasonAndNoList() {
        val screen = Screen(
            imports = ImportState.Failed("index.json is not readable"),
            characters = listOf(character("a", "Brother Aldric")),
        )
        screen.show()
        assertEquals(HomeBody.FAILED, screen.state.body, "the reason takes the progress line's place")
        assertEquals("index.json is not readable", screen.state.reason, "the store's own words")
        assertEquals(
            emptyList(),
            screen.state.characters,
            "docs/UI-SPEC.md S0: the list appears only once the store is Ready",
        )
    }

    @Test
    fun theListArrivesWhenTheImportFinishesWithNoFurtherShow() {
        val screen = Screen(imports = ImportState.Checking, characters = listOf(character("a", "Brother Aldric")))
        screen.show()
        assertEquals(HomeBody.PREPARING, screen.state.body, "the wait")
        screen.importState.value = ImportState.Ready
        assertEquals(HomeBody.LIST, screen.state.body, "the import finishes on its own scope and Home follows")
        assertEquals(listOf("Brother Aldric"), screen.state.characters.map { it.name }, "with the characters")
    }

    // ---- the list --------------------------------------------------------------------------------------------

    @Test
    fun theListIsTheSixMostRecentNewestFirst() {
        val seven = (1..7).map { character("id-$it", "Character $it") }
        val screen = Screen(characters = seven)
        screen.show()
        assertEquals(
            listOf("Character 7", "Character 6", "Character 5", "Character 4", "Character 3", "Character 2"),
            screen.state.characters.map { it.name },
            "most recently touched first, capped at ${CharacterLimits.MAX_CHARACTERS}",
        )
    }

    @Test
    fun eachRowIsTheStoredNameOverItsSummary() {
        val cleric = character("a", "Brother Aldric")
        val screen = Screen(characters = listOf(cleric))
        screen.show()
        val row = screen.state.characters.single()
        assertEquals("Brother Aldric", row.name, "the name as transcribed")
        assertEquals(Summaries.summaryOf(cleric), row.summary, "and S1's own identity line, from one function")
    }

    @Test
    fun anEmptyStoreIsReadyWithNothingInIt() {
        val screen = Screen()
        screen.show()
        assertEquals(HomeBody.LIST, screen.state.body, "Ready with no characters is still the list body")
        assertEquals(emptyList(), screen.state.characters, "the screen's one quiet line covers it")
        assertTrue(screen.state.empty, "and that line is drawn")
        assertTrue(screen.state.canCreate, "NEW is the way out of it")
    }

    @Test
    fun anUnansweredStoreIsNotAnEmptyOne() {
        // The stamp check and one indexed SELECT are both milliseconds on a warm launch, so Ready can land
        // first. A player with six characters must not be told they have none for the frame in between.
        val screen = Screen(characters = (1..6).map { character("id-$it", "Character $it") })
        assertEquals(HomeBody.LIST, screen.state.body, "Ready before the first list has come back")
        assertFalse(screen.state.empty, "so no quiet line is drawn yet")
        assertFalse(screen.state.canCreate, "and NEW does not answer with a count nobody has read")
        screen.show()
        assertFalse(screen.state.empty, "and once it answers, six characters is not empty either")
    }

    @Test
    fun everyShowReloadsTheList() {
        val cleric = character("a", "Brother Aldric")
        val screen = Screen(characters = listOf(cleric))
        screen.show()
        assertEquals("Brother Aldric", screen.state.characters.single().name, "as stored")

        // What a rename on S1 leaves behind: goBack() shows Home again before it delivers any result, so a
        // `loaded` guard here would leave the old name on screen.
        screen.repo.save(cleric.copy(name = "Aldric of the Vale"))
        screen.show()
        assertEquals(
            "Aldric of the Vale",
            screen.state.characters.single().name,
            "the second show reads the store again",
        )
        assertEquals(2, screen.repo.calls.count { it == "list" }, "one indexed query per show, and no load")
        assertFalse(screen.repo.calls.any { it.startsWith("load") }, "S0 never decodes a character document")
    }

    @Test
    fun everyShowAsksTheStoreToImportTheCompendium() {
        val screen = Screen(imports = ImportState.Failed("read failed"))
        screen.show()
        screen.show()
        assertEquals(2, screen.importCalls, "the store's state is the guard, so a failed launch retries on the next show")
    }

    // ---- NEW -------------------------------------------------------------------------------------------------

    @Test
    fun creatingACharacterReturnsAnIdTheNextListContains() {
        val screen = Screen()
        screen.show()
        assertTrue(screen.model.requestNew(), "an empty store can take one")
        screen.create("Brother Aldric", "cleric")
        assertEquals("minted", screen.opened, "the sheet is opened on the id create returned")

        screen.show()
        val row = screen.state.characters.single()
        assertEquals("minted", row.id, "and the next list holds it")
        assertEquals("Brother Aldric", row.name, "under the typed name")
        assertEquals("Cleric 1", row.summary, "at level 1, with no race yet")
        assertNull(screen.state.message, "nothing was refused")
    }

    @Test
    fun theSeventhCharacterIsRefusedBeforeAnythingIsTyped() {
        val screen = Screen(characters = (1..6).map { character("id-$it", "Character $it") })
        screen.show()
        assertFalse(screen.model.requestNew(), "the editor is never pushed")
        assertEquals(
            CharacterLimits.tooMany(CharacterLimits.MAX_CHARACTERS),
            screen.state.message,
            "the same sentence the repository throws",
        )
        assertFalse(screen.repo.calls.any { it.startsWith("create") }, "and nothing reached the store")
    }

    @Test
    fun aSecondRefusalStillMoves() {
        val screen = Screen(characters = (1..6).map { character("id-$it", "Character $it") })
        screen.show()
        screen.model.requestNew()
        val first = screen.state
        screen.model.requestNew()
        assertEquals(first.message, screen.state.message, "the sentence does not change")
        assertEquals(
            first.messageNonce + 1,
            screen.state.messageNonce,
            "but the screen is told to show it again — a second tap that looked identical would read as dead",
        )
    }

    @Test
    fun aRefusalIsGoneByTheNextShow() {
        val screen = Screen(characters = (1..6).map { character("id-$it", "Character $it") })
        screen.show()
        screen.model.requestNew()
        assertNotNull(screen.state.message, "refused")
        screen.show()
        assertNull(screen.state.message, "a player back from a sheet is not still being told")
    }

    @Test
    fun anOverLongNameIsRefusedBeforeTheClassIsPicked() {
        // The editor has no length parameter, so a 41-character name is typable. Refusing it here rather
        // than at create() is what keeps the transcription: the editor's result is gone once the class step
        // has been through, and the player would have to type the whole name again.
        val screen = Screen()
        screen.show()
        val long = "x".repeat(CharacterLimits.MAX_NAME + 1)
        assertFalse(screen.model.requestClass(long), "the class picker is never pushed")
        assertEquals(
            CharacterLimits.nameTooLong(CharacterLimits.MAX_NAME + 1),
            screen.state.message,
            "the same sentence CharacterLimits.check throws, said one screen earlier",
        )
        assertFalse(screen.repo.calls.any { it.startsWith("create") }, "and nothing reached the store")
    }

    @Test
    fun aNameAtTheLimitGoesOnToTheClassStep() {
        val screen = Screen()
        screen.show()
        assertTrue(screen.model.requestClass("x".repeat(CharacterLimits.MAX_NAME)), "40 is legal")
        assertTrue(screen.model.requestClass("Brother Aldric"), "and so is every real name")
        assertNull(screen.state.message, "neither earns a sentence")
    }

    @Test
    fun aCharacterTheStoreRefusesIsASentenceAndNoSheet() {
        val screen = Screen()
        screen.create("x".repeat(CharacterLimits.MAX_NAME + 1), "cleric")
        assertNull(screen.opened, "no sheet is opened")
        assertEquals(
            "name is ${CharacterLimits.MAX_NAME + 1} characters (at most ${CharacterLimits.MAX_NAME})",
            screen.state.message,
            "the engine's own refusal, read out to the player",
        )
        screen.show()
        assertEquals(emptyList(), screen.state.characters, "and nothing was stored")
    }

    @Test
    fun newIsNotOfferedBeforeReady() {
        val screen = Screen(imports = ImportState.Failed("read failed"))
        screen.show()
        assertFalse(screen.model.requestNew(), "the class picker would throw on CompendiumStore.reader()")
        assertNull(screen.state.message, "and there is nothing to explain — NEW is not drawn in this body")
    }

    // ---- the wheel -------------------------------------------------------------------------------------------

    @Test
    fun theWheelIsConsumedWholeInEveryState() {
        val states = listOf(ImportState.Idle, ImportState.Importing(1, 22), ImportState.Failed("no"), ImportState.Ready)
        for (imports in states) {
            val screen = Screen(imports = imports)
            for (key in listOf(WHEEL_UP, WHEEL_DOWN, WHEEL_PRESS)) {
                assertTrue(screen.model.handleKey(key), "$imports: key $key is consumed, never forwarded to LightOS")
                assertTrue(screen.model.consumesKey(key), "$imports: and its release half with it")
            }
        }
    }

    @Test
    fun aTurnScrollsTheListOneCharacterRowPerDetent() {
        val screen = Screen(characters = (1..6).map { character("id-$it", "Character $it") })
        screen.show()
        screen.model.handleKey(WHEEL_UP)
        screen.model.handleKey(WHEEL_DOWN)
        screen.model.handleKey(WHEEL_PRESS)
        assertEquals(listOf(-1, 1), screen.ticks, "317 toward the top of the phone, 318 toward the bottom")
    }

    @Test
    fun aTurnDuringTheImportMovesNothing() {
        val screen = Screen(imports = ImportState.Importing(1, 22))
        screen.show()
        screen.model.handleKey(WHEEL_UP)
        screen.model.handleKey(WHEEL_DOWN)
        assertEquals(
            emptyList(),
            screen.ticks,
            "docs/UI-SPEC.md S0: before Ready there is no list, so the turn is a consumed no-op",
        )
    }

    @Test
    fun volumeIsLeftForLightOs() {
        val screen = Screen()
        assertFalse(screen.model.handleKey(VOLUME_UP), "volume is not the tool's key")
        assertFalse(screen.model.consumesKey(VOLUME_UP), "in either half")
    }
}
