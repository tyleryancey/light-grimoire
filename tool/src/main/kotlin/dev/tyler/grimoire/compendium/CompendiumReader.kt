package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.rules.ArmorStats
import kotlinx.serialization.KSerializer

/**
 * The typed read facade every screen and repository goes through (plan §Files). The DAO hands out rows;
 * this class turns a row's `json` column back into the kind's model with the same strict codec the
 * importer decoded it with (plan D2), restores the caller's order for batch fetches, feeds Derive its
 * armor table (plan D11) and composes the two search queries with the pure ranking (plan D9). Pure apart
 * from the DAO it wraps, so CompendiumReaderTest runs it over a fake on the JVM.
 */
class CompendiumReader(private val dao: CompendiumDao) {
    companion object {
        /** The bound on a batch fetch — the largest bounded list a character owns (items ≤ 60). */
        const val MAX_KEYS = 60
    }

    // ---- records ---------------------------------------------------------------------------------------------

    /** One record decoded with [serializer]; pair it with the kind's own model, or use the per-kind accessors. */
    suspend fun <T : CompendiumRecord> get(kind: Kind, key: String, serializer: KSerializer<T>): T? =
        dao.get(kind.id, key)?.let { CompendiumJson.decodeFromString(serializer, it.json) }

    /** One record decoded with the kind's own model. */
    suspend fun get(kind: Kind, key: String): CompendiumRecord? =
        dao.get(kind.id, key)?.let { kind.decodeOne(it.json) }

    /**
     * The records of [keys] in the caller's order, unknown keys dropped; at most [MAX_KEYS] keys.
     * No query runs for an empty list.
     */
    suspend fun <T : CompendiumRecord> getAll(kind: Kind, keys: List<String>, serializer: KSerializer<T>): List<T> {
        require(keys.size <= MAX_KEYS) { "getAll(${kind.id}) takes at most $MAX_KEYS keys, got ${keys.size}" }
        if (keys.isEmpty()) return emptyList()
        val byKey = dao.getAll(kind.id, keys).associateBy { it.key }
        return keys.mapNotNull { key -> byKey[key]?.let { CompendiumJson.decodeFromString(serializer, it.json) } }
    }

    suspend fun spell(key: String): SpellRecord? = get(Kind.SPELLS, key, SpellRecord.serializer())
    suspend fun condition(key: String): TextRecord? = get(Kind.CONDITIONS, key, TextRecord.serializer())
    suspend fun rule(key: String): RuleRecord? = get(Kind.RULES, key, RuleRecord.serializer())
    suspend fun ruleSection(key: String): TextRecord? = get(Kind.RULE_SECTIONS, key, TextRecord.serializer())
    suspend fun characterClass(key: String): ClassRecord? = get(Kind.CLASSES, key, ClassRecord.serializer())
    suspend fun subclass(key: String): SubclassRecord? = get(Kind.SUBCLASSES, key, SubclassRecord.serializer())
    suspend fun feature(key: String): FeatureRecord? = get(Kind.FEATURES, key, FeatureRecord.serializer())
    suspend fun race(key: String): RaceRecord? = get(Kind.RACES, key, RaceRecord.serializer())
    suspend fun subrace(key: String): SubraceRecord? = get(Kind.SUBRACES, key, SubraceRecord.serializer())
    suspend fun trait(key: String): TraitRecord? = get(Kind.TRAITS, key, TraitRecord.serializer())
    suspend fun background(key: String): BackgroundRecord? = get(Kind.BACKGROUNDS, key, BackgroundRecord.serializer())
    suspend fun feat(key: String): FeatRecord? = get(Kind.FEATS, key, FeatRecord.serializer())
    suspend fun equipment(key: String): EquipmentRecord? = get(Kind.EQUIPMENT, key, EquipmentRecord.serializer())
    suspend fun weaponProperty(key: String): TextRecord? = get(Kind.WEAPON_PROPERTIES, key, TextRecord.serializer())
    suspend fun magicItem(key: String): MagicItemRecord? = get(Kind.MAGIC_ITEMS, key, MagicItemRecord.serializer())
    suspend fun creature(key: String): CreatureRecord? = get(Kind.CREATURES, key, CreatureRecord.serializer())
    suspend fun skill(key: String): SkillRecord? = get(Kind.SKILLS, key, SkillRecord.serializer())
    suspend fun language(key: String): LanguageRecord? = get(Kind.LANGUAGES, key, LanguageRecord.serializer())
    suspend fun damageType(key: String): TextRecord? = get(Kind.DAMAGE_TYPES, key, TextRecord.serializer())
    suspend fun magicSchool(key: String): TextRecord? = get(Kind.MAGIC_SCHOOLS, key, TextRecord.serializer())
    suspend fun alignment(key: String): AlignmentRecord? = get(Kind.ALIGNMENTS, key, AlignmentRecord.serializer())
    suspend fun proficiency(key: String): ProficiencyRecord? = get(Kind.PROFICIENCIES, key, ProficiencyRecord.serializer())

