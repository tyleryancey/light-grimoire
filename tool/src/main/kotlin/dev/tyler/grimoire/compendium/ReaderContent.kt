package dev.tyler.grimoire.compendium

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Composes one record into everything the S13 reader screen renders (plan D9/D10). PURE: kotlin
 * stdlib plus the JsonObject fields the models keep open ([CreatureRecord.speed]/[senses]) — no
 * Android or Compose types; the screen turns [Block]s into rows and [LinkSection]s into link rows.
 *
 * Composition rules shared by every kind: header lines are [Block.Field]s built from the typed
 * record fields (secondary = the quieter lines); body prose always goes through [Markdown.parse],
 * then every [Block.Table] through [TableLayout.lower]; a LEADING level-1 heading whose plain text
 * equals the record's name is dropped (the nine rules chapters open "# <Name>") while mid-text
 * level-1 headings (multiclassing, reading-a-stat-block) stay; named sub-texts (creature actions,
 * spell higher-level text) render as run-ins — a bold "Name. " merged into the first paragraph.
 */
data class ReaderDoc(val blocks: List<Block>, val links: List<LinkSection>)

/** One link row group on the reader screen: a label plus the query that fills it when tapped. */
data class LinkSection(val label: String, val query: RefQuery)

/** A deferred lookup the reader screen resolves through [CompendiumReader] — never eagerly loaded. */
sealed interface RefQuery {
    data class Keys(val kind: Kind, val keys: List<String>) : RefQuery

    data class SubclassesOf(val classKey: String) : RefQuery

    data class ClassFeatures(val classKey: String) : RefQuery

    data class SubclassFeatures(val subclassKey: String) : RefQuery

    data class SectionsOf(val ruleKey: String) : RefQuery

    data class Chapter(val sectionKey: String) : RefQuery
}

object ReaderContent {
    /**
     * The full reader composition for one record. [kind] disambiguates only the five kinds that
     * share [TextRecord] (conditions get the self-excluded SEE scan, rule_sections their CHAPTER
     * link); every other branch is chosen by the sealed model alone.
     */
    fun of(kind: Kind, record: CompendiumRecord): ReaderDoc = when (record) {
        is SpellRecord -> spell(record)
        is CreatureRecord -> creature(record)
        is ClassRecord -> characterClass(record)
        is SubclassRecord -> subclass(record)
        is FeatureRecord -> feature(record)
        is RaceRecord -> race(record)
        is SubraceRecord -> subrace(record)
        is TraitRecord -> trait(record)
        is BackgroundRecord -> background(record)
        is FeatRecord -> feat(record)
        is EquipmentRecord -> equipment(record)
        is MagicItemRecord -> magicItem(record)
        is SkillRecord -> skill(record)
        is LanguageRecord -> language(record)
        is AlignmentRecord -> alignment(record)
        is ProficiencyRecord -> proficiency(record)
        is RuleRecord -> rule(record)
        is TextRecord -> textRecord(kind, record)
    }

    // ---- per-kind composition --------------------------------------------------------------------------------

    private fun spell(r: SpellRecord): ReaderDoc {
        val line1 = buildString {
            if (r.level == 0) append("${titlecase(r.school)} cantrip") else append("${ordinal(r.level)}-level ${r.school}")
            if (r.ritual) append(" (ritual)")
        }
        val fields = mutableListOf(
            Block.Field(line1),
            Block.Field("${r.castingTime} · ${r.range} · ${r.components.joinToString(" ")}"),
            Block.Field(durationLine(r)),
        )
        r.material?.let { fields.add(Block.Field("M: $it", secondary = true)) }
        if (r.classes.isNotEmpty()) {
            fields.add(Block.Field(r.classes.joinToString(", ") { titlecase(it) }, secondary = true))
        }
        val higher = if (r.higherLevel.isBlank()) emptyList() else runIn("At higher levels", r.higherLevel)
        return ReaderDoc(fields + body(r.name, r.text) + higher, see(r.text + "\n" + r.higherLevel))
    }

