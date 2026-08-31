package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan D2: one strict model per kind (`ignoreUnknownKeys = false`), so decoding every sha256-pinned record
 * on the JVM is the proof that the models cover the bundle. A field the pipeline starts emitting fails here
 * until the model gains it — never loosen the decoder.
 */
class RecordsDecodeTest {
    private val index = Fixtures.compendiumIndex()
    private val texts: Map<Kind, String> = Kind.entries.associateWith { Fixtures.compendium(it.file) }
    private val records: Map<Kind, List<CompendiumRecord>> = texts.mapValues { (kind, text) -> kind.decodeAll(text) }

    private fun spells() = records.getValue(Kind.SPELLS).map { assertIs<SpellRecord>(it, "spells decode to SpellRecord") }
    private fun creatures() = records.getValue(Kind.CREATURES).map { assertIs<CreatureRecord>(it, "creatures decode to CreatureRecord") }
    private fun classes() = records.getValue(Kind.CLASSES).map { assertIs<ClassRecord>(it, "classes decode to ClassRecord") }
    private fun equipment() = records.getValue(Kind.EQUIPMENT).map { assertIs<EquipmentRecord>(it, "equipment decodes to EquipmentRecord") }

    @Test
    fun everyKindDecodesStrictlyToItsIndexedCount() {
        for (kind in Kind.entries) {
            assertEquals(index.files.getValue(kind.file).count, records.getValue(kind).size, "${kind.id} record count")
        }
        assertEquals(1992, records.values.sumOf { it.size }, "records across all kinds")
    }

    @Test
    fun eachRawSliceDecodesToTheSameRecordAsTheWholeArray() {
        for (kind in Kind.entries) {
            val slices = JsonArraySplit.elements(texts.getValue(kind))
            val decoded = records.getValue(kind)
            assertEquals(decoded.size, slices.size, "${kind.id} slices vs records")
            for (i in decoded.indices) {
                assertEquals(decoded[i], kind.decodeOne(slices[i]), "${kind.id}[$i] ${decoded[i].key} from its raw slice")
            }
        }
    }

    @Test
    fun keysAreUniqueLowercaseSlugsAndNamesStartWithALetter() {
        val slug = Regex("^[a-z0-9][a-z0-9-]*$")
        for ((kind, list) in records) {
            assertEquals(list.size, list.map { it.key }.toSet().size, "${kind.id} keys are unique")
            for (r in list) {
                assertTrue(slug.matches(r.key), "${kind.id} key '${r.key}' is a slug")
                assertTrue(r.name.isNotEmpty() && r.name.first().isLetter(), "${kind.id}/${r.key} name '${r.name}' starts with a letter")
            }
        }
    }

    @Test
    fun envelopeConstantsHoldOnEveryRecord() {
        for ((kind, list) in records) {
            for (r in list) {
                assertEquals("2014", r.edition, "${kind.id}/${r.key} edition")
                assertEquals("srd-5.1", r.source, "${kind.id}/${r.key} source")
                assertEquals("CC-BY-4.0", r.license, "${kind.id}/${r.key} license")
                assertTrue(!r.xref.isNullOrBlank(), "${kind.id}/${r.key} carries an xref")
            }
        }
    }

    @Test
    fun spellLevelsSpanZeroToNineAndFireballDecodesAsExpected() {
        val spells = spells()
        assertEquals((0..9).toSet(), spells.map { it.level }.toSet(), "spell levels present")
        for (s in spells) assertTrue(s.level in 0..9, "${s.key} level ${s.level} in 0..9")
        val fireball = spells.single { it.key == "fireball" }
        assertEquals("Fireball", fireball.name, "fireball name")
        assertEquals(3, fireball.level, "fireball level")
        assertEquals("evocation", fireball.school, "fireball school")
        assertEquals("1 action", fireball.castingTime, "fireball casting time")
        assertEquals(listOf("sorcerer", "wizard"), fireball.classes, "fireball classes")
        assertEquals(false, fireball.concentration, "fireball concentration")
        assertEquals(false, fireball.ritual, "fireball ritual")
        assertEquals("dex", fireball.saveAbility, "fireball save")
        assertEquals(AreaOfEffect(size = 20, type = "sphere"), fireball.areaOfEffect, "fireball area")
        assertEquals("8d6", fireball.damageAtSlotLevel?.get("3"), "fireball damage at slot 3")
        assertNull(fireball.attackType, "fireball has no attack type")
        assertTrue(fireball.higherLevel.isNotBlank(), "fireball higherLevel text")
    }

    @Test
    fun everyClassHasTwentyLevelsWithNineSlotColumns() {
        val classes = classes()
        assertEquals(12, classes.size, "SRD classes")
        for (c in classes) {
            assertEquals((1..20).toList(), c.levels.map { it.level }, "${c.key} levels 1..20")
            for (row in c.levels) assertEquals(9, row.slots.size, "${c.key} level ${row.level} slot columns")
        }
        val wizard = classes.single { it.key == "wizard" }
        assertEquals("int", wizard.spellcasting?.ability, "wizard casting ability")
        assertNull(classes.single { it.key == "fighter" }.spellcasting, "fighter does not cast")
    }

