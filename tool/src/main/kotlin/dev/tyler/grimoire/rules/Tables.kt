package dev.tyler.grimoire.rules

import kotlinx.serialization.Serializable

/** Warlock Pact Magic: [count] slots, every one of [level]. Never merged with regular slots. */
@Serializable
data class PactSlots(val count: Int, val level: Int)

/** Spell slot maxima for spell levels 1..9 plus Pact Magic — derived from the classes, never stored. */
@Serializable
data class SlotMaxima(val slots: List<Int>, val pact: PactSlots?)

/**
 * The SRD 5.1 class tables the engine needs, mirrored from pipeline/reference/rules.py and cross-checked
 * against the bundled compendium (classes.json, skills.json) in TablesCompendiumTest.
 */
object Tables {
    /** Caster progression per SRD class; "third" exists only for custom subclasses (`ClassEntry.custom`). */
    val CASTER_TYPE: Map<String, CasterType> = mapOf(
        "bard" to CasterType.FULL,
        "cleric" to CasterType.FULL,
        "druid" to CasterType.FULL,
        "sorcerer" to CasterType.FULL,
        "wizard" to CasterType.FULL,
        "paladin" to CasterType.HALF,
        "ranger" to CasterType.HALF,
        "warlock" to CasterType.PACT,
        "barbarian" to CasterType.NONE,
        "fighter" to CasterType.NONE,
        "monk" to CasterType.NONE,
        "rogue" to CasterType.NONE,
    )

    val SPELLCASTING_ABILITY: Map<String, Ability> = mapOf(
        "bard" to Ability.CHA,
        "cleric" to Ability.WIS,
        "druid" to Ability.WIS,
        "paladin" to Ability.CHA,
        "ranger" to Ability.WIS,
        "sorcerer" to Ability.CHA,
        "warlock" to Ability.CHA,
        "wizard" to Ability.INT,
    )

    val HIT_DIE: Map<String, Int> = mapOf(
        "barbarian" to 12,
        "fighter" to 10,
        "paladin" to 10,
        "ranger" to 10,
        "bard" to 8,
        "cleric" to 8,
        "druid" to 8,
        "monk" to 8,
        "rogue" to 8,
        "warlock" to 8,
        "sorcerer" to 6,
        "wizard" to 6,
    )

    /** SRD 5.1 skills and their governing ability, in the oracle's order. */
    val SKILLS: Map<String, Ability> = mapOf(
        "acrobatics" to Ability.DEX,
        "animal-handling" to Ability.WIS,
        "arcana" to Ability.INT,
        "athletics" to Ability.STR,
        "deception" to Ability.CHA,
        "history" to Ability.INT,
        "insight" to Ability.WIS,
        "intimidation" to Ability.CHA,
        "investigation" to Ability.INT,
        "medicine" to Ability.WIS,
        "nature" to Ability.INT,
        "perception" to Ability.WIS,
        "performance" to Ability.CHA,
        "persuasion" to Ability.CHA,
        "religion" to Ability.INT,
        "sleight-of-hand" to Ability.DEX,
        "stealth" to Ability.DEX,
        "survival" to Ability.WIS,
    )

    /** Spell slots per spell level by caster level 1..20 (the Multiclass Spellcaster table = every full-caster table). */
    val FULL_CASTER_SLOTS: List<List<Int>> = listOf(
        listOf(2, 0, 0, 0, 0, 0, 0, 0, 0),
        listOf(3, 0, 0, 0, 0, 0, 0, 0, 0),
        listOf(4, 2, 0, 0, 0, 0, 0, 0, 0),
        listOf(4, 3, 0, 0, 0, 0, 0, 0, 0),
        listOf(4, 3, 2, 0, 0, 0, 0, 0, 0),
        listOf(4, 3, 3, 0, 0, 0, 0, 0, 0),
        listOf(4, 3, 3, 1, 0, 0, 0, 0, 0),
        listOf(4, 3, 3, 2, 0, 0, 0, 0, 0),
        listOf(4, 3, 3, 3, 1, 0, 0, 0, 0),
        listOf(4, 3, 3, 3, 2, 0, 0, 0, 0),
        listOf(4, 3, 3, 3, 2, 1, 0, 0, 0),
        listOf(4, 3, 3, 3, 2, 1, 0, 0, 0),
        listOf(4, 3, 3, 3, 2, 1, 1, 0, 0),
        listOf(4, 3, 3, 3, 2, 1, 1, 0, 0),
        listOf(4, 3, 3, 3, 2, 1, 1, 1, 0),
        listOf(4, 3, 3, 3, 2, 1, 1, 1, 0),
        listOf(4, 3, 3, 3, 2, 1, 1, 1, 1),
        listOf(4, 3, 3, 3, 3, 1, 1, 1, 1),
        listOf(4, 3, 3, 3, 3, 2, 1, 1, 1),
        listOf(4, 3, 3, 3, 3, 2, 2, 1, 1),
    )

