package dev.tyler.grimoire.rules

import dev.tyler.grimoire.Fixtures
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * ROADMAP M1: `Model.kt` round-trips every sample character under fixtures/characters byte-for-byte after
 * normalisation. (Kotlin nests block comments, so the glob is spelled out in words here.)
 *
 * Normalisation = drop null-valued keys on both sides and compare JSON trees (key order and whitespace do
 * not matter). The hand-authored fixtures omit some schema-optional keys and spell others out as `null`;
 * `RulesJson` treats absent and null alike (`explicitNulls = false`), exactly as the Python oracle does
 * with `dict.get`. What survives the comparison is every non-null value, every key, every enum spelling.
 *
 * Note for fixture authors: `Model.encode` writes every defaulted key, so a sample character may omit a
 * nullable key but must spell out non-null defaulted ones (`speed`, `currency`, `notes`, `meta`…) or this
 * test reports the omission — that is the fixture being incomplete, not the codec being wrong.
 */
class ModelRoundTripTest {
    private val characters = listOf("cleric-5-life", "paladin-6-warlock-2", "rogue-3-thief")

    private val minimal = """
        {"schemaVersion":1,"id":"min","name":"Minimal","edition":"2014",
         "classes":[{"classKey":"fighter","level":1}],
         "abilities":{"str":10,"dex":10,"con":10,"int":10,"wis":10,"cha":10},
         "hp":{"max":10,"damage":0,"temp":0},"hitDice":[{"die":10,"total":1,"used":0}]}
    """.trimIndent()

    @Test
    fun everyFixtureCharacterSurvivesDecodeThenEncode() {
        for (name in characters) {
            val raw = Fixtures.character(name)
            val encoded = Model.encode(Model.decode(raw))
            assertEquals(normalise(RulesJson.parseToJsonElement(raw)), normalise(RulesJson.parseToJsonElement(encoded)), "round trip $name")
        }
    }

    @Test
    fun decodingIsStableAcrossASecondRoundTrip() {
        for (name in characters) {
            val once = Model.decode(Fixtures.character(name))
            val twice = Model.decode(Model.encode(once))
            assertEquals(once, twice, "second decode $name")
        }
    }

    @Test
    fun onlyTheSchemaRequiredKeysAreNeededToDecode() {
        val c = Model.decode(minimal)
        assertEquals(Ac.Manual(10), c.ac, "default ac")
        assertEquals(30, c.speed, "default speed")
        assertEquals(0, c.exhaustion, "default exhaustion")
        assertEquals(null, c.spellcasting, "default spellcasting")
        assertEquals(null, c.concentration, "default concentration")
        assertEquals(DeathSaves(), c.deathSaves, "default death saves")
        assertEquals(emptyList(), c.counters, "default counters")
        assertEquals(Currency(), c.currency, "default currency")
        assertEquals(Meta(), c.meta, "default meta")
    }

    @Test
    fun aStrayKeyIsRejectedBecauseTheSchemaForbidsAdditionalProperties() {
        val stray = minimal.replaceFirst("\"id\":\"min\"", "\"id\":\"min\",\"favourite\":\"blue\"")
        assertFailsWith<SerializationException>("stray key") { Model.decode(stray) }
    }

    @Test
    fun anUnknownSchemaVersionIsRefusedByMigrate() {
        val future = minimal.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2")
        assertFailsWith<RulesException>("schemaVersion 2") { Model.decode(future) }
    }

    /** Drop null-valued object members recursively; arrays and scalars pass through. */
    private fun normalise(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.filterValues { it !is JsonNull }.mapValues { normalise(it.value) })
        is JsonArray -> JsonArray(element.map(::normalise))
        else -> element
    }
}
