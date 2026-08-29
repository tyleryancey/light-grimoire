package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.ArmorStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Plan D11: the armor table Derive needs is decoded from the 13 armor rows, never stored in columns. Tying
 * the bridge to [Fixtures.armorTable] (the fixture generator's reading of equipment.json) ties M2's
 * compendium to M1's replayed fixtures.
 */
class RulesBridgeTest {
    private val equipment: List<CompendiumRecord> = Kind.EQUIPMENT.decodeAll(Fixtures.compendium(Kind.EQUIPMENT.file))

    @Test
    fun armorTableMatchesTheFixtureGeneratorsReadingOfEquipment() {
        val table = RulesBridge.armorTable(equipment)
        assertEquals(Fixtures.armorTable(), table, "armor table from the decoded records")
        assertEquals(13, table.size, "armor rows")
        assertEquals(ArmorStats(base = 16, dexBonus = false, maxBonus = null), table["chain-mail"], "chain-mail")
        assertEquals(ArmorStats(base = 11, dexBonus = true, maxBonus = null), table["leather-armor"], "leather armor")
        assertEquals(ArmorStats(base = 12, dexBonus = true, maxBonus = null), table["studded-leather-armor"], "studded leather armor")
        assertEquals(ArmorStats(base = 14, dexBonus = true, maxBonus = 2), table["scale-mail"], "scale mail caps dex at 2")
        assertEquals(ArmorStats(base = 2, dexBonus = false, maxBonus = null), table["shield"], "the shield is an armor row too")
    }

    @Test
    fun onlyArmorEquipmentHasArmorStats() {
        val longsword = assertIs<EquipmentRecord>(equipment.single { it.key == "longsword" }, "longsword is equipment")
        assertNull(longsword.armorStats(), "a weapon has no armor stats")
        val rope = assertIs<EquipmentRecord>(equipment.single { it.key == "rope-hempen-50-feet" }, "rope is equipment")
        assertNull(rope.armorStats(), "gear has no armor stats")
        val chainMail = assertIs<EquipmentRecord>(equipment.single { it.key == "chain-mail" }, "chain mail is equipment")
        assertEquals(ArmorStats(base = 16, dexBonus = false, maxBonus = null), chainMail.armorStats(), "chain mail armor stats")
        for (r in equipment) {
            val e = assertIs<EquipmentRecord>(r, "${r.key} is equipment")
            assertEquals(e.armor != null, e.armorStats() != null, "${e.key} has armor stats iff it has an armor block")
        }
    }

    @Test
    fun armorTableIgnoresRecordsOfOtherKinds() {
        val spells = Kind.SPELLS.decodeAll(Fixtures.compendium(Kind.SPELLS.file))
        assertEquals(emptyMap(), RulesBridge.armorTable(spells), "spells contribute no armor")
        assertEquals(Fixtures.armorTable(), RulesBridge.armorTable(spells + equipment), "mixed input yields the armor rows only")
        assertEquals(emptyMap(), RulesBridge.armorTable(emptyList()), "no records, no armor")
    }
}
