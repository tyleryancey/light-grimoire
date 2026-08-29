package dev.tyler.grimoire.rules

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/** What the engine needs from a compendium armor row: the base AC and how DEX applies. */
@Serializable
data class ArmorStats(val base: Int, val dexBonus: Boolean, val maxBonus: Int?)

@Serializable
data class DerivedHp(val current: Int, val max: Int, val temp: Int, val bloodied: Boolean, val down: Boolean)

@Serializable
data class DerivedSpellcasting(
    val ability: Ability?,
    val saveDc: Int?,
    val attackBonus: Int?,
    val slotsMax: List<Int>,
    val pact: PactSlots?,
)

/** An attack row as the Turn screen rolls it: to-hit bonus and the full damage expression. */
@Serializable
data class DerivedAttack(val id: String, val name: String, val toHit: Int, val damage: String, val damageType: String?)

/** Everything the sheet shows that is not stored. Recomputed on every read; never persisted. */
@Serializable
data class Derived(
    val level: Int,
    val profBonus: Int,
    val abilityMods: Map<Ability, Int>,
    val saves: Map<Ability, Int>,
    val skills: Map<String, Int>,
    val passivePerception: Int,
    val initiative: Int,
    val ac: Int,
    val hp: DerivedHp,
    val spellcasting: DerivedSpellcasting,
    val attacks: List<DerivedAttack>,
)

/** Derived statistics — a port of `derive` and its helpers in pipeline/reference/rules.py. */
object Derive {
    /** floor((score - 10) / 2): 8 and 9 are -1, not 0 — hence floorDiv, never `/`. */
    fun abilityMod(score: Int): Int = Math.floorDiv(score - 10, 2)

    fun proficiencyBonus(level: Int): Int {
        if (level !in 1..20) throw RulesException("level must be 1..20")
        return 2 + (level - 1) / 4
    }

    fun totalLevel(character: Character): Int = character.classes.sumOf { it.level }

    /**
     * Hit point maximum by the average method: the first level of the first class takes the die's
     * maximum, every later level the fixed average (die/2 + 1); CON applies to each; never less than 1
     * per level.
     */
    fun hpMaxAverage(classes: List<ClassEntry>, conScore: Int): Int {
        val con = abilityMod(conScore)
        var total = 0
        var first = true
        for (c in classes) {
            val die = Tables.hitDieFor(c.classKey, c.custom)
            // Hit dice are schema-bounded to d6/d8/d10/d12; anything else is undefined here as in the oracle.
            val average = Tables.AVERAGE_HIT_DIE.getValue(die)
            repeat(c.level) {
                val gain = (if (first) die else average) + con
                first = false
                total += max(1, gain)
            }
        }
        return total
    }

    /**
     * Armor class for an [Ac] block. `manual` returns the transcribed value. Computed (2014):
     * unarmored 10 + DEX; armor: base + DEX (light) / base + min(DEX, max) (medium) / base (heavy);
     * unarmored-monk 10 + DEX + WIS; unarmored-barbarian 10 + DEX + CON; mage-armor 13 + DEX;
     * then +2 for a shield and the flat bonus (rings, cloaks, a fighting style).
     */
    fun armorClass(ac: Ac, mods: Map<Ability, Int>, armorTable: Map<String, ArmorStats>): Int {
        val computed = when (ac) {
            is Ac.Manual -> return ac.value
            is Ac.Computed -> ac
        }
        val dex = mods.getValue(Ability.DEX)
        var base = when (computed.formula) {
            AcFormula.UNARMORED -> 10 + dex
            AcFormula.UNARMORED_MONK -> 10 + dex + mods.getValue(Ability.WIS)
            AcFormula.UNARMORED_BARBARIAN -> 10 + dex + mods.getValue(Ability.CON)
            AcFormula.MAGE_ARMOR -> 13 + dex
            AcFormula.ARMOR -> {
                val key = computed.armorKey
                val armor = armorTable[key] ?: throw RulesException("unknown armor '$key'")
                when {
                    !armor.dexBonus -> armor.base
                    armor.maxBonus != null -> armor.base + min(dex, armor.maxBonus)
                    else -> armor.base + dex
                }
            }
        }
        if (computed.shield) base += 2
        return base + computed.bonus
    }

    /** The hit-point block alone: current HP is `max - damage`, floored at 0; bloodied means at or below half. */
    fun hpState(character: Character): DerivedHp {
        val hp = character.hp
        val current = max(0, hp.max - hp.damage)
        return DerivedHp(
            current = current,
            max = hp.max,
            temp = hp.temp,
            bloodied = current * 2 <= hp.max && current > 0,
            down = current == 0,
        )
    }

    fun derive(character: Character, armorTable: Map<String, ArmorStats>): Derived {
        val mods = Ability.entries.associateWith { abilityMod(character.abilities[it]) }
        val level = totalLevel(character)
        // The oracle's `override or computed` treats an override of 0 as absent; mirrored on purpose.
        val prof = character.profBonusOverride?.takeIf { it != 0 } ?: proficiencyBonus(level)
        val saveProficiencies = character.saveProficiencies.toSet()
        val saves = Ability.entries.associateWith { mods.getValue(it) + if (it in saveProficiencies) prof else 0 }
        val skills = Tables.SKILLS.mapValues { (skill, ability) ->
            val proficiency = when (character.skills[skill] ?: SkillLevel.NONE) {
                SkillLevel.NONE -> 0
                SkillLevel.HALF -> prof / 2
                SkillLevel.PROFICIENT -> prof
                SkillLevel.EXPERTISE -> 2 * prof
            }
            mods.getValue(ability) + proficiency
        }
        val passivePerception = 10 + skills.getValue("perception")
        val initiative = mods.getValue(Ability.DEX) + character.initiativeBonus

        // Spellcasting ability: explicit on the character, else the first class that casts.
        val castAbility = character.spellcasting?.ability
            ?: character.classes.firstNotNullOfOrNull { Tables.SPELLCASTING_ABILITY[it.classKey] }
        val spellDc = castAbility?.let { 8 + prof + mods.getValue(it) }
        val spellAttack = castAbility?.let { prof + mods.getValue(it) }
        val slots = Tables.spellSlots(character.classes)

        val attacks = character.attacks.map { attack ->
            val toHit = attack.bonusOverride
                ?: (mods.getValue(attack.ability) + (if (attack.proficient) prof else 0) + attack.bonus)
            val abilityBonus = if (attack.damageBonusMode == DamageBonusMode.ABILITY) mods.getValue(attack.ability) else 0
            val damageBonus = abilityBonus + attack.damageBonus
            val formula = attack.damage + when {
                damageBonus > 0 -> "+$damageBonus"
                damageBonus < 0 -> "$damageBonus"
                else -> ""
            }
            DerivedAttack(attack.id, attack.name, toHit, formula, attack.damageType)
        }

        return Derived(
            level = level,
            profBonus = prof,
            abilityMods = mods,
            saves = saves,
            skills = skills,
            passivePerception = passivePerception,
            initiative = initiative,
            ac = armorClass(character.ac, mods, armorTable),
            hp = hpState(character),
            spellcasting = DerivedSpellcasting(castAbility, spellDc, spellAttack, slots.slots, slots.pact),
            attacks = attacks,
        )
    }
}
