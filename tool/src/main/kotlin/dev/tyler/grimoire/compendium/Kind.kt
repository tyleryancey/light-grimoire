package dev.tyler.grimoire.compendium

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/** The nine S13 Compendium rows (docs/UI-SPEC.md) plus the lookup kinds that only back other screens. */
enum class KindGroup {
    SPELLS,
    CONDITIONS,
    RULES,
    CLASSES_AND_FEATURES,
    RACES,
    BACKGROUNDS_AND_FEATS,
    EQUIPMENT,
    MAGIC_ITEMS,
    CREATURES,
    LOOKUP,
}

/**
 * The 22 bundled kinds in S13 order. [id] is the asset stem and the `records.kind` column; [serializer] is
 * the strict model the kind decodes with (plan D2). The declaration order is also the import order — RULES
 * comes before RULE_SECTIONS because a section's chapter is resolved from rules.json (plan D4).
 */
enum class Kind(val id: String, val group: KindGroup, val serializer: KSerializer<out CompendiumRecord>) {
    SPELLS("spells", KindGroup.SPELLS, SpellRecord.serializer()),
    CONDITIONS("conditions", KindGroup.CONDITIONS, TextRecord.serializer()),
    RULES("rules", KindGroup.RULES, RuleRecord.serializer()),
    RULE_SECTIONS("rule_sections", KindGroup.RULES, TextRecord.serializer()),
    CLASSES("classes", KindGroup.CLASSES_AND_FEATURES, ClassRecord.serializer()),
    SUBCLASSES("subclasses", KindGroup.CLASSES_AND_FEATURES, SubclassRecord.serializer()),
    FEATURES("features", KindGroup.CLASSES_AND_FEATURES, FeatureRecord.serializer()),
    RACES("races", KindGroup.RACES, RaceRecord.serializer()),
    SUBRACES("subraces", KindGroup.RACES, SubraceRecord.serializer()),
    TRAITS("traits", KindGroup.RACES, TraitRecord.serializer()),
    BACKGROUNDS("backgrounds", KindGroup.BACKGROUNDS_AND_FEATS, BackgroundRecord.serializer()),
    FEATS("feats", KindGroup.BACKGROUNDS_AND_FEATS, FeatRecord.serializer()),
    EQUIPMENT("equipment", KindGroup.EQUIPMENT, EquipmentRecord.serializer()),
    WEAPON_PROPERTIES("weapon_properties", KindGroup.EQUIPMENT, TextRecord.serializer()),
    MAGIC_ITEMS("magic_items", KindGroup.MAGIC_ITEMS, MagicItemRecord.serializer()),
    CREATURES("creatures", KindGroup.CREATURES, CreatureRecord.serializer()),
    SKILLS("skills", KindGroup.LOOKUP, SkillRecord.serializer()),
    LANGUAGES("languages", KindGroup.LOOKUP, LanguageRecord.serializer()),
    DAMAGE_TYPES("damage_types", KindGroup.LOOKUP, TextRecord.serializer()),
    MAGIC_SCHOOLS("magic_schools", KindGroup.LOOKUP, TextRecord.serializer()),
    ALIGNMENTS("alignments", KindGroup.LOOKUP, AlignmentRecord.serializer()),
    PROFICIENCIES("proficiencies", KindGroup.LOOKUP, ProficiencyRecord.serializer()),
    ;

    /** The asset path under `compendium/` and the key into `index.files`. */
    val file: String = "$id.json"

    /** Decodes a whole asset file (a JSON array) strictly. */
    fun decodeAll(text: String): List<CompendiumRecord> = decodeList(serializer, text)

    /** Decodes one record — a raw slice from [JsonArraySplit] or a `records.json` column. */
    fun decodeOne(json: String): CompendiumRecord = CompendiumJson.decodeFromString(serializer, json)

    companion object {
        private val BY_ID: Map<String, Kind> = entries.associateBy { it.id }

        /** The kind for a `records.kind` value; throws [IllegalArgumentException] for anything else. */
        fun byId(id: String): Kind = requireNotNull(BY_ID[id]) { "unknown compendium kind: $id" }

        private fun <T : CompendiumRecord> decodeList(serializer: KSerializer<T>, text: String): List<T> =
            CompendiumJson.decodeFromString(ListSerializer(serializer), text)
    }
}
