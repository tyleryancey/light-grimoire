package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures

/**
 * Shared test support: the prose inventory swept by [MarkdownSweepTest] and [TableLayoutTest].
 * Not a test class itself — it holds no `@Test`, only the two helpers both classes call.
 *
 * Every prose field a reader screen will render, for all 1 992 records, exactly as the body will
 * be fed to [Markdown.parse]: each record's `text`; spells also `higherLevel`; classes their
 * `spellcasting.info[].text`; backgrounds also `feature.text`; creatures their `text` plus every
 * trait/action/reaction/legendary-action `text`. Not swept because they are rendered as plain
 * one-line rows, never as Markdown bodies: background characteristic lists (personality traits,
 * ideals, bonds, flaws), subclass `flavor`, and proficiencies (which carry no prose at all) —
 * none of them contains a Markdown token (checked 31 Aug 2026).
 *
 * The `when` below mirrors ReaderContent's prose call sites by hand: a new prose field routed
 * through Markdown.parse there MUST be added here too, or it renders un-swept.
 */
internal fun proseFields(record: CompendiumRecord): List<String> = when (record) {
    is SpellRecord -> listOf(record.text, record.higherLevel)
    is ClassRecord -> record.spellcasting?.info?.map { it.text } ?: emptyList()
    is BackgroundRecord -> listOf(record.text, record.feature.text)
    is CreatureRecord -> listOf(record.text) +
        (record.traits + record.actions + record.reactions + record.legendaryActions).map { it.text }
    is ProficiencyRecord -> emptyList()
    is SubclassRecord -> listOf(record.text)
    is FeatureRecord -> listOf(record.text)
    is RaceRecord -> listOf(record.text)
    is SubraceRecord -> listOf(record.text)
    is TraitRecord -> listOf(record.text)
    is FeatRecord -> listOf(record.text)
    is EquipmentRecord -> listOf(record.text)
    is MagicItemRecord -> listOf(record.text)
    is SkillRecord -> listOf(record.text)
    is LanguageRecord -> listOf(record.text)
    is AlignmentRecord -> listOf(record.text)
    is RuleRecord -> listOf(record.text)
    is TextRecord -> listOf(record.text)
}

/** (kind, record key, field text) for every swept prose field in the bundle. */
internal fun allProseFields(): List<Triple<Kind, String, String>> =
    Kind.entries.flatMap { kind ->
        kind.decodeAll(Fixtures.compendium(kind.file)).flatMap { record ->
            proseFields(record).map { Triple(kind, record.key, it) }
        }
    }
