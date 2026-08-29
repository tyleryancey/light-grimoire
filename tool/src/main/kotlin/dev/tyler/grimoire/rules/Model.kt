package dev.tyler.grimoire.rules

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The stored character — a one-to-one mirror of pipeline/schema/character.schema.json (docs/DATA-MODEL.md).
 *
 * Only what a paper sheet records is stored; everything the sheet *shows* that can be computed (modifiers,
 * saves, AC in computed mode, current HP, spell DC, slot maxima, attack bonuses) is derived by [Derive] on
 * every read and never persisted. Invariants the rest of the code keeps: `0 <= hp.damage <= hp.max`,
 * `hp.temp >= 0`, counters stay within `[0, max]`, `slotsUsed[i] <= slotsMax[i]` (clamped on edit).
 *
 * Nullable properties always default to `null` — never to a non-null value — because [RulesJson] writes
 * nothing for a null and reads a missing key as null. That keeps a stored character lossless across
 * save/reload while letting hand-written JSON omit optional keys, exactly like the Python oracle's `dict.get`.
 */
@Serializable
data class Character(
    val schemaVersion: Int = Model.SCHEMA_VERSION,
    val id: String,
    val name: String,
    /** Schema-required but defaulted like [schemaVersion]: "2014" is the only legal value today. */
    val edition: String = "2014",
    val classes: List<ClassEntry>,
    val race: Race? = null,
    val background: Background? = null,
    /** FINAL scores, racial bonuses already applied — what the paper sheet shows. */
    val abilities: Abilities,
    val saveProficiencies: List<Ability> = emptyList(),
    /** Compendium skill key to proficiency level; a missing key means [SkillLevel.NONE]. */
    val skills: Map<String, SkillLevel> = emptyMap(),
    val profBonusOverride: Int? = null,
    val ac: Ac = Ac.Manual(10),
    val speed: Int = 30,
    val initiativeBonus: Int = 0,
    val hp: Hp,
    val hitDice: List<HitDicePool>,
    val deathSaves: DeathSaves = DeathSaves(),
    /** Compendium condition keys; exhaustion is tracked separately as a level. */
    val conditions: List<String> = emptyList(),
    val exhaustion: Int = 0,
    val concentration: Concentration? = null,
    val inspiration: Boolean = false,
    val counters: List<Counter> = emptyList(),
    val spellcasting: Spellcasting? = null,
    val attacks: List<Attack> = emptyList(),
    val items: List<Item> = emptyList(),
    val currency: Currency = Currency(),
    val notes: List<Note> = emptyList(),
    val meta: Meta = Meta(),
)

@Serializable
data class ClassEntry(
    /** Compendium class key, or a custom slug for a non-SRD class (then [custom] must declare the hit die). */
    val classKey: String,
    val level: Int,
    val subclassKey: String? = null,
    /** Non-SRD subclasses are a name only; [subclassKey] stays null. */
    val customSubclassName: String? = null,
    val custom: CustomClass? = null,
)

@Serializable
data class CustomClass(
    val hitDie: Int? = null,
    val casterType: CasterType? = null,
    val spellcastingAbility: String? = null,
)

@Serializable
data class Race(val key: String? = null, val subraceKey: String? = null, val name: String = "")

@Serializable
data class Background(val key: String? = null, val name: String = "")

@Serializable
data class Abilities(val str: Int, val dex: Int, val con: Int, val int: Int, val wis: Int, val cha: Int) {
    operator fun get(ability: Ability): Int = when (ability) {
        Ability.STR -> str
        Ability.DEX -> dex
        Ability.CON -> con
        Ability.INT -> int
        Ability.WIS -> wis
        Ability.CHA -> cha
    }
}

/**
 * Current HP is `max - damage` and is never stored, so a mis-tap can never lose the maximum. All three
 * fields are schema-required and deliberately have no defaults: a character JSON missing `damage` must
 * fail to load rather than quietly read as unhurt.
 */
@Serializable
data class Hp(val max: Int, val damage: Int, val temp: Int)

/** One pool per die size; rests treat hit dice by size. All fields are schema-required. */
@Serializable
data class HitDicePool(val die: Int, val total: Int, val used: Int)

@Serializable
data class DeathSaves(
    val successes: Int = 0,
    val failures: Int = 0,
    val stable: Boolean = false,
    val dead: Boolean = false,
)

@Serializable
data class Concentration(val spellKey: String? = null, val name: String = "")

/** The one primitive for every limited-use resource (ADR-0003). */
@Serializable
data class Counter(
    val id: String,
    val name: String,
    val value: Int,
    val max: Int,
    val reset: ResetTrigger,
    val source: String? = null,
    /** Set when the counter was seeded from a compendium feature. */
    val featureKey: String? = null,
    val showOnTurn: Boolean = false,
)

/** Slot maxima are derived from [Character.classes]; only what was spent is stored. */
@Serializable
data class Spellcasting(
    val ability: Ability? = null,
    val mode: SpellcastingMode = SpellcastingMode.NONE,
    val slotsUsed: List<Int> = List(9) { 0 },
    /** Null when the character has no Pact Magic. */
    val pactUsed: Int? = null,
    val spells: List<KnownSpell> = emptyList(),
    val dcOverride: Int? = null,
)

