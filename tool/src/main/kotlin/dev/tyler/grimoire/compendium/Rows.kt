package dev.tyler.grimoire.compendium

/**
 * What the importer knows beyond the record it is deriving: which rules chapter owns each rule section and
 * where in the chapter it sits. Built once from the decoded rules.json (imported before rule_sections —
 * see [Kind]) and empty for every other kind.
 */
class ImportContext(val sectionOwner: Map<String, Pair<String, Int>>) {
    companion object {
        val EMPTY = ImportContext(emptyMap())

        /** From the decoded rules.json; every record must be a [RuleRecord] and every section may be listed once. */
        fun from(rules: List<CompendiumRecord>): ImportContext {
            val owner = HashMap<String, Pair<String, Int>>()
            for (rule in rules) {
                require(rule is RuleRecord) { "ImportContext needs rules records; '${rule.key}' is not a chapter" }
                for ((i, section) in rule.sections.withIndex()) {
                    val previous = owner.put(section, rule.key to i)
                    check(previous == null) { "rule section '$section' is listed by both '${previous?.first}' and '${rule.key}'" }
                }
            }
            return ImportContext(owner)
        }
    }
}

/** `records.sortName`: the name trimmed and lowercased, the order of every kind-scoped list. */
object SortName {
    fun of(name: String): String = name.trim().lowercase()
}

/**
 * The FTS4 body of a record (plan D9): the prose a player would search for, enriched where the searchable
 * text lives outside `text` (a spell's higher-level paragraph, a magic item's headline, a background's
 * feature, a creature's traits and actions, a class's spellcasting rules). Markdown-lite markers are left
 * in — unicode61 treats `*#|-` as separators. Proficiencies have nothing to search.
 */
object Body {
    fun of(record: CompendiumRecord): String = when (record) {
        is SpellRecord -> record.text + "\n" + record.higherLevel
        is MagicItemRecord -> record.headline + "\n" + record.text
        is BackgroundRecord -> record.feature.text + "\n" + record.text
        is CreatureRecord -> {
            val parts = ArrayList<String>()
            if (record.text.isNotEmpty()) parts += record.text
            for (list in listOf(record.traits, record.actions, record.reactions, record.legendaryActions)) {
                for (action in list) parts += "${action.name}. ${action.text}"
            }
            parts.joinToString("\n")
        }
        is ClassRecord -> record.spellcasting?.info.orEmpty().joinToString("\n") { "${it.name}. ${it.text}" }
        is ProficiencyRecord -> ""
        is TextRecord -> record.text
        is RuleRecord -> record.text
        is SubclassRecord -> record.text
        is FeatureRecord -> record.text
        is RaceRecord -> record.text
        is SubraceRecord -> record.text
        is TraitRecord -> record.text
        is FeatRecord -> record.text
        is SkillRecord -> record.text
        is LanguageRecord -> record.text
        is AlignmentRecord -> record.text
        is EquipmentRecord -> record.text
    }
}

/**
 * Derives the two Room rows of one record (plan §Entities). Pure: the same inputs give the same rows, which
 * is what RowsTest pins for the whole bundle. [of] keeps the caller's `slice` object as `json` untouched.
 */
object Rows {
    data class Built(val record: RecordRow, val search: SearchRow)

    /**
     * @param position the record's index in its asset array (rule_sections replace it with the index within
     *   the owning chapter's `sections[]`)
     * @param slice the record's raw JSON text from [JsonArraySplit]
     * @param record the same slice decoded with [kind]'s serializer
     * @param ctx [ImportContext.from] rules.json for rule_sections; [ImportContext.EMPTY] otherwise
     */
    fun of(kind: Kind, position: Int, slice: String, record: CompendiumRecord, ctx: ImportContext): Built {
        val base = RecordRow(
            kind = kind.id,
            key = record.key,
            name = record.name,
            sortName = SortName.of(record.name),
            position = position,
            level = null,
            school = null,
            castingTime = null,
            concentration = null,
            ritual = null,
            classList = null,
            classKey = null,
            subclassKey = null,
            parentKey = null,
            category = null,
            subcategory = null,
            rarity = null,
            cr = null,
            json = slice,
        )
        val row = when (kind) {
            Kind.SPELLS -> {
                val spell = expect<SpellRecord>(kind, record)
                base.copy(
                    level = spell.level,
                    school = spell.school,
                    castingTime = spell.castingTime,
                    concentration = spell.concentration,
                    ritual = spell.ritual,
                    classList = spell.classes.joinToString(separator = " ", prefix = " ", postfix = " "),
                )
            }
            Kind.CONDITIONS, Kind.WEAPON_PROPERTIES, Kind.DAMAGE_TYPES, Kind.MAGIC_SCHOOLS -> {
                expect<TextRecord>(kind, record)
                base
            }
            Kind.RULES -> {
                expect<RuleRecord>(kind, record)
                base
            }
            Kind.RULE_SECTIONS -> {
                expect<TextRecord>(kind, record)
                val owner = checkNotNull(ctx.sectionOwner[record.key]) { "rule section '${record.key}' is listed by no chapter in rules.json" }
                base.copy(parentKey = owner.first, position = owner.second)
            }
            Kind.CLASSES -> {
                expect<ClassRecord>(kind, record)
                base
            }
            Kind.SUBCLASSES -> base.copy(classKey = expect<SubclassRecord>(kind, record).classKey)
            Kind.FEATURES -> {
                val feature = expect<FeatureRecord>(kind, record)
                base.copy(level = feature.level, classKey = feature.classKey, subclassKey = feature.subclassKey, parentKey = feature.parentKey)
            }
            Kind.RACES -> {
                expect<RaceRecord>(kind, record)
                base
            }
            Kind.SUBRACES -> base.copy(parentKey = expect<SubraceRecord>(kind, record).raceKey)
            Kind.TRAITS -> base.copy(parentKey = expect<TraitRecord>(kind, record).parentKey)
            Kind.BACKGROUNDS -> {
                expect<BackgroundRecord>(kind, record)
                base
            }
            Kind.FEATS -> {
                expect<FeatRecord>(kind, record)
                base
            }
            Kind.EQUIPMENT -> {
                val equipment = expect<EquipmentRecord>(kind, record)
                val subcategory = when {
                    equipment.armor != null -> "armor"
                    equipment.weapon != null -> "weapon"
                    else -> null
                }
                base.copy(category = equipment.category, subcategory = subcategory)
            }
            Kind.MAGIC_ITEMS -> {
                val item = expect<MagicItemRecord>(kind, record)
                base.copy(category = item.category, subcategory = if (item.isVariant) "variant" else "base", rarity = item.rarity)
            }
            Kind.CREATURES -> {
                val creature = expect<CreatureRecord>(kind, record)
                base.copy(category = creature.type, subcategory = creature.size, cr = creature.cr)
            }
            Kind.SKILLS -> {
                expect<SkillRecord>(kind, record)
                base
            }
            Kind.LANGUAGES -> {
                expect<LanguageRecord>(kind, record)
                base
            }
            Kind.ALIGNMENTS -> {
                expect<AlignmentRecord>(kind, record)
                base
            }
            Kind.PROFICIENCIES -> {
                expect<ProficiencyRecord>(kind, record)
                base
            }
        }
        return Built(row, SearchRow(kind = kind.id, key = record.key, name = record.name, body = Body.of(record)))
    }

    private inline fun <reified T : CompendiumRecord> expect(kind: Kind, record: CompendiumRecord): T =
        record as? T ?: throw IllegalArgumentException("${kind.id} record '${record.key}' was not decoded with the ${kind.id} model")
}
