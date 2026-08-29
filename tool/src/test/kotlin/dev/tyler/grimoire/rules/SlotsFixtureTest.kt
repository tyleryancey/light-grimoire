package dev.tyler.grimoire.rules

import dev.tyler.grimoire.Fixtures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Replays fixtures/slots.json: every SRD class at every level (single) and the multiclass rows, including
 * the custom third-caster rows that are the only place `ClassEntry.custom` is exercised.
 */
class SlotsFixtureTest {
    @Serializable
    private data class Case(val classes: List<ClassEntry>, val slots: List<Int>, val pact: PactSlots?)

    @Serializable
    private data class Fixture(@SerialName("\$comment") val comment: String, val single: List<Case>, val multiclass: List<Case>)

    private val fixture = Json.decodeFromString(Fixture.serializer(), Fixtures.text("slots.json"))

    private fun label(classes: List<ClassEntry>) = classes.joinToString("/") { "${it.classKey}${it.level}" }

    @Test
    fun everySingleClassRowMatchesItsClassTable() {
        assertEquals(240, fixture.single.size, "12 classes x 20 levels")
        for (case in fixture.single) {
            assertEquals(SlotMaxima(case.slots, case.pact), Tables.spellSlots(case.classes), "slots ${label(case.classes)}")
        }
    }

    @Test
    fun everyMulticlassRowFollowsTheSpellcasterRule() {
        assertEquals(11, fixture.multiclass.size, "multiclass rows")
        for (case in fixture.multiclass) {
            assertEquals(SlotMaxima(case.slots, case.pact), Tables.spellSlots(case.classes), "slots ${label(case.classes)}")
        }
    }

    @Test
    fun spellcastingFromOneClassKeepsThatClassTableEvenWhenMulticlassed() {
        // Paladin 3 alone and with a non-caster: three 1st-level slots. With a Wizard 1 the multiclass
        // rule applies (floor(3/2) + 1 = caster level 2): still three 1st-level slots but from the shared table.
        val paladin3 = ClassEntry("paladin", level = 3)
        assertEquals(3, Tables.spellSlots(listOf(paladin3)).slots[0], "paladin 3")
        assertEquals(3, Tables.spellSlots(listOf(paladin3, ClassEntry("fighter", level = 1))).slots[0], "paladin 3 fighter 1")
        assertEquals(listOf(3, 0, 0, 0, 0, 0, 0, 0, 0), Tables.spellSlots(listOf(paladin3, ClassEntry("wizard", level = 1))).slots, "paladin 3 wizard 1")
        assertEquals(listOf(4, 2, 0, 0, 0, 0, 0, 0, 0), Tables.spellSlots(listOf(ClassEntry("paladin", level = 5), ClassEntry("cleric", level = 1))).slots, "paladin 5 cleric 1")
        assertEquals(Tables.FULL_CASTER_SLOTS[7], Tables.spellSlots(listOf(ClassEntry("wizard", level = 5), ClassEntry("cleric", level = 3))).slots, "wizard 5 cleric 3")
    }

    @Test
    fun noClassesMeansNoSlots() {
        assertEquals(SlotMaxima(List(9) { 0 }, null), Tables.spellSlots(emptyList()), "no classes")
    }

    @Test
    fun aCustomClassWithoutACasterTypeDoesNotCast() {
        val homebrew = ClassEntry("homebrew", level = 5, custom = CustomClass(hitDie = 8))
        assertEquals(CasterType.NONE, Tables.casterType(homebrew.classKey, homebrew.custom), "custom caster type")
        assertEquals(SlotMaxima(List(9) { 0 }, null), Tables.spellSlots(listOf(homebrew)), "custom slots")
    }
}