    private fun creature(r: CreatureRecord): ReaderDoc {
        val subtype = r.subtype?.let { " ($it)" } ?: ""
        val fields = mutableListOf(
            Block.Field("${r.size} ${r.type}$subtype, ${r.alignment}"),
            Block.Field("AC " + r.ac.joinToString(", ") { "${it.value} (${it.type})" }),
            Block.Field("HP ${r.hp} (${r.hpRoll})"),
            Block.Field(speedLine(r.speed)),
        )
        val grid = TableLayout.lower(abilityTable(r.abilities))
        val stats = mutableListOf<Block>()
        if (r.saves.isNotEmpty()) {
            val saves = ABILITY_ORDER.filter { it in r.saves }.joinToString(", ") { "${abilityShort(it)} ${signed(r.saves.getValue(it))}" }
            stats.add(Block.Field("Saves $saves", secondary = true))
        }
        if (r.skills.isNotEmpty()) {
            val skills = r.skills.keys.sorted().joinToString(", ") { "${humanize(it)} ${signed(r.skills.getValue(it))}" }
            stats.add(Block.Field("Skills $skills", secondary = true))
        }
        if (r.vulnerabilities.isNotEmpty()) stats.add(Block.Field("Vulnerabilities " + r.vulnerabilities.joinToString(", "), secondary = true))
        if (r.resistances.isNotEmpty()) stats.add(Block.Field("Resistances " + r.resistances.joinToString(", "), secondary = true))
        if (r.immunities.isNotEmpty()) stats.add(Block.Field("Immunities " + r.immunities.joinToString(", "), secondary = true))
        if (r.conditionImmunities.isNotEmpty()) {
            stats.add(Block.Field("Condition immunities " + r.conditionImmunities.joinToString(", "), secondary = true))
        }
        sensesLine(r.senses)?.let { stats.add(Block.Field(it, secondary = true)) }
        if (r.languages.isNotBlank()) stats.add(Block.Field("Languages ${r.languages}", secondary = true))
        stats.add(Block.Field("CR ${crText(r.cr)} · ${r.xp} XP · Prof +${r.profBonus}", secondary = true))

        val bodyBlocks = body(r.name, r.text) +
            r.traits.flatMap { runIn(it.name, it.text) } +
            actionSection("Actions", r.actions) +
            actionSection("Reactions", r.reactions) +
            actionSection("Legendary Actions", r.legendaryActions)

        val prose = buildString {
            append(r.text)
            for (a in r.traits + r.actions + r.reactions + r.legendaryActions) {
                append('\n')
                append(a.text)
            }
        }
        val scanned = CrossRefs.conditions(prose)
        val union = (scanned + r.conditionImmunities.filterNot { it in scanned }).distinct().take(CrossRefs.MAX_REFS)
        val links = if (union.isEmpty()) emptyList() else listOf(LinkSection("SEE", RefQuery.Keys(Kind.CONDITIONS, union)))
        return ReaderDoc(fields + grid + stats + bodyBlocks, links)
    }

    private fun characterClass(r: ClassRecord): ReaderDoc {
        val fields = mutableListOf(
            Block.Field("Hit die d${r.hitDie}"),
            Block.Field("Saves " + r.savingThrows.joinToString(", ") { abilityShort(it) }),
        )
        val profs = r.proficiencies.filterNot { it.startsWith("saving-throw-") }
        if (profs.isNotEmpty()) fields.add(Block.Field(profs.joinToString(", ") { humanize(it) }, secondary = true))
        r.spellcasting?.let {
            fields.add(Block.Field("Spellcasting: ${abilityShort(it.ability)} (from level ${it.startsAtLevel})"))
        }
        // The 20-level table is deferred to M4 (wizard transcription) — levels are not rendered here.
        val bodyBlocks = r.spellcasting?.info.orEmpty().flatMap { info ->
            listOf(heading(3, info.name)) + lowerTables(Markdown.parse(info.text))
        }
        val links = listOf(
            LinkSection("SUBCLASSES", RefQuery.SubclassesOf(r.key)),
            LinkSection("FEATURES", RefQuery.ClassFeatures(r.key)),
        )
        return ReaderDoc(fields + bodyBlocks, links)
    }

    private fun subclass(r: SubclassRecord): ReaderDoc = ReaderDoc(
        listOf(Block.Field(r.flavor, secondary = true)) + body(r.name, r.text),
        listOf(LinkSection("FEATURES", RefQuery.SubclassFeatures(r.key))) + see(r.text),
    )

