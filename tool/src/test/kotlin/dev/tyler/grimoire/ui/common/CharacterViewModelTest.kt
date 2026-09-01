package dev.tyler.grimoire.ui.common

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.data.CharacterRepository
import dev.tyler.grimoire.data.FakeCharacterRepository
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Model
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The flush contract every character screen inherits.
 *
 * Two of the three exit hooks can be driven from the JVM: `onAppPause` takes nothing, and `onCleared` is
 * widened to public by [CharacterViewModel] precisely so this file can call it. The third,
 * `onScreenHide`, takes a `SimpleLightScreen` that needs a real activity and a Main dispatcher — its body
 * is the same single call to [CharacterViewModel.flushNow], which is tested here directly.
 *
 * What these tests are really holding is the milestone's kill-and-relaunch requirement: a debounced save
 * that has not landed when the screen goes away is lost unless something flushes it, and the repository's
 * reads are served from the pending value in both the fake and the real store, so a reload cannot tell the
 * difference. `calls` is the only place a missing flush shows.
 */
class CharacterViewModelTest {
    /** The smallest possible subclass: it adds nothing, so what is tested is the base class alone. */
    private class Probe(repo: CharacterRepository, id: String) : CharacterViewModel<Unit>(repo, id)

    private val cleric: Character by lazy { Model.decode(Fixtures.character("cleric-5-life")) }

    private fun repoWith(character: Character) = FakeCharacterRepository().apply {
        stored[character.id] = character
        updatedAt[character.id] = 1L
    }

    @Test
    fun flushNowReachesTheRepository() {
        val repo = repoWith(cleric)
        Probe(repo, cleric.id).flushNow()
        assertEquals(listOf("flush"), repo.calls, "flushNow is a flush and nothing else")
    }

    @Test
    fun appPauseFlushes() {
        val repo = repoWith(cleric)
        Probe(repo, cleric.id).onAppPause()
        assertTrue(repo.calls.contains("flush"), "onAppPause must flush — it is the only hook a LightOS foreground steal fires")
    }

    @Test
    fun clearedFlushes() {
        val repo = repoWith(cleric)
        Probe(repo, cleric.id).onCleared()
        assertTrue(repo.calls.contains("flush"), "onCleared must flush — goBack() clears the store synchronously")
    }

    @Test
    fun aPendingSaveLandsOnEveryExitPath() {
        val wounded = cleric.copy(hp = cleric.hp.copy(damage = 20))
        for (exit in listOf<(Probe) -> Unit>({ it.onAppPause() }, { it.onCleared() }, { it.flushNow() })) {
            val repo = repoWith(cleric)
            val model = Probe(repo, cleric.id)
            repo.save(wounded)
            assertEquals(cleric, repo.stored[cleric.id], "the save is still pending before the exit")
            exit(model)
            assertEquals(wounded, repo.stored[cleric.id], "the pending save landed on this exit path")
        }
    }
}