    // ---- rules bridge ----------------------------------------------------------------------------------------

    /** Derive's armor table (plan D11): the 13 armor rows decoded on demand, never stored as columns. */
    suspend fun armorTable(): Map<String, ArmorStats> {
        val refs = dao.bySubcategory(Kind.EQUIPMENT.id, "armor")
        val records = getAll(Kind.EQUIPMENT, refs.map { it.key }, EquipmentRecord.serializer())
        return RulesBridge.armorTable(records)
    }

    // ---- search ----------------------------------------------------------------------------------------------

    /**
     * The S13 search (plan D9): a name-prefix query (skipped under two characters) and an FTS query
     * (skipped when no token survives), ranked and split into [Search.Results]' two tiers by [Search];
     * [kinds] empty means every kind. Never more than [Search.LIMIT] hits across both tiers.
     */
    suspend fun search(input: String, kinds: List<Kind> = emptyList()): Search.Results {
        val prefix = Search.likePrefix(input)
        val match = Search.ftsQuery(input)
        val kindIds = kinds.map { it.id }
        // Both queries fetch Search.FETCH candidates, not Search.LIMIT: their SQL order is not a ranking
        // (alphabetical for names, import order for the FTS hits), so cutting at the display bound would
        // decide the results before anything ranked them. Search.split applies the real bound.
        val nameHits = when {
            prefix == null -> emptyList()
            kindIds.isEmpty() -> Search.rankNames(prefix, dao.nameMatches(prefix, Search.FETCH))
            else -> Search.rankNames(prefix, dao.nameMatchesIn(kindIds, prefix, Search.FETCH))
        }
        val textHits = when {
            match == null -> emptyList()
            kindIds.isEmpty() -> dao.textMatches(match, Search.FETCH)
            else -> dao.textMatchesIn(kindIds, match, Search.FETCH)
        }
        return Search.split(nameHits, textHits)
    }

    // ---- lists (pass-throughs; every one kind-scoped, finite by the bundle; large kinds take a limit) ------

    suspend fun countsByKind(): List<KindCount> = dao.countsByKind()
    suspend fun listByName(kind: Kind, limit: Int): List<CompendiumRef> = dao.listByName(kind.id, limit)
    suspend fun listInOrder(kind: Kind, limit: Int): List<CompendiumRef> = dao.listInOrder(kind.id, limit)
    suspend fun children(kind: Kind, parentKey: String): List<CompendiumRef> = dao.children(kind.id, parentKey)
    suspend fun subclassesOf(classKey: String): List<CompendiumRef> = dao.subclassesOf(classKey)

    /** The rules chapter owning a rule section (the S10 CHAPTER link); null when the section has no owner. */
    suspend fun chapterOfSection(sectionKey: String): CompendiumRef? = dao.chapterOfSection(sectionKey)
    suspend fun spellsByLevel(level: Int): List<CompendiumRef> = dao.spellsByLevel(level)
    suspend fun spellsForClass(classKey: String, maxLevel: Int): List<CompendiumRef> = dao.spellsForClass(classKey, maxLevel)
    suspend fun classFeatures(classKey: String, maxLevel: Int): List<CompendiumRef> = dao.classFeatures(classKey, maxLevel)
    suspend fun subclassFeatures(subclassKey: String): List<CompendiumRef> = dao.subclassFeatures(subclassKey)
    suspend fun byCategory(kind: Kind, category: String): List<CompendiumRef> = dao.byCategory(kind.id, category)
    suspend fun bySubcategory(kind: Kind, subcategory: String): List<CompendiumRef> = dao.bySubcategory(kind.id, subcategory)
    suspend fun categoriesOf(kind: Kind): List<CategoryCount> = dao.categoriesOf(kind.id)
    suspend fun creaturesByCr(minCr: Double, maxCr: Double): List<CompendiumRef> = dao.creaturesByCr(minCr, maxCr)

    /** The [CompendiumRef]s of [keys] in sortName order — for lists that already hold keys (M4 feature keys). */
    suspend fun refs(kind: Kind, keys: List<String>): List<CompendiumRef> {
        require(keys.size <= MAX_KEYS) { "refs(${kind.id}) takes at most $MAX_KEYS keys, got ${keys.size}" }
        if (keys.isEmpty()) return emptyList()
        return dao.refs(kind.id, keys)
    }
}