    @Test
    fun theNetHasNoWeaponDamage() {
        val net = equipment().single { it.key == "net" }
        val weapon = assertNotNull(net.weapon, "net is a weapon")
        assertNull(weapon.damage, "net.weapon.damage")
        assertEquals(Range(normal = 5, long = 15), weapon.range, "net range")
        assertTrue("thrown" in weapon.properties, "net is thrown")
        val chainMail = assertNotNull(equipment().single { it.key == "chain-mail" }.armor, "chain mail is armor")
        assertEquals(Armor(category = "Heavy", base = 16, dexBonus = false, maxBonus = null, strMinimum = 13, stealthDisadvantage = true), chainMail, "chain mail armor block")
    }

    @Test
    fun sevenCreaturesHoverAndEightyFourHaveFractionalChallengeRatings() {
        val creatures = creatures()
        val hovering = creatures.filter { it.speed["hover"]?.jsonPrimitive?.booleanOrNull == true }.map { it.key }
        assertEquals(
            listOf("air-elemental", "flying-sword", "ghost", "invisible-stalker", "specter", "will-o-wisp", "wraith"),
            hovering,
            "creatures with speed.hover",
        )
        assertEquals(84, creatures.count { it.cr % 1.0 != 0.0 }, "creatures with a fractional cr")
        val goblin = creatures.single { it.key == "goblin" }
        assertEquals(0.25, goblin.cr, "goblin cr")
        assertEquals("humanoid", goblin.type, "goblin type")
        assertEquals("Small", goblin.size, "goblin size")
        assertTrue(goblin.actions.any { it.name == "Scimitar" }, "goblin has a Scimitar action")
    }

    @Test
    fun strictDecoderRejectsUnknownKeysAndReadsMissingNullablesAsNull() {
        val alignment = """{"abbreviation": "N", "edition": "2014", "key": "neutral", "license": "CC-BY-4.0", "name": "Neutral", "source": "srd-5.1", "text": "t", "xref": "/x"}"""
        assertEquals("N", assertIs<AlignmentRecord>(Kind.ALIGNMENTS.decodeOne(alignment)).abbreviation, "well-formed alignment decodes")
        assertFailsWith<SerializationException>("unknown key rejected") {
            Kind.ALIGNMENTS.decodeOne(alignment.dropLast(1) + """, "extra": 1}""")
        }
        assertFailsWith<SerializationException>("missing required key rejected") {
            Kind.ALIGNMENTS.decodeOne(alignment.replace(""""abbreviation": "N", """, ""))
        }
        val trait = """{"edition": "2014", "key": "t", "license": "CC-BY-4.0", "name": "T", "proficiencies": [], "races": [], "source": "srd-5.1", "subraces": [], "text": "t"}"""
        val decoded = assertIs<TraitRecord>(Kind.TRAITS.decodeOne(trait), "trait decodes")
        assertNull(decoded.parentKey, "absent nullable parentKey reads as null")
        assertNull(decoded.xref, "absent nullable xref reads as null")
    }

    @Test
    fun kindsAreLookedUpByIdKeepTheirFileNamesAndGroupIntoTheNineRowsPlusLookup() {
        for (kind in Kind.entries) {
            assertEquals(kind, Kind.byId(kind.id), "byId round-trips ${kind.id}")
            assertEquals("${kind.id}.json", kind.file, "${kind.id} file name")
        }
        assertFailsWith<IllegalArgumentException>("unknown kind id") { Kind.byId("monsters") }
        assertEquals(
            listOf(
                "spells", "conditions", "rules", "rule_sections", "classes", "subclasses", "features", "races",
                "subraces", "traits", "backgrounds", "feats", "equipment", "weapon_properties", "magic_items",
                "creatures", "skills", "languages", "damage_types", "magic_schools", "alignments", "proficiencies",
            ),
            Kind.entries.map { it.id },
            "the 22 kinds in S13 order (import order and search grouping)",
        )
        assertTrue(Kind.RULES.ordinal < Kind.RULE_SECTIONS.ordinal, "rules import before rule_sections")
        assertEquals(KindGroup.RULES, Kind.RULE_SECTIONS.group, "rule sections sit under RULES")
        assertEquals(KindGroup.CLASSES_AND_FEATURES, Kind.FEATURES.group, "features sit under CLASSES & FEATURES")
        assertEquals(KindGroup.RACES, Kind.TRAITS.group, "traits sit under RACES")
        assertEquals(KindGroup.BACKGROUNDS_AND_FEATS, Kind.FEATS.group, "feats sit under BACKGROUNDS & FEATS")
        assertEquals(KindGroup.EQUIPMENT, Kind.WEAPON_PROPERTIES.group, "weapon properties sit under EQUIPMENT")
        assertEquals(
            setOf(Kind.SKILLS, Kind.LANGUAGES, Kind.DAMAGE_TYPES, Kind.MAGIC_SCHOOLS, Kind.ALIGNMENTS, Kind.PROFICIENCIES),
            Kind.entries.filter { it.group == KindGroup.LOOKUP }.toSet(),
            "lookup kinds",
        )
        assertEquals(KindGroup.entries.toSet(), Kind.entries.map { it.group }.toSet(), "every group has a kind")
    }
}