    private fun feature(r: FeatureRecord): ReaderDoc {
        val fields = mutableListOf(Block.Field("${titlecase(r.subclassKey ?: r.classKey)} ${r.level}"))
        if (r.prerequisites.isNotEmpty()) {
            fields.add(Block.Field("Prerequisite: " + r.prerequisites.joinToString(", ") { prerequisite(it) }))
        }
        val links = buildList {
            r.parentKey?.let { add(LinkSection("PART OF", RefQuery.Keys(Kind.FEATURES, listOf(it)))) }
            add(LinkSection("CLASS", RefQuery.Keys(Kind.CLASSES, listOf(r.classKey))))
            addAll(see(r.text))
        }
        return ReaderDoc(fields + body(r.name, r.text), links)
    }

    private fun race(r: RaceRecord): ReaderDoc {
        val fields = mutableListOf(Block.Field("${r.size} · Speed ${r.speed} ft."))
        bonusLine(r.abilityBonuses, r.abilityBonusOptions)?.let { fields.add(Block.Field(it)) }
        if (r.languages.isNotEmpty()) {
            fields.add(Block.Field("Languages: " + r.languages.joinToString(", ") { titlecase(it) }, secondary = true))
        }
        val links = buildList {
            if (r.traits.isNotEmpty()) add(LinkSection("TRAITS", RefQuery.Keys(Kind.TRAITS, r.traits)))
            if (r.subraces.isNotEmpty()) add(LinkSection("SUBRACES", RefQuery.Keys(Kind.SUBRACES, r.subraces)))
        }
        return ReaderDoc(fields + body(r.name, r.text), links)
    }

    private fun subrace(r: SubraceRecord): ReaderDoc {
        val fields = bonusLine(r.abilityBonuses, "")?.let { listOf(Block.Field(it)) }.orEmpty()
        // Deliberately no see() scan: the only subrace condition-word hit in the bundle is
        // lightfoot-halfling's idiomatic "prone to wanderlust", which must not become a link.
        val links = buildList {
            if (r.traits.isNotEmpty()) add(LinkSection("TRAITS", RefQuery.Keys(Kind.TRAITS, r.traits)))
            add(LinkSection("RACE", RefQuery.Keys(Kind.RACES, listOf(r.raceKey))))
        }
        return ReaderDoc(fields + body(r.name, r.text), links)
    }

    private fun trait(r: TraitRecord): ReaderDoc {
        val links = buildList {
            r.parentKey?.let { add(LinkSection("PART OF", RefQuery.Keys(Kind.TRAITS, listOf(it)))) }
            addAll(see(r.text))
        }
        return ReaderDoc(body(r.name, r.text), links)
    }

    private fun background(r: BackgroundRecord): ReaderDoc {
        val fields = if (r.skillProficiencies.isEmpty()) emptyList() else listOf(
            Block.Field(
                "Skills: " + r.skillProficiencies.joinToString(", ") { humanize(it.removePrefix("skill-")) },
                secondary = true,
            ),
        )
        val bodyBlocks = body(r.name, r.text) +
            listOf(heading(3, r.feature.name)) + lowerTables(Markdown.parse(r.feature.text)) +
            characteristics(r)
        return ReaderDoc(fields + bodyBlocks, emptyList())
    }

    private fun feat(r: FeatRecord): ReaderDoc {
        val fields = if (r.prerequisites.isEmpty()) emptyList() else listOf(
            Block.Field("Prerequisite: " + r.prerequisites.joinToString(", ") { prerequisite(it) }),
        )
        return ReaderDoc(fields + body(r.name, r.text), see(r.text))
    }

