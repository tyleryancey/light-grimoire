package dev.tyler.grimoire.compendium

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Typed mirrors of the 22 bundled kinds (`.claude/skills/compendium-data`, pipeline/schema/compendium.schema.json),
 * one strict `@Serializable` model per kind; five prose-only kinds share [TextRecord]. The same models decode
 * the `json` column at read time, so there is no read-model drift (plan D2). Rules the models keep:
 *
 * - Every record carries the envelope `key, name, edition, source, license, xref` (surfaced through
 *   [CompendiumRecord]); `xref` is an upstream URL, never displayed.
 * - Nullable fields default to `null`, never to a value: [CompendiumJson] reads both an emitted `null` and an
 *   absent key as null.
 * - Sub-objects whose key set is open in the source (`classSpecific`, `featureSpecific`, `speed`, `senses`,
 *   `usage`) stay [JsonObject]; everything with a fixed shape is typed.
 * - No `ignoreUnknownKeys`: a field the pipeline starts emitting fails RecordsDecodeTest until it is added here.
 */
sealed interface CompendiumRecord {
    val key: String
    val name: String
    val edition: String
    val source: String
    val license: String
    val xref: String?
}

// ---- shared sub-models ------------------------------------------------------------------------------------

@Serializable
data class NamedText(val name: String, val text: String)

@Serializable
data class KeyQty(val key: String, val qty: Int)

@Serializable
data class DamageDice(val dice: String, val type: String)

@Serializable
data class AbilityBonus(val ability: String, val bonus: Int)

/** One prerequisite in any of its shapes: class multiclassing / feats (`ability`+`minimum`), features (`type`+…). */
@Serializable
data class Prerequisite(
    val type: String? = null,
    val level: Int? = null,
    val spell: String? = null,
    val feature: String? = null,
    val ability: String? = null,
    val minimum: Int? = null,
)

// ---- spells ------------------------------------------------------------------------------------------------

@Serializable
data class AreaOfEffect(val size: Int, val type: String)

@Serializable
data class SpellRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val level: Int,
    val school: String,
    val castingTime: String,
    val range: String,
    val components: List<String>,
    val material: String? = null,
    val ritual: Boolean,
    val concentration: Boolean,
    val duration: String,
    val classes: List<String>,
    val subclasses: List<String>,
    val attackType: String? = null,
    val saveAbility: String? = null,
    val saveSuccess: String? = null,
    val damageType: String? = null,
    /** Slot level ("1".."9") to dice text such as `"8d6"` or `"1d8 + MOD"`. */
    val damageAtSlotLevel: Map<String, String>? = null,
    val damageAtCharacterLevel: Map<String, String>? = null,
    val healAtSlotLevel: Map<String, String>? = null,
    val areaOfEffect: AreaOfEffect? = null,
    val text: String,
    val higherLevel: String,
) : CompendiumRecord

// ---- creatures ---------------------------------------------------------------------------------------------

@Serializable
data class Scores(val str: Int, val dex: Int, val con: Int, val int: Int, val wis: Int, val cha: Int)

@Serializable
data class AcEntry(val type: String, val value: Int)

@Serializable
data class SaveBlock(val ability: String, val dc: Int, val success: String)

/** A trait, action, reaction or legendary action; only `name` and `text` are always present. */
@Serializable
data class CreatureAction(
    val name: String,
    val text: String,
    val attackBonus: Int? = null,
    val damage: List<DamageDice>? = null,
    val save: SaveBlock? = null,
    val usage: JsonObject? = null,
)

@Serializable
data class CreatureRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val size: String,
    val type: String,
    val subtype: String? = null,
    val alignment: String,
    val ac: List<AcEntry>,
    val hp: Int,
    val hitDice: String,
    val hpRoll: String,
    /** `walk`/`fly`/`swim`/`burrow`/`climb` as text plus an optional `hover: true`. */
    val speed: JsonObject,
    val abilities: Scores,
    val saves: Map<String, Int>,
    val skills: Map<String, Int>,
    val vulnerabilities: List<String>,
    val resistances: List<String>,
    val immunities: List<String>,
    val conditionImmunities: List<String>,
    /** `passive_perception` (int) plus optional `darkvision`/`blindsight`/`truesight`/`tremorsense` text. */
    val senses: JsonObject,
    val languages: String,
    /** 0, 0.125, 0.25, 0.5, 1 … 30. */
    val cr: Double,
    val profBonus: Int,
    val xp: Int,
    val traits: List<CreatureAction>,
    val actions: List<CreatureAction>,
    val reactions: List<CreatureAction>,
    val legendaryActions: List<CreatureAction>,
    val text: String,
) : CompendiumRecord

// ---- classes, subclasses, features -----------------------------------------------------------------------

@Serializable
data class ProficiencyChoice(val choose: Int, val desc: String, val from: List<String>)

@Serializable
data class Multiclassing(
    val prerequisites: List<Prerequisite>,
    val prerequisiteOptions: String,
    val proficiencies: List<String>,
)

@Serializable
data class ClassSpellcasting(val ability: String, val startsAtLevel: Int, val info: List<NamedText>)

@Serializable
data class ClassLevel(
    val level: Int,
    val profBonus: Int,
    val abilityScoreBonuses: Int,
    val features: List<String>,
    /** Nine columns, slot levels 1–9 (warlock lists pact slots here — see TablesCompendiumTest). */
    val slots: List<Int>,
    val cantripsKnown: Int? = null,
    val spellsKnown: Int? = null,
    /** Per-class counters (`rage_count`, `ki_points`, …) — the wizard reads the keys, it does not memorise them. */
    val classSpecific: JsonObject,
)

