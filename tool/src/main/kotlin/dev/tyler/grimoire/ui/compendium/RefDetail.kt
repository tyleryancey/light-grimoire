package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.compendium.CompendiumRef
import dev.tyler.grimoire.compendium.Kind

/**
 * Which of a [CompendiumRef]'s columns a list row shows as its right-aligned lightened detail
 * (docs/UI-SPEC.md S13.1's per-group table and S13.4's disambiguator, D6). The style is a property of the
 * list, not of the row: the same creature reads "1/4" in the CREATURES list and "Creature" in a mixed-kind
 * result list, and most groups show nothing at all.
 */
enum class DetailStyle {
    /** No detail — every group whose rows are already unambiguous (S13.1: all but MAGIC ITEMS and CREATURES). */
    NONE,

    /** The kind's own human label, for a list that mixes kinds. */
    KIND,

    /** A spell's school, abbreviated as the S13.2 wireframe draws it ("evocation" -> "Evo"). */
    SCHOOL,

    /** A magic item's rarity as the bundle writes it ("Very Rare") — S13.1 MAGIC ITEMS. */
    RARITY,

    /** A creature's challenge rating as a bare fraction ("1/8", "1/2", "5") — S13.1 CREATURES. */
    CR,

    /** A feature's class and level ("Barbarian 1") — S13.4's disambiguator. */
    CLASS_LEVEL,

    /** A humanized category slug ("Adventuring gear"). */
    CATEGORY,
}

/**
 * The right-detail string for one row, or null when there is nothing to show — never an empty string, so a
 * caller can pass the result straight to `NavRow(detail = …)` and get the spec's bare row back.
 *
 * Pure: stdlib only, no Compose and no Android, so RefDetailTest runs it over the real bundle's refs.
 */
object RefDetail {
    fun of(ref: CompendiumRef, style: DetailStyle): String? = when (style) {
        DetailStyle.NONE -> null
        DetailStyle.KIND -> Kind.entries.firstOrNull { it.id == ref.kind }?.let { label(it) }
        DetailStyle.SCHOOL -> ref.school.trimmedOrNull()?.let { school(it) }
        DetailStyle.RARITY -> ref.rarity.trimmedOrNull()
        DetailStyle.CR -> challengeRating(ref.cr)
        DetailStyle.CLASS_LEVEL -> classLevel(ref.classKey, ref.level)
        DetailStyle.CATEGORY -> (ref.category ?: ref.subcategory).trimmedOrNull()?.let { Slug.humanize(it) }
    }

    /**
     * One human label per [Kind], singular because it labels a single row. A `when` rather than a map so a
     * 23rd kind cannot be added without naming its label here, and a plain [Kind] property was rejected: the
     * enum's constructor is pinned by the importer's tests.
     */
    fun label(kind: Kind): String = when (kind) {
        Kind.SPELLS -> "Spell"
        Kind.CONDITIONS -> "Condition"
        Kind.RULES -> "Rule"
        Kind.RULE_SECTIONS -> "Rule section"
        Kind.CLASSES -> "Class"
        Kind.SUBCLASSES -> "Subclass"
        Kind.FEATURES -> "Class feature"
        Kind.RACES -> "Race"
        Kind.SUBRACES -> "Subrace"
        Kind.TRAITS -> "Trait"
        Kind.BACKGROUNDS -> "Background"
        Kind.FEATS -> "Feat"
        Kind.EQUIPMENT -> "Equipment"
        Kind.WEAPON_PROPERTIES -> "Weapon property"
        Kind.MAGIC_ITEMS -> "Magic item"
        Kind.CREATURES -> "Creature"
        Kind.SKILLS -> "Skill"
        Kind.LANGUAGES -> "Language"
        Kind.DAMAGE_TYPES -> "Damage type"
        Kind.MAGIC_SCHOOLS -> "Magic school"
        Kind.ALIGNMENTS -> "Alignment"
        Kind.PROFICIENCIES -> "Proficiency"
    }

    /**
     * A spell's school abbreviated, because the S13.2 wireframe draws it that way — `Abj`, `Illu`, `Evo`, `Tran`
     * (docs/UI-SPEC.md S13.2). It is not cosmetic: `NavRow` gives the name `weight(1f)` and the detail its full
     * intrinsic width, so every character of school is a character the spell name loses. Measured over the
     * bundle, the full titlecased school ellipsizes 37 of the 319 names and these abbreviations 6.
     *
     * The bundle ships exactly these eight schools (`magic_schools.json`, and every one of the 319 spells
     * carries one of them), so the `when` is total over the data; anything else falls back to the titlecased
     * slug rather than showing nothing. None runs past four characters — the widest the wireframe itself draws.
     */
    private fun school(slug: String): String = when (slug.lowercase()) {
        "abjuration" -> "Abj"
        "conjuration" -> "Conj"
        "divination" -> "Div"
        "enchantment" -> "Ench"
        "evocation" -> "Evo"
        "illusion" -> "Illu"
        "necromancy" -> "Nec"
        "transmutation" -> "Tran"
        else -> Slug.titlecase(slug)
    }

    /**
     * The SRD's fractional ratings as the book prints them and every whole rating without a decimal point —
     * "1/8", "1/4", "1/2", "0", "5", "21". The three fractions are exact in binary, so they compare exactly
     * against the values the bundle's JSON decodes to.
     */
    private fun challengeRating(cr: Double?): String? = when {
        cr == null -> null
        cr == 0.125 -> "1/8"
        cr == 0.25 -> "1/4"
        cr == 0.5 -> "1/2"
        cr == cr.toLong().toDouble() -> cr.toLong().toString()
        else -> cr.toString()
    }

    /** "Barbarian 1" for a class feature; the level alone when the feature hangs off no class, null when neither is set. */
    private fun classLevel(classKey: String?, level: Int?): String? {
        val name = classKey.trimmedOrNull()?.let { Slug.titlecase(it) }
        return when {
            name != null && level != null -> "$name $level"
            name != null -> name
            level != null -> level.toString()
            else -> null
        }
    }

    private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * The two slug shapes the compendium's list rows need, the same ones `ReaderContent` renders on S10 (kept
 * here rather than shared with it: the reader's copies are private to its own composition rules).
 */
internal object Slug {
    /** "adventuring-gear" -> "Adventuring gear"; underscores read as spaces too ("damage_types" -> "Damage types"). */
    fun humanize(slug: String): String {
        val words = slug.replace('-', ' ').replace('_', ' ')
        return words.replaceFirstChar { it.uppercaseChar() }
    }

    /** Every hyphen-word capitalized: "evocation" -> "Evocation", "high-elf" -> "High Elf". */
    fun titlecase(slug: String): String =
        slug.split('-').joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}