    private fun equipment(r: EquipmentRecord): ReaderDoc {
        val fields = mutableListOf<Block>()
        costWeight(r.cost, r.weight)?.let { fields.add(Block.Field(it, secondary = true)) }
        r.armor?.let { fields.add(Block.Field(armorLine(it))) }
        r.weapon?.let { w ->
            fields.add(Block.Field("${w.category} ${w.rangeType.lowercase()} weapon"))
            weaponDamageLine(w)?.let { fields.add(Block.Field(it)) }
        }
        val links = buildList {
            r.weapon?.takeIf { it.properties.isNotEmpty() }?.let {
                add(LinkSection("PROPERTIES", RefQuery.Keys(Kind.WEAPON_PROPERTIES, it.properties)))
            }
            // ball-bearings ("fall prone") and basic poison ("the poisoned weapon") are the only hits.
            addAll(see(r.text))
        }
        return ReaderDoc(fields + body(r.name, r.text), links)
    }

    private fun magicItem(r: MagicItemRecord): ReaderDoc {
        // The headline is emitted by the pipeline verbatim; rarity/attunement are never re-derived here.
        val fields = listOf(Block.Field(r.headline, secondary = true))
        val variants = if (!r.isVariant && r.variants.isNotEmpty()) {
            listOf(LinkSection("VARIANTS", RefQuery.Keys(Kind.MAGIC_ITEMS, r.variants)))
        } else {
            emptyList()
        }
        return ReaderDoc(fields + body(r.name, r.text), variants + see(r.text))
    }

    private fun skill(r: SkillRecord): ReaderDoc =
        ReaderDoc(listOf(Block.Field("Ability: ${abilityName(r.ability)}")) + body(r.name, r.text), emptyList())

    private fun language(r: LanguageRecord): ReaderDoc {
        val head = buildString {
            append(r.type)
            r.script?.let { append(" · Script: $it") }
        }
        val fields = mutableListOf(Block.Field(head))
        if (r.typicalSpeakers.isNotEmpty()) {
            fields.add(Block.Field("Typical speakers: " + r.typicalSpeakers.joinToString(", "), secondary = true))
        }
        return ReaderDoc(fields + body(r.name, r.text), emptyList())
    }

    private fun alignment(r: AlignmentRecord): ReaderDoc =
        ReaderDoc(listOf(Block.Field(r.abbreviation, secondary = true)) + body(r.name, r.text), emptyList())

    private fun proficiency(r: ProficiencyRecord): ReaderDoc {
        val fields = mutableListOf(Block.Field(r.type))
        if (r.classes.isNotEmpty()) {
            fields.add(Block.Field("Classes: " + r.classes.joinToString(", ") { titlecase(it) }, secondary = true))
        }
        if (r.races.isNotEmpty()) {
            fields.add(Block.Field("Races: " + r.races.joinToString(", ") { titlecase(it) }, secondary = true))
        }
        return ReaderDoc(fields, emptyList())
    }

    private fun rule(r: RuleRecord): ReaderDoc =
        ReaderDoc(body(r.name, r.text), listOf(LinkSection("SECTIONS", RefQuery.SectionsOf(r.key))))

    private fun textRecord(kind: Kind, r: TextRecord): ReaderDoc = when (kind) {
        Kind.CONDITIONS -> ReaderDoc(body(r.name, r.text), see(r.text, excludeKey = r.key))
        Kind.RULE_SECTIONS -> ReaderDoc(
            body(r.name, r.text),
            listOf(LinkSection("CHAPTER", RefQuery.Chapter(r.key))) + see(r.text),
        )
        else -> ReaderDoc(body(r.name, r.text), emptyList())
    }

    // ---- body pipeline ---------------------------------------------------------------------------------------

    /**
     * Parse [text], drop a leading heading that just repeats [name], lower every table.
     *
     * Any level, not only `#`: the nine rules chapters open `# <Name>` but 33 of the 40 rule sections open
     * `## <Name>`, and the top bar already carries the name either way. Every leading heading in those two
     * files matches its record's name exactly, so this drops the duplicates and nothing else; mid-text
     * headings (multiclassing's "Proficiency Bonus", reading-a-stat-block's) are untouched.
     */
    private fun body(name: String, text: String): List<Block> {
        if (text.isBlank()) return emptyList()
        val parsed = Markdown.parse(text)
        val first = parsed.firstOrNull()
        val trimmed = if (first is Block.Heading && plain(first.spans) == name) parsed.drop(1) else parsed
        return lowerTables(trimmed)
    }

