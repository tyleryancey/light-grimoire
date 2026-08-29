package dev.tyler.grimoire.rules

import dev.tyler.grimoire.Fixtures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Replays fixtures/derived.json: the whole derived sheet of each sample character, with the armor table
 * taken from the bundled compendium exactly as the generator does.
 */
class DerivedFixtureTest {
    @Serializable
    private data class Case(val character: String, val derived: Derived)

    @Serializable
    private data class Fixture(@SerialName("\$comment") val comment: String, val cases: List<Case>)

    private val fixture = Json.decodeFromString(Fixture.serializer(), Fixtures.text("derived.json"))
    private val armor = Fixtures.armorTable()

    @Test
    fun everySampleCharacterDerivesTheOracleSheet() {
        assertEquals(3, fixture.cases.size, "sample characters")
        for (case in fixture.cases) {
            val character = Model.decode(Fixtures.character(case.character))
            assertEquals(case.derived, Derive.derive(character, armor), "derived ${case.character}")
        }
    }

    @Test
    fun hpStateIsTheHpBlockOfTheFullDerivation() {
        for (case in fixture.cases) {
            val character = Model.decode(Fixtures.character(case.character))
            assertEquals(case.derived.hp, Derive.hpState(character), "hp state ${case.character}")
        }
    }

    @Test
    fun theArmorTableHoldsTheThirteenSrdArmors() {
        assertEquals(13, armor.size, "armor rows")
        assertEquals(ArmorStats(base = 16, dexBonus = false, maxBonus = null), armor["chain-mail"], "chain mail")
        assertEquals(ArmorStats(base = 14, dexBonus = true, maxBonus = 2), armor["breastplate"], "breastplate")
        assertEquals(ArmorStats(base = 11, dexBonus = true, maxBonus = null), armor["leather-armor"], "leather")
    }

    @Test
    fun anUnknownArmorKeyIsAnError() {
        val cleric = Model.decode(Fixtures.character("cleric-5-life"))
        val exotic = cleric.copy(ac = Ac.Computed(AcFormula.ARMOR, armorKey = "mithral-plate"))
        val error = assertFailsWith<RulesException>("unknown armor") { Derive.derive(exotic, armor) }
        assertEquals("unknown armor 'mithral-plate'", error.message, "message")
    }

    @Test
    fun computedFormulasWithoutArmorFollowTheSrd() {
        // Rogue: DEX 17 (+3), CON 12 (+1), WIS 10 (+0). No table lookups needed for these formulas.
        val rogue = Model.decode(Fixtures.character("rogue-3-thief"))
        fun ac(ac: Ac) = Derive.derive(rogue.copy(ac = ac), emptyMap()).ac
        assertEquals(13, ac(Ac.Computed(AcFormula.UNARMORED)), "unarmored")
        assertEquals(13, ac(Ac.Computed(AcFormula.UNARMORED_MONK)), "monk")
        assertEquals(14, ac(Ac.Computed(AcFormula.UNARMORED_BARBARIAN)), "barbarian")
        assertEquals(16, ac(Ac.Computed(AcFormula.MAGE_ARMOR)), "mage armor")
        assertEquals(15, ac(Ac.Computed(AcFormula.UNARMORED, shield = true)), "unarmored + shield")
        assertEquals(14, ac(Ac.Computed(AcFormula.UNARMORED, bonus = 1)), "unarmored + ring")
        assertEquals(19, ac(Ac.Manual(19)), "manual")
    }

    @Test
    fun mediumArmorCapsTheDexterityBonus() {
        // Rogue in a breastplate: 14 + min(3, 2) = 16; in chain mail (heavy): flat 16; in leather: 11 + 3 = 14.
        val rogue = Model.decode(Fixtures.character("rogue-3-thief"))
        fun wearing(key: String) = Derive.derive(rogue.copy(ac = Ac.Computed(AcFormula.ARMOR, armorKey = key)), armor).ac
        assertEquals(16, wearing("breastplate"), "breastplate")
        assertEquals(16, wearing("chain-mail"), "chain mail")
        assertEquals(14, wearing("leather-armor"), "leather")
    }
}