    /** Pact Magic (slot count, slot level) by warlock level 1..20. */
    val PACT_SLOTS: List<PactSlots> = listOf(
        PactSlots(1, 1), PactSlots(2, 1), PactSlots(2, 2), PactSlots(2, 2), PactSlots(2, 3),
        PactSlots(2, 3), PactSlots(2, 4), PactSlots(2, 4), PactSlots(2, 5), PactSlots(2, 5),
        PactSlots(3, 5), PactSlots(3, 5), PactSlots(3, 5), PactSlots(3, 5), PactSlots(3, 5),
        PactSlots(3, 5), PactSlots(4, 5), PactSlots(4, 5), PactSlots(4, 5), PactSlots(4, 5),
    )

    /** The fixed hit-point gain per level after the first, by hit die (die/2 + 1). */
    val AVERAGE_HIT_DIE: Map<Int, Int> = mapOf(6 to 4, 8 to 5, 10 to 6, 12 to 7)

    private val NO_SLOTS: List<Int> = List(9) { 0 }

    fun casterType(classKey: String, custom: CustomClass?): CasterType =
        CASTER_TYPE[classKey] ?: custom?.casterType ?: CasterType.NONE

    fun hitDieFor(classKey: String, custom: CustomClass?): Int =
        HIT_DIE[classKey] ?: custom?.hitDie
            ?: throw RulesException("unknown class '$classKey': custom classes must declare hitDie")

    /**
     * Slot maxima for a set of classes.
     *
     * SRD 5.1 Multiclassing, Spellcasting: with the Spellcasting feature from ONE class (single-class, or
     * multiclassed with non-casters or a warlock) that class's own table applies — half casters use the
     * full table at ceil(L/2) from level 2, third casters at ceil(L/3) from level 3. With Spellcasting
     * from two or more classes the caster level is full + floor(half/2) + floor(third/3) on the shared
     * table, so a Paladin 3 / Wizard 1 is caster level 2. Pact Magic is separate and never combines.
     */
    fun spellSlots(classes: List<ClassEntry>): SlotMaxima {
        if (classes.isEmpty()) return SlotMaxima(NO_SLOTS, null)
        var pact: PactSlots? = null
        for (c in classes) {
            if (casterType(c.classKey, c.custom) == CasterType.PACT) pact = PACT_SLOTS[c.level - 1]
        }
        val slotClasses = classes.filter { casterType(it.classKey, it.custom).isSpellcasting }
        if (slotClasses.isEmpty()) return SlotMaxima(NO_SLOTS, pact)
        val casterLevel = if (slotClasses.size == 1) {
            val c = slotClasses.single()
            when (casterType(c.classKey, c.custom)) {
                CasterType.FULL -> c.level
                CasterType.HALF -> if (c.level < 2) 0 else (c.level + 1) / 2
                else -> if (c.level < 3) 0 else (c.level + 2) / 3
            }
        } else {
            slotClasses.sumOf { c ->
                when (casterType(c.classKey, c.custom)) {
                    CasterType.FULL -> c.level
                    CasterType.HALF -> c.level / 2
                    else -> c.level / 3
                }
            }
        }
        val slots = if (casterLevel > 0) FULL_CASTER_SLOTS[casterLevel - 1] else NO_SLOTS
        return SlotMaxima(slots, pact)
    }

    private val CasterType.isSpellcasting: Boolean
        get() = this == CasterType.FULL || this == CasterType.HALF || this == CasterType.THIRD
}