    /** Parse [text] and merge a bold "Name. " into its first paragraph (prepended alone otherwise). */
    private fun runIn(name: String, text: String): List<Block> {
        val parsed = Markdown.parse(text)
        val label = Span.Text("$name. ", bold = true)
        val first = parsed.firstOrNull()
        val merged = when (first) {
            null -> listOf(Block.Para(listOf(label)))
            is Block.Para -> listOf(Block.Para(listOf(label) + first.spans)) + parsed.drop(1)
            else -> listOf(Block.Para(listOf(label))) + parsed
        }
        return lowerTables(merged)
    }

    private fun lowerTables(blocks: List<Block>): List<Block> =
        blocks.flatMap { if (it is Block.Table) TableLayout.lower(it) else listOf(it) }

    private fun actionSection(title: String, actions: List<CreatureAction>): List<Block> =
        if (actions.isEmpty()) emptyList()
        else listOf(heading(3, title)) + actions.flatMap { runIn(it.name, it.text) }

    private fun characteristics(r: BackgroundRecord): List<Block> {
        val groups = listOf(
            "Personality Traits" to r.personalityTraits,
            "Ideals" to r.ideals,
            "Bonds" to r.bonds,
            "Flaws" to r.flaws,
        ).filter { it.second.isNotEmpty() }
        if (groups.isEmpty()) return emptyList()
        return listOf(heading(3, "Suggested Characteristics")) + groups.flatMap { (label, items) ->
            listOf(heading(4, label)) + items.mapIndexed { i, item -> Block.Numbered(i + 1, Markdown.spans(item)) }
        }
    }

    private fun see(prose: String, excludeKey: String? = null): List<LinkSection> {
        val keys = CrossRefs.conditions(prose, excludeKey)
        return if (keys.isEmpty()) emptyList() else listOf(LinkSection("SEE", RefQuery.Keys(Kind.CONDITIONS, keys)))
    }

    private fun heading(level: Int, text: String): Block.Heading = Block.Heading(level, listOf(Span.Text(text)))

    private fun plain(spans: List<Span>): String = buildString {
        for (span in spans) if (span is Span.Text) append(span.text)
    }

    // ---- pure formatters -------------------------------------------------------------------------------------

    private val ABILITY_ORDER = listOf("str", "dex", "con", "int", "wis", "cha")

    private val ABILITY_NAMES = mapOf(
        "str" to "Strength",
        "dex" to "Dexterity",
        "con" to "Constitution",
        "int" to "Intelligence",
        "wis" to "Wisdom",
        "cha" to "Charisma",
    )

