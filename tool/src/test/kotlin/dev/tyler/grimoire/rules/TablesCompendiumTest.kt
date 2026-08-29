package dev.tyler.grimoire.rules

import dev.tyler.grimoire.Fixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The constants in Tables.kt have a second source of truth besides the oracle: the bundled compendium
 * (classes.json, skills.json). The Python tests cross-check the oracle against it; this does the same for
 * the Kotlin tables so a typo in a hit die or a slot row cannot hide behind a fixture that shares it.
 */
class TablesCompendiumTest {
    private val classes = Json.parseToJsonElement(Fixtures.compendium("classes.json")).jsonArray.map { it.jsonObject }
    private val skills = Json.parseToJsonElement(Fixtures.compendium("skills.json")).jsonArray.map { it.jsonObject }

    @Test
    fun hitDiceAndCastingAbilitiesMatchTheBundledClasses() {
        assertEquals(12, classes.size, "SRD classes")
        for (c in classes) {
            val key = c.getValue("key").jsonPrimitive.content
            assertEquals(c.getValue("hitDie").jsonPrimitive.int, Tables.HIT_DIE[key], "hit die $key")
            val spellcasting = c["spellcasting"]?.takeUnless { it is JsonNull }?.jsonObject
            if (spellcasting == null) {
                assertEquals(null, Tables.SPELLCASTING_ABILITY[key], "$key does not cast")
            } else {
                val ability = Json.decodeFromJsonElement(Ability.serializer(), spellcasting.getValue("ability"))
                assertEquals(ability, Tables.SPELLCASTING_ABILITY[key], "casting ability $key")
            }
        }
    }

    @Test
    fun singleClassSlotsAndProficiencyMatchTheBundledLevelTables() {
        for (c in classes) {
            val key = c.getValue("key").jsonPrimitive.content
            for (row in c.getValue("levels").jsonArray.map { it.jsonObject }) {
                val level = row.getValue("level").jsonPrimitive.int
                val rowSlots = row.getValue("slots").jsonArray.map { it.jsonPrimitive.int }
                val got = Tables.spellSlots(listOf(ClassEntry(key, level = level)))
                if (key == "warlock") {
                    // The SRD Levels data lists pact slots in the slot-level column.
                    val pact = got.pact ?: error("warlock $level has no pact slots")
                    val fromPact = MutableList(9) { 0 }.also { it[pact.level - 1] = pact.count }
                    assertEquals(rowSlots, fromPact, "warlock pact slots at level $level")
                    assertEquals(List(9) { 0 }, got.slots, "warlock has no regular slots at level $level")
                } else {
                    assertEquals(rowSlots, got.slots, "$key slots at level $level")
                }
                assertEquals(row.getValue("profBonus").jsonPrimitive.int, Derive.proficiencyBonus(level), "$key proficiency at level $level")
            }
        }
    }

    @Test
    fun skillAbilitiesMatchTheBundledSkills() {
        val expected = skills.associate {
            it.getValue("key").jsonPrimitive.content to Json.decodeFromJsonElement(Ability.serializer(), it.getValue("ability"))
        }
        assertEquals(expected, Tables.SKILLS, "skill to ability")
    }
}
