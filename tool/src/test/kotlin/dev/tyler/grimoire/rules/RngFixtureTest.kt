package dev.tyler.grimoire.rules

import dev.tyler.grimoire.Fixtures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Replays fixtures/rng.json: the first outputs of Mulberry32 for six seeds, as raw 32-bit words and as
 * d20 / d6 streams. Seeds and words are unsigned 32-bit in the fixture, so they are decoded as Long.
 * Decoding is strict (every fixture key is declared) so a new field in the fixture fails here, loudly.
 */
class RngFixtureTest {
    @Serializable
    private data class Case(val seed: Long, val u32: List<Long>, val d20: List<Int>, val d6: List<Int>)

    @Serializable
    private data class Fixture(@SerialName("\$comment") val comment: String, val cases: List<Case>)

    private val cases = Json.decodeFromString(Fixture.serializer(), Fixtures.text("rng.json")).cases

    @Test
    fun rawWordsMatchTheOracleForEverySeed() {
        for (case in cases) {
            val rng = Mulberry32(case.seed)
            val words = List(case.u32.size) { rng.nextU32().toLong() and 0xFFFFFFFFL }
            assertEquals(case.u32, words, "u32 stream for seed ${case.seed}")
        }
    }

    @Test
    fun d20StreamMatchesFromAFreshGenerator() {
        for (case in cases) {
            val rng = Mulberry32(case.seed)
            assertEquals(case.d20, List(case.d20.size) { rng.die(20) }, "d20 stream for seed ${case.seed}")
        }
    }

    @Test
    fun d6StreamMatchesFromAFreshGenerator() {
        for (case in cases) {
            val rng = Mulberry32(case.seed)
            assertEquals(case.d6, List(case.d6.size) { rng.die(6) }, "d6 stream for seed ${case.seed}")
        }
    }

    @Test
    fun fixtureCoversTheSixPinnedSeedsIncludingTheUnsignedOnes() {
        assertEquals(listOf(0L, 1L, 42L, 2024L, 0xDEADBEEFL, 0xFFFFFFFFL), cases.map { it.seed }, "seeds")
    }
}
