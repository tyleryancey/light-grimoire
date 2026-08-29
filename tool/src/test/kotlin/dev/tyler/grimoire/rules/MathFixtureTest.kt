package dev.tyler.grimoire.rules

import dev.tyler.grimoire.Fixtures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Replays fixtures/math.json: ability modifiers, proficiency bonus, the average-hit-die table and HP maxima. */
class MathFixtureTest {
    @Serializable
    private data class ModCase(val score: Int, val mod: Int)

    @Serializable
    private data class ProfCase(val level: Int, val bonus: Int)

    @Serializable
    private data class HpCase(val classes: List<ClassEntry>, val con: Int, val hp: Int)

    @Serializable
    private data class Fixture(
        @SerialName("\$comment") val comment: String,
        val abilityMod: List<ModCase>,
        val proficiencyBonus: List<ProfCase>,
        val averageHitDie: Map<String, Int>,
        val hpMaxAverage: List<HpCase>,
    )

    private val fixture = Json.decodeFromString(Fixture.serializer(), Fixtures.text("math.json"))

    @Test
    fun abilityModifierFloorsForEveryScoreOneToThirty() {
        assertEquals(30, fixture.abilityMod.size, "score coverage")
        for (case in fixture.abilityMod) {
            assertEquals(case.mod, Derive.abilityMod(case.score), "modifier for score ${case.score}")
        }
    }

    @Test
    fun proficiencyBonusForEveryLevel() {
        assertEquals(20, fixture.proficiencyBonus.size, "level coverage")
        for (case in fixture.proficiencyBonus) {
            assertEquals(case.bonus, Derive.proficiencyBonus(case.level), "proficiency at level ${case.level}")
        }
    }

    @Test
    fun proficiencyBonusRejectsLevelsOutsideOneToTwenty() {
        for (level in listOf(0, 21)) {
            val error = assertFailsWith<RulesException>("level $level") { Derive.proficiencyBonus(level) }
            assertEquals("level must be 1..20", error.message, "message for level $level")
        }
    }

    @Test
    fun averageHitDieTableMatches() {
        // The oracle keys this table by int; JSON forces the keys to strings, so read them back as ints.
        assertEquals(fixture.averageHitDie.mapKeys { it.key.toInt() }, Tables.AVERAGE_HIT_DIE, "average hit die")
    }

    @Test
    fun hpMaximumByTheAverageMethodForEveryCase() {
        for (case in fixture.hpMaxAverage) {
            val classes = case.classes.joinToString("/") { "${it.classKey}${it.level}" }
            assertEquals(case.hp, Derive.hpMaxAverage(case.classes, case.con), "hp max $classes con ${case.con}")
        }
    }

    @Test
    fun everyLevelGrantsAtLeastOneHitPoint() {
        // Wizard with CON 1 (-5): level 1 is max(1, 6 - 5), each later level max(1, 4 - 5) — three in all.
        assertEquals(3, Derive.hpMaxAverage(listOf(ClassEntry("wizard", level = 3)), 1), "minimum 1 per level")
    }

    @Test
    fun aCustomClassMustDeclareItsHitDie() {
        val undeclared = listOf(ClassEntry("homebrew", level = 2))
        val error = assertFailsWith<RulesException>("custom without hitDie") { Derive.hpMaxAverage(undeclared, 10) }
        assertEquals("unknown class 'homebrew': custom classes must declare hitDie", error.message, "message")
        val declared = listOf(ClassEntry("homebrew", level = 2, custom = CustomClass(hitDie = 10)))
        assertEquals(10 + 6, Derive.hpMaxAverage(declared, 10), "custom d10 at CON 10")
    }
}
