package dev.tyler.grimoire

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

/** Proves the JVM tests see the golden fixtures pipeline/ generates, without a committed copy. */
class FixturesSmokeTest {
    @Test
    fun everyGoldenFixtureIsReadableAndCarriesItsComment() {
        for (name in listOf("rng", "dice", "math", "slots", "derived", "events")) {
            val root = Json.parseToJsonElement(Fixtures.text("$name.json")).jsonObject
            assertTrue("\$comment" in root, "fixtures/$name.json has no \$comment — not a pipeline-generated fixture?")
        }
        assertTrue(Fixtures.text("characters/cleric-5-life.json").isNotBlank(), "sample character missing")
    }
}