@Serializable
data class ClassRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val hitDie: Int,
    val savingThrows: List<String>,
    val proficiencies: List<String>,
    val proficiencyChoices: List<ProficiencyChoice>,
    val startingEquipment: List<KeyQty>,
    val startingEquipmentOptions: List<String>,
    val multiclassing: Multiclassing,
    val spellcasting: ClassSpellcasting? = null,
    val subclasses: List<String>,
    val levels: List<ClassLevel>,
) : CompendiumRecord

@Serializable
data class SubclassLevel(val level: Int, val features: List<String>, val classSpecific: JsonObject)

@Serializable
data class SubclassSpell(val key: String, val prerequisites: List<Prerequisite>)

@Serializable
data class SubclassRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val classKey: String,
    val flavor: String,
    val levels: List<SubclassLevel>,
    val spells: List<SubclassSpell>,
    val text: String,
) : CompendiumRecord

@Serializable
data class FeatureRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val classKey: String,
    val subclassKey: String? = null,
    val level: Int,
    /** The feature this one is an option of (invocations, fighting styles, …). */
    val parentKey: String? = null,
    val featureSpecific: JsonObject,
    val prerequisites: List<Prerequisite>,
    val text: String,
) : CompendiumRecord

// ---- races, subraces, traits ---------------------------------------------------------------------------

@Serializable
data class RaceRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val speed: Int,
    val size: String,
    val abilityBonuses: List<AbilityBonus>,
    val abilityBonusOptions: String,
    val traits: List<String>,
    val subraces: List<String>,
    val languages: List<String>,
    val languageOptions: String,
    val startingProficiencies: List<String>,
    val startingProficiencyOptions: String,
    val text: String,
) : CompendiumRecord

@Serializable
data class SubraceRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val raceKey: String,
    val abilityBonuses: List<AbilityBonus>,
    val traits: List<String>,
    val languages: List<String>,
    val languageOptions: String,
    val startingProficiencies: List<String>,
    val text: String,
) : CompendiumRecord

@Serializable
data class TraitRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val parentKey: String? = null,
    val proficiencies: List<String>,
    val races: List<String>,
    val subraces: List<String>,
    val text: String,
) : CompendiumRecord

// ---- backgrounds, feats ----------------------------------------------------------------------------------

@Serializable
data class BackgroundRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val skillProficiencies: List<String>,
    val startingEquipment: List<KeyQty>,
    val startingEquipmentOptions: List<String>,
    val startingGold: Int,
    val languageOptions: String,
    val feature: NamedText,
    val personalityTraits: List<String>,
    val ideals: List<String>,
    val bonds: List<String>,
    val flaws: List<String>,
    val text: String,
) : CompendiumRecord

@Serializable
data class FeatRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val prerequisites: List<Prerequisite>,
    val text: String,
) : CompendiumRecord

// ---- equipment, magic items ------------------------------------------------------------------------------

@Serializable
data class Cost(val qty: Int, val unit: String)

@Serializable
data class Range(val normal: Int, val long: Int? = null)

@Serializable
data class Weapon(
    val category: String,
    val rangeType: String,
    /** Null for the net, which deals no damage. */
    val damage: DamageDice? = null,
    val twoHandedDamage: DamageDice? = null,
    val range: Range,
    val throwRange: Range? = null,
    val properties: List<String>,
)

@Serializable
data class Armor(
    val category: String,
    val base: Int,
    val dexBonus: Boolean,
    val maxBonus: Int? = null,
    val strMinimum: Int,
    val stealthDisadvantage: Boolean,
)

@Serializable
data class EquipmentRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val category: String,
    val cost: Cost? = null,
    val weight: Double? = null,
    val gearCategory: String? = null,
    val toolCategory: String? = null,
    val vehicleCategory: String? = null,
    val weapon: Weapon? = null,
    val armor: Armor? = null,
    val contents: List<KeyQty>? = null,
    val text: String,
) : CompendiumRecord

@Serializable
data class MagicItemRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val category: String,
    val rarity: String,
    val attunement: Boolean,
    val attunementBy: String? = null,
    val isVariant: Boolean,
    val variants: List<String>,
    /** "Wondrous item, rare (requires attunement)". */
    val headline: String,
    val text: String,
) : CompendiumRecord

// ---- lookup kinds ----------------------------------------------------------------------------------------

@Serializable
data class SkillRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val ability: String,
    val text: String,
) : CompendiumRecord

@Serializable
data class ProficiencyRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val type: String,
    val referenceKey: String,
    val classes: List<String>,
    val races: List<String>,
) : CompendiumRecord

@Serializable
data class LanguageRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val type: String,
    val script: String? = null,
    val typicalSpeakers: List<String>,
    val text: String,
) : CompendiumRecord

@Serializable
data class AlignmentRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val abbreviation: String,
    val text: String,
) : CompendiumRecord

// ---- rules and prose-only kinds --------------------------------------------------------------------------

/** A rules chapter; `sections` lists its rule_sections keys in reading order. */
@Serializable
data class RuleRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val sections: List<String>,
    val text: String,
) : CompendiumRecord

/** conditions, damage_types, magic_schools, rule_sections, weapon_properties: envelope plus text. */
@Serializable
data class TextRecord(
    override val key: String,
    override val name: String,
    override val edition: String,
    override val source: String,
    override val license: String,
    override val xref: String? = null,
    val text: String,
) : CompendiumRecord