    /** "1st", "2nd", "3rd", "4th"… (spell levels 1–9 only). */
    private fun ordinal(n: Int): String = when (n) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${n}th"
    }

    /** "str" -> "Str". */
    private fun abilityShort(ability: String): String = titlecase(ability)

    /** "dex" -> "Dexterity"; an unmapped value stays as written. */
    private fun abilityName(ability: String): String = ABILITY_NAMES[ability] ?: ability

    /** Slug humanizer: "light-armor" -> "Light armor". */
    private fun humanize(slug: String): String {
        val words = slug.replace('-', ' ')
        return if (words.isEmpty()) words else words.replaceFirstChar { it.uppercaseChar() }
    }

    /** Every hyphen-word capitalized: "wizard" -> "Wizard", "high-elf" -> "High Elf". */
    private fun titlecase(slug: String): String =
        slug.split('-').joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

    private fun signed(n: Int): String = if (n >= 0) "+$n" else "$n"

    /** "8 (-1)", "14 (+2)": the score plus floor((score - 10) / 2). */
    private fun scoreCell(score: Int): String = "$score (${signed((score - 10).floorDiv(2))})"

    private fun abilityTable(s: Scores): Block.Table = Block.Table(
        listOf(
            listOf("STR", "DEX", "CON", "INT", "WIS", "CHA"),
            listOf(s.str, s.dex, s.con, s.int, s.wis, s.cha).map { scoreCell(it) },
        ),
    )

    /** 0.125 -> "1/8", 0.25 -> "1/4", 0.5 -> "1/2", anything else its integer part. */
    private fun crText(cr: Double): String = when (cr) {
        0.125 -> "1/8"
        0.25 -> "1/4"
        0.5 -> "1/2"
        else -> cr.toInt().toString()
    }

    /** Concentration spells prefix and decapitalize: "Up to 1 minute" -> "Concentration, up to 1 minute". */
    private fun durationLine(r: SpellRecord): String =
        if (r.concentration) "Concentration, " + r.duration.replaceFirstChar { it.lowercaseChar() } else r.duration

    private fun prerequisite(p: Prerequisite): String = when {
        p.ability != null && p.minimum != null -> "${abilityShort(p.ability)} ${p.minimum}"
        p.level != null -> "Level ${p.level}"
        p.spell != null -> humanize(p.spell.substringAfterLast('/'))
        p.feature != null -> humanize(p.feature.substringAfterLast('/'))
        else -> p.type ?: ""
    }

    /** "+2 Dex" (+ the options note when present); null when the record grants neither. */
    private fun bonusLine(bonuses: List<AbilityBonus>, options: String): String? {
        if (bonuses.isEmpty() && options.isBlank()) return null
        return buildString {
            append(bonuses.joinToString(", ") { "+${it.bonus} ${abilityShort(it.ability)}" })
            if (options.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append("($options)")
            }
        }
    }

    /** "75 gp · 55 lb.", either part omitted when missing; null when both are. */
    private fun costWeight(cost: Cost?, weight: Double?): String? {
        val parts = mutableListOf<String>()
        cost?.let { parts.add("${it.qty} ${it.unit}") }
        weight?.let { parts.add("${number(it)} lb.") }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    /** A weight without a trailing ".0": 55.0 -> "55", 0.5 -> "0.5". */
    private fun number(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun armorLine(a: Armor): String = buildString {
        if (a.category == "Shield") {
            append("Shield · AC +${a.base}")
        } else {
            append("${a.category} armor · AC ${a.base}")
            if (a.dexBonus) {
                append(" + Dex")
                a.maxBonus?.let { append(" (max $it)") }
            }
        }
        if (a.strMinimum > 0) append(" · Str ${a.strMinimum}")
        if (a.stealthDisadvantage) append(" · Stealth disadvantage")
    }

    /** "1d8 slashing (versatile 1d10)" · "range 150/600 ft." · "thrown 20/60 ft."; null for no parts. */
    private fun weaponDamageLine(w: Weapon): String? {
        val parts = mutableListOf<String>()
        w.damage?.let { d ->
            parts.add(
                buildString {
                    append("${d.dice} ${d.type}")
                    w.twoHandedDamage?.let { append(" (versatile ${it.dice})") }
                },
            )
        }
        if (w.rangeType == "Ranged" && w.throwRange == null) parts.add("range ${rangeText(w.range)}")
        w.throwRange?.let { parts.add("thrown ${rangeText(it)}") }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    private fun rangeText(r: Range): String =
        if (r.long != null) "${r.normal}/${r.long} ft." else "${r.normal} ft."

    /** "Speed 30 ft.", walk unlabeled first, then burrow/climb/fly (+" (hover)")/swim. */
    private fun speedLine(speed: JsonObject): String {
        fun value(key: String): String? = (speed[key] as? JsonPrimitive)?.content
        val hover = (speed["hover"] as? JsonPrimitive)?.content == "true"
        val parts = mutableListOf<String>()
        value("walk")?.let { parts.add(it) }
        value("burrow")?.let { parts.add("burrow $it") }
        value("climb")?.let { parts.add("climb $it") }
        value("fly")?.let { parts.add("fly $it" + if (hover) " (hover)" else "") }
        value("swim")?.let { parts.add("swim $it") }
        return "Speed " + parts.joinToString(", ")
    }

    /** "Senses darkvision 60 ft., passive Perception 9"; null when the object is empty. */
    private fun sensesLine(senses: JsonObject): String? {
        val parts = mutableListOf<String>()
        for (key in listOf("blindsight", "darkvision", "tremorsense", "truesight")) {
            (senses[key] as? JsonPrimitive)?.content?.let { parts.add("$key $it") }
        }
        (senses["passive_perception"] as? JsonPrimitive)?.content?.let { parts.add("passive Perception $it") }
        return if (parts.isEmpty()) null else "Senses " + parts.joinToString(", ")
    }
}
