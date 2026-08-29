package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.rules.ArmorStats

/** The engine's view of an armor row (plan D11): base AC, whether Dex applies, and its cap. Null for anything that is not armor. */
fun EquipmentRecord.armorStats(): ArmorStats? =
    armor?.let { ArmorStats(base = it.base, dexBonus = it.dexBonus, maxBonus = it.maxBonus) }

/**
 * The seam between the compendium and `rules/` (plan D11): Derive takes an armor table keyed by equipment
 * key, and the 13 armor rows are decoded from their `json` column on each sheet load rather than stored in
 * columns. RulesBridgeTest pins this against the fixture generator's reading of the same file.
 */
object RulesBridge {
    /** Equipment records with an armor block, keyed by equipment key; records of other kinds are ignored. */
    fun armorTable(records: List<CompendiumRecord>): Map<String, ArmorStats> {
        val table = LinkedHashMap<String, ArmorStats>()
        for (record in records) {
            if (record !is EquipmentRecord) continue
            val stats = record.armorStats() ?: continue
            table[record.key] = stats
        }
        return table
    }
}