@Serializable
data class KnownSpell(
    val name: String,
    val prepared: Boolean,
    val key: String? = null,
    val alwaysPrepared: Boolean = false,
    val custom: Boolean = false,
    /** Required when [custom] — there is no compendium record to read the level from. */
    val level: Int? = null,
)

@Serializable
data class Attack(
    val id: String,
    val name: String,
    val group: AttackGroup,
    val ability: Ability,
    /** Dice notation without the ability bonus, e.g. `1d8`. */
    val damage: String,
    val proficient: Boolean = true,
    /** A stored to-hit that replaces the computed one; `0` is a real override, null means "compute". */
    val bonusOverride: Int? = null,
    val bonus: Int = 0,
    val damageType: String? = null,
    val damageBonusMode: DamageBonusMode = DamageBonusMode.ABILITY,
    val damageBonus: Int = 0,
    /** Counter spent when the attack is used (superiority dice and the like). */
    val counterId: String? = null,
)

@Serializable
data class Item(
    val id: String,
    val name: String,
    val qty: Int,
    val key: String? = null,
    val kind: ItemKind? = null,
    val equipped: Boolean = false,
    val attuned: Boolean = false,
    val custom: Boolean = false,
)

@Serializable
data class Currency(val cp: Int = 0, val sp: Int = 0, val ep: Int = 0, val gp: Int = 0, val pp: Int = 0)

@Serializable
data class Note(val title: String = "", val text: String = "")

@Serializable
data class Meta(val source: MetaSource = MetaSource.MANUAL, val createdAt: String? = null, val updatedAt: String? = null)

/** Armor class as transcribed (`manual`) or as a formula the engine evaluates (`computed`). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("mode")
sealed class Ac {
    @Serializable
    @SerialName("manual")
    data class Manual(val value: Int) : Ac()

    @Serializable
    @SerialName("computed")
    data class Computed(
        val formula: AcFormula,
        val armorKey: String? = null,
        val shield: Boolean = false,
        val bonus: Int = 0,
    ) : Ac()
}

@Serializable
enum class Ability {
    @SerialName("str") STR,
    @SerialName("dex") DEX,
    @SerialName("con") CON,
    @SerialName("int") INT,
    @SerialName("wis") WIS,
    @SerialName("cha") CHA,
}

@Serializable
enum class SkillLevel {
    @SerialName("none") NONE,
    @SerialName("half") HALF,
    @SerialName("proficient") PROFICIENT,
    @SerialName("expertise") EXPERTISE,
}

@Serializable
enum class ResetTrigger {
    @SerialName("short") SHORT,
    @SerialName("long") LONG,
    @SerialName("dawn") DAWN,
    @SerialName("none") NONE,
}

@Serializable
enum class CasterType {
    @SerialName("none") NONE,
    @SerialName("full") FULL,
    @SerialName("half") HALF,
    @SerialName("third") THIRD,
    @SerialName("pact") PACT,
}

@Serializable
enum class SpellcastingMode {
    @SerialName("prepared") PREPARED,
    @SerialName("known") KNOWN,
    @SerialName("pact") PACT,
    @SerialName("none") NONE,
}

@Serializable
enum class AttackGroup {
    @SerialName("action") ACTION,
    @SerialName("bonus") BONUS,
    @SerialName("reaction") REACTION,
    @SerialName("other") OTHER,
}

@Serializable
enum class DamageBonusMode {
    @SerialName("ability") ABILITY,
    @SerialName("none") NONE,
}

@Serializable
enum class ItemKind {
    @SerialName("equipment") EQUIPMENT,
    @SerialName("magic_item") MAGIC_ITEM,
}

@Serializable
enum class AcFormula {
    @SerialName("unarmored") UNARMORED,
    @SerialName("armor") ARMOR,
    @SerialName("unarmored-monk") UNARMORED_MONK,
    @SerialName("unarmored-barbarian") UNARMORED_BARBARIAN,
    @SerialName("mage-armor") MAGE_ARMOR,
}

@Serializable
enum class MetaSource {
    @SerialName("manual") MANUAL,
    @SerialName("quickbuild") QUICKBUILD,
    @SerialName("import") IMPORT,
}

/** Thrown for invalid rules input (the Python oracle's `ValueError`); messages mirror rules.py verbatim. */
class RulesException(message: String) : IllegalArgumentException(message)

/**
 * The one Json configuration for everything under rules/. Defaults are written out (a stored character is
 * self-describing), nulls are not (absent and null mean the same thing, as in the oracle), unknown keys are
 * an error (the schema says `additionalProperties: false`, so a stray key is a bug, not data).
 */
val RulesJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = false
}

/** Codec for [Character] plus the schema-version gate. */
object Model {
    const val SCHEMA_VERSION = 1

    fun decode(text: String): Character {
        val json = migrate(RulesJson.parseToJsonElement(text).jsonObject)
        return RulesJson.decodeFromJsonElement(Character.serializer(), json)
    }

    fun encode(character: Character): String = RulesJson.encodeToString(Character.serializer(), character)

    /**
     * Upgrades a stored character to [SCHEMA_VERSION]. Version 1 is current, so this is the identity; a
     * missing `schemaVersion` is read as 1. Bumping the version means adding a step here — old JSON
     * must always load (docs/DATA-MODEL.md).
     */
    fun migrate(json: JsonObject): JsonObject {
        val version = json["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        if (version != SCHEMA_VERSION) throw RulesException("unsupported schemaVersion $version (expected $SCHEMA_VERSION)")
        return json
    }
}
