package dev.tyler.grimoire.ui.home

import dev.tyler.grimoire.compendium.CompendiumReader
import dev.tyler.grimoire.data.CharacterLimits
import dev.tyler.grimoire.data.NewCharacter
import dev.tyler.grimoire.rules.Tables
import dev.tyler.grimoire.ui.compendium.Bundle
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S0's `NEW`, step two: the twelve classes as the bundle itself lists them (docs/UI-SPEC.md S12 step 2).
 *
 * Every row below is the sha256-pinned `assets/compendium/classes.json` read through the same
 * `listInOrder` the device runs, never a literal in the tool — and the last test is the join that matters:
 * every key this screen can hand back builds a legal character in `data/NewCharacter`.
 */
class ClassPickerViewModelTest {
    private companion object {
        const val WHEEL_UP = 317
        const val WHEEL_DOWN = 318
        const val WHEEL_PRESS = 319
        const val VOLUME_UP = 24
    }

    private fun loaded(): ClassPickerViewModel = ClassPickerViewModel(Bundle.reader()).also {
        runBlocking { it.load() }
    }

    @Test
    fun theTwelveSrdClassesInTheBundlesOrder() {
        val model = ClassPickerViewModel(Bundle.reader())
        assertTrue(model.state.value.loading, "the picker starts on the quiet line")
        assertEquals(emptyList(), model.state.value.rows, "with nothing to draw yet")
        runBlocking { model.load() }
        assertFalse(model.state.value.loading, "the wait ends with the rows")
        assertEquals(
            listOf(
                "Barbarian", "Bard", "Cleric", "Druid", "Fighter", "Monk",
                "Paladin", "Ranger", "Rogue", "Sorcerer", "Warlock", "Wizard",
            ),
            model.state.value.rows.map { it.name },
            "the bundle's own order, which is alphabetical for classes",
        )
    }

    @Test
    fun theListIsBuiltOnceAndNeverRebuiltUnderAFinger() {
        val dao = Bundle.dao()
        val model = ClassPickerViewModel(CompendiumReader(dao))
        runBlocking {
            model.load()
            val queries = dao.calls.size
            model.load()
            assertEquals(queries, dao.calls.size, "a relaunch's second onScreenShow asks the database nothing")
        }
    }

    @Test
    fun everyKeyThePickerCanHandBackBuildsALegalCharacter() {
        for (row in loaded().state.value.rows) {
            val character = NewCharacter.of("Brother Aldric", row.key, "id-${row.key}")
            CharacterLimits.check(character)
            assertEquals(
                Tables.HIT_DIE.getValue(row.key),
                character.hp.max,
                "${row.key}: the compendium's key is one the engine's hit-die table knows",
            )
        }
    }

    @Test
    fun theWheelIsConsumedWhole() {
        val model = loaded()
        for (key in listOf(WHEEL_UP, WHEEL_DOWN, WHEEL_PRESS)) {
            assertTrue(model.handleKey(key), "key $key is consumed, never forwarded to LightOS")
            assertTrue(model.consumesKey(key), "and its release half with it")
        }
        assertFalse(model.handleKey(VOLUME_UP), "volume is not the tool's key")
        assertFalse(model.consumesKey(VOLUME_UP), "in either half")
    }
}
