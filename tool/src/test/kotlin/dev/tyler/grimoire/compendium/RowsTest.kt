package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Plan §Entities / §Rows: every bundled record becomes one `records` row and one `search_index` row through
 * the pure [Rows.of], exactly as the importer will feed Room (plan D4). The whole bundle is derived once
 * here so the per-kind column rules, the rule-section ownership and the distributions are checked against
 * the sha256-pinned assets rather than against hand-written samples.
 */
class RowsTest {
    private companion object {
        val index: CompendiumIndex by lazy { Fixtures.compendiumIndex() }

        /** Rows per kind, built in [Kind] order the way AssetImporter does (RULES before RULE_SECTIONS). */
        val built: Map<Kind, List<Rows.Built>> by lazy {
            var ctx = ImportContext.EMPTY
            Kind.entries.associateWith { kind ->
                val text = Fixtures.compendium(kind.file)
                val slices = JsonArraySplit.elements(text)
                val records = kind.decodeAll(text)
                if (kind == Kind.RULES) ctx = ImportContext.from(records)
                records.indices.map { Rows.of(kind, it, slices[it], records[it], ctx) }
            }
        }

        val chapters: List<RuleRecord> by lazy {
            Kind.RULES.decodeAll(Fixtures.compendium(Kind.RULES.file)).map { assertIs<RuleRecord>(it, "rules decode to RuleRecord") }
        }
    }

    private fun rows(kind: Kind): List<RecordRow> = built.getValue(kind).map { it.record }
    private fun row(kind: Kind, key: String): RecordRow = rows(kind).single { it.key == key }
    private fun body(kind: Kind, key: String): String = built.getValue(kind).single { it.search.key == key }.search.body

    @Test
    fun fireballRowCarriesTheSpellColumnsAndItsBodyIncludesTheHigherLevelText() {
        val fireball = row(Kind.SPELLS, "fireball")
        assertEquals("spells", fireball.kind, "fireball kind")
        assertEquals("Fireball", fireball.name, "fireball name")
        assertEquals("fireball", fireball.sortName, "fireball sortName")
        assertEquals(3, fireball.level, "fireball level")
        assertEquals("evocation", fireball.school, "fireball school")
        assertEquals("1 action", fireball.castingTime, "fireball castingTime")
        assertEquals(false, fireball.concentration, "fireball concentration")
        assertEquals(false, fireball.ritual, "fireball ritual")
        assertEquals(" sorcerer wizard ", fireball.classList, "fireball classList is space-padded")
        val record = assertIs<SpellRecord>(Kind.SPELLS.decodeOne(fireball.json), "fireball json decodes")
        val body = body(Kind.SPELLS, "fireball")
        assertTrue(body.contains(record.text), "fireball body contains the text")
        assertTrue(body.contains(record.higherLevel), "fireball body contains the higherLevel text")
        assertEquals(record.text + "\n" + record.higherLevel, body, "fireball body is text, newline, higherLevel")
    }

    @Test
    fun spellRowsLeaveTheOtherKindsColumnsNull() {
        for (spell in rows(Kind.SPELLS)) {
            assertNull(spell.classKey, "${spell.key} classKey")
            assertNull(spell.subclassKey, "${spell.key} subclassKey")
            assertNull(spell.parentKey, "${spell.key} parentKey")
            assertNull(spell.category, "${spell.key} category")
            assertNull(spell.subcategory, "${spell.key} subcategory")
            assertNull(spell.rarity, "${spell.key} rarity")
            assertNull(spell.cr, "${spell.key} cr")
        }
    }

    @Test
    fun spellLevelDistributionMatchesTheBundle() {
        val byLevel: Map<Int?, Int> = rows(Kind.SPELLS).groupingBy { it.level }.eachCount()
        assertEquals(
            mapOf<Int?, Int>(0 to 24, 1 to 49, 2 to 54, 3 to 42, 4 to 31, 5 to 37, 6 to 31, 7 to 20, 8 to 16, 9 to 15),
            byLevel,
            "spells per level",
        )
        assertEquals(
            listOf("alarm", "burning-hands", "charm-person"),
            rows(Kind.SPELLS).filter { it.classList!!.contains(" wizard ") && it.level == 1 && it.sortName < "cl" }.map { it.key }.sorted(),
            "classList LIKE filtering on the padded column",
        )
    }

    @Test
    fun equipmentSubcategoryIsArmorWeaponOrNull() {
        val chainMail = row(Kind.EQUIPMENT, "chain-mail")
        assertEquals("armor", chainMail.category, "chain-mail category")
        assertEquals("armor", chainMail.subcategory, "chain-mail subcategory")
        val longsword = row(Kind.EQUIPMENT, "longsword")
        assertEquals("weapon", longsword.category, "longsword category")
        assertEquals("weapon", longsword.subcategory, "longsword subcategory")
        val rope = row(Kind.EQUIPMENT, "rope-hempen-50-feet")
        assertEquals("adventuring-gear", rope.category, "rope category")
        assertNull(rope.subcategory, "rope subcategory")
        val bySub = rows(Kind.EQUIPMENT).groupingBy { it.subcategory }.eachCount()
        assertEquals(13, bySub["armor"], "armor rows")
        assertEquals(37, bySub["weapon"], "weapon rows")
        assertEquals(index.files.getValue("equipment.json").count - 50, bySub[null], "equipment rows without a subcategory")
        for (e in rows(Kind.EQUIPMENT)) {
            assertNull(e.level, "${e.key} level")
            assertNull(e.rarity, "${e.key} rarity")
            assertNull(e.cr, "${e.key} cr")
        }
    }

    @Test
    fun featureRowsCarryClassSubclassLevelAndParent() {
        val channel = row(Kind.FEATURES, "channel-divinity-1-rest")
        assertEquals("cleric", channel.classKey, "channel divinity classKey")
        assertEquals(2, channel.level, "channel divinity level")
        assertNull(channel.subclassKey, "channel divinity is a class feature")
        assertNull(channel.parentKey, "channel divinity is not an option of another feature")
        val disciple = row(Kind.FEATURES, "disciple-of-life")
        assertEquals("cleric", disciple.classKey, "disciple of life classKey")
        assertEquals("life", disciple.subclassKey, "disciple of life subclassKey")
        assertEquals(1, disciple.level, "disciple of life level")
        val invocation = row(Kind.FEATURES, "eldritch-invocation-agonizing-blast")
        assertEquals("eldritch-invocations", invocation.parentKey, "invocation parentKey")
        assertEquals("warlock", invocation.classKey, "invocation classKey")
        assertEquals(84, rows(Kind.FEATURES).count { it.parentKey != null }, "features that are options of another feature")
        assertEquals(88, rows(Kind.FEATURES).count { it.subclassKey != null }, "subclass features")
        for (f in rows(Kind.FEATURES)) assertTrue(f.level in 1..20, "${f.key} level ${f.level} in 1..20")
    }

    @Test
    fun subclassRowsCarryTheirClassKey() {
        assertEquals("cleric", row(Kind.SUBCLASSES, "life").classKey, "life classKey")
        for (s in rows(Kind.SUBCLASSES)) {
            assertTrue(!s.classKey.isNullOrEmpty(), "${s.key} has a classKey")
            assertNull(s.subclassKey, "${s.key} subclassKey")
            assertNull(s.level, "${s.key} level")
        }
    }

    @Test
    fun magicItemRowsSplitVariantsFromBaseItems() {
        val adamantine = row(Kind.MAGIC_ITEMS, "adamantine-armor")
        assertEquals("armor", adamantine.category, "adamantine armor category")
        assertEquals("Uncommon", adamantine.rarity, "adamantine armor rarity")
        assertEquals("base", adamantine.subcategory, "adamantine armor is a base item")
        val ammunition = row(Kind.MAGIC_ITEMS, "ammunition-1")
        assertEquals("variant", ammunition.subcategory, "ammunition +1 is a variant")
        assertEquals("ammunition", ammunition.category, "ammunition +1 category")
        val bySub: Map<String?, Int> = rows(Kind.MAGIC_ITEMS).groupingBy { it.subcategory }.eachCount()
        assertEquals(mapOf<String?, Int>("variant" to 123, "base" to 239), bySub, "magic item variants vs base")
        val body = body(Kind.MAGIC_ITEMS, "adamantine-armor")
        val record = assertIs<MagicItemRecord>(Kind.MAGIC_ITEMS.decodeOne(adamantine.json), "adamantine json decodes")
        assertEquals(record.headline + "\n" + record.text, body, "magic item body is headline, newline, text")
    }

    @Test
    fun creatureRowsCarryTypeSizeAndChallengeRating() {
        val goblin = row(Kind.CREATURES, "goblin")
        assertEquals(0.25, goblin.cr, "goblin cr")
        assertEquals("humanoid", goblin.category, "goblin category is its type")
        assertEquals("Small", goblin.subcategory, "goblin subcategory is its size")
        assertNull(goblin.rarity, "goblin rarity")
        assertNull(goblin.level, "goblin level")
        val body = body(Kind.CREATURES, "goblin")
        assertTrue(body.contains("Scimitar. "), "goblin body names the Scimitar action")
        assertTrue(body.contains("Shortbow. "), "goblin body names the Shortbow action")
        val record = assertIs<CreatureRecord>(Kind.CREATURES.decodeOne(goblin.json), "goblin json decodes")
        for (trait in record.traits) assertTrue(body.contains("${trait.name}. ${trait.text}"), "goblin body carries trait ${trait.name}")
        val dragon = row(Kind.CREATURES, "adult-red-dragon")
        val dragonRecord = assertIs<CreatureRecord>(Kind.CREATURES.decodeOne(dragon.json), "dragon json decodes")
        assertTrue(dragonRecord.legendaryActions.isNotEmpty(), "the dragon has legendary actions")
        val dragonBody = body(Kind.CREATURES, "adult-red-dragon")
        for (la in dragonRecord.legendaryActions) assertTrue(dragonBody.contains("${la.name}. ${la.text}"), "dragon body carries legendary action ${la.name}")
        assertEquals(84, rows(Kind.CREATURES).count { it.cr!! % 1.0 != 0.0 }, "creatures with a fractional cr")
    }

    @Test
    fun creatureBodyStartsWithItsProseAndCarriesEveryReaction() {
        val acolyte = assertIs<CreatureRecord>(Kind.CREATURES.decodeOne(row(Kind.CREATURES, "acolyte").json), "acolyte json decodes")
        assertTrue(acolyte.text.isNotEmpty(), "the acolyte has prose")
        assertTrue(body(Kind.CREATURES, "acolyte").startsWith(acolyte.text + "\n"), "acolyte body starts with its prose, then a newline")
        val knight = assertIs<CreatureRecord>(Kind.CREATURES.decodeOne(row(Kind.CREATURES, "knight").json), "knight json decodes")
        val parry = knight.reactions.single()
        assertEquals("Parry", parry.name, "the knight's one reaction")
        assertTrue(body(Kind.CREATURES, "knight").contains("Parry. ${parry.text}"), "knight body carries the Parry reaction")
        assertTrue(!body(Kind.CREATURES, "goblin").startsWith("\n"), "a creature without prose does not start with a bare newline")
        var withText = 0
        var withReactions = 0
        var withLegendary = 0
        for (b in built.getValue(Kind.CREATURES)) {
            val record = assertIs<CreatureRecord>(Kind.CREATURES.decodeOne(b.record.json), "${b.record.key} json decodes")
            val body = b.search.body
            if (record.text.isNotEmpty()) {
                withText++
                assertTrue(body.startsWith(record.text), "${b.record.key} body starts with its prose")
            } else {
                assertTrue(!body.startsWith("\n"), "${b.record.key} body has no leading newline")
            }
            if (record.reactions.isNotEmpty()) withReactions++
            if (record.legendaryActions.isNotEmpty()) withLegendary++
            for (list in listOf(record.traits, record.actions, record.reactions, record.legendaryActions)) {
                for (action in list) assertTrue(body.contains("${action.name}. ${action.text}"), "${b.record.key} body carries ${action.name}")
            }
        }
        assertEquals(45, withText, "creatures with prose")
        assertEquals(12, withReactions, "creatures with reactions")
        assertEquals(32, withLegendary, "creatures with legendary actions")
    }

    @Test
    fun backgroundBodyIncludesTheFeatureText() {
        val acolyte = row(Kind.BACKGROUNDS, "acolyte")
        val record = assertIs<BackgroundRecord>(Kind.BACKGROUNDS.decodeOne(acolyte.json), "acolyte json decodes")
        val body = body(Kind.BACKGROUNDS, "acolyte")
        assertTrue(body.contains(record.feature.text), "acolyte body contains the feature text")
        assertEquals(record.feature.text + "\n" + record.text, body, "background body is feature text, newline, text")
    }

    @Test
    fun classBodyIsItsSpellcastingInfoAndEmptyForNonCasters() {
        val cleric = body(Kind.CLASSES, "cleric")
        for (name in listOf("Cantrips", "Preparing and Casting Spells", "Spellcasting Ability", "Ritual Casting", "Spellcasting Focus")) {
            assertTrue(cleric.contains("$name. "), "cleric body carries spellcasting info '$name'")
        }
        assertEquals("", body(Kind.CLASSES, "fighter"), "fighter body")
        assertEquals("", body(Kind.CLASSES, "barbarian"), "barbarian body")
    }

    @Test
    fun subraceParentIsItsRaceAndTraitParentIsItsBaseTrait() {
        assertEquals("elf", row(Kind.SUBRACES, "high-elf").parentKey, "high-elf parentKey")
        for (s in rows(Kind.SUBRACES)) assertTrue(!s.parentKey.isNullOrEmpty(), "${s.key} has a raceKey")
        assertEquals("draconic-ancestry", row(Kind.TRAITS, "draconic-ancestry-black").parentKey, "draconic ancestry variant parentKey")
        assertEquals(10, rows(Kind.TRAITS).count { it.parentKey != null }, "trait variants")
        assertNull(row(Kind.TRAITS, "darkvision").parentKey, "darkvision has no parent")
    }

    @Test
    fun ruleSectionsAreOwnedByTheirChapterInReadingOrder() {
        val sections = rows(Kind.RULE_SECTIONS)
        assertEquals(40, sections.size, "rule sections")
        assertEquals(40, chapters.sumOf { it.sections.size }, "sections owned by the nine chapters")
        assertEquals(9, chapters.size, "rules chapters")
        val owner = HashMap<String, String>()
        for (chapter in chapters) {
            for ((i, key) in chapter.sections.withIndex()) {
                assertNull(owner.put(key, chapter.key), "section $key is owned once")
                val section = sections.single { it.key == key }
                assertEquals(chapter.key, section.parentKey, "$key parentKey is its chapter")
                assertEquals(i, section.position, "$key position is its index within ${chapter.key}")
            }
        }
        for (chapter in chapters) {
            val positions = sections.filter { it.parentKey == chapter.key }.map { it.position }.sorted()
            assertEquals((0 until chapter.sections.size).toList(), positions, "${chapter.key} section positions are 0..n-1")
        }
        for (chapter in rows(Kind.RULES)) assertNull(chapter.parentKey, "${chapter.key} chapter has no parent")
    }

    @Test
    fun ruleSectionWithoutAChapterIsRejected() {
        val orphan = """{"edition": "2014", "key": "orphan", "license": "CC-BY-4.0", "name": "Orphan", "source": "srd-5.1", "text": "t", "xref": "/x"}"""
        val record = Kind.RULE_SECTIONS.decodeOne(orphan)
        val failure = assertFailsWith<IllegalStateException>("a section no chapter lists is an error") {
            Rows.of(Kind.RULE_SECTIONS, 0, orphan, record, ImportContext.EMPTY)
        }
        assertTrue(failure.message!!.contains("orphan"), "the error names the section: ${failure.message}")
        val ctx = ImportContext.from(chapters)
        assertEquals("combat" to 0, ctx.sectionOwner[chapters.single { it.key == "combat" }.sections[0]], "first combat section")
    }

    @Test
    fun importContextRejectsASectionListedByTwoChapters() {
        val a = chapters[0]
        val duplicate = a.copy(key = "${a.key}-again", name = "${a.name} again")
        val failure = assertFailsWith<IllegalStateException>("a section owned twice is an error") { ImportContext.from(chapters + duplicate) }
        assertTrue(failure.message!!.contains(a.sections[0]), "the error names the section: ${failure.message}")
        assertFailsWith<IllegalArgumentException>("a non-rule record is rejected") { ImportContext.from(listOf(Kind.CONDITIONS.decodeAll(Fixtures.compendium("conditions.json"))[0])) }
    }

    @Test
    fun positionIsTheArrayIndexForEveryKindExceptRuleSections() {
        for ((kind, list) in built) {
            if (kind == Kind.RULE_SECTIONS) continue
            assertEquals(list.indices.toList(), list.map { it.record.position }, "${kind.id} positions are the array indices")
        }
    }

    @Test
    fun proficienciesHaveAnEmptySearchBody() {
        for (p in built.getValue(Kind.PROFICIENCIES)) assertEquals("", p.search.body, "${p.record.key} body")
    }

    @Test
    fun jsonColumnIsTheRawSliceAndSearchRowMirrorsTheRecordRow() {
        for ((kind, list) in built) {
            val slices = JsonArraySplit.elements(Fixtures.compendium(kind.file))
            assertEquals(index.files.getValue(kind.file).count, list.size, "${kind.id} rows")
            for ((i, b) in list.withIndex()) {
                assertEquals(slices[i], b.record.json, "${kind.id}[$i] json is the raw slice")
                assertEquals(kind.id, b.record.kind, "${kind.id}[$i] record kind")
                assertEquals(kind.id, b.search.kind, "${kind.id}[$i] search kind")
                assertEquals(b.record.key, b.search.key, "${kind.id}[$i] search key")
                assertEquals(b.record.name, b.search.name, "${kind.id}[$i] search name")
                assertEquals(b.record.name.trim().lowercase(), b.record.sortName, "${kind.id}[$i] sortName")
            }
        }
    }

    @Test
    fun rowsOfKeepsTheSliceObjectItIsGiven() {
        val text = Fixtures.compendium(Kind.ALIGNMENTS.file)
        val slice = JsonArraySplit.elements(text)[0]
        val record = Kind.ALIGNMENTS.decodeAll(text)[0]
        val b = Rows.of(Kind.ALIGNMENTS, 0, slice, record, ImportContext.EMPTY)
        assertSame(slice, b.record.json, "json is the very slice object, never re-encoded")
        assertEquals(assertIs<AlignmentRecord>(record).text, b.search.body, "alignment body is its text")
    }

    @Test
    fun rowsOfRejectsARecordOfTheWrongKind() {
        val text = Fixtures.compendium(Kind.ALIGNMENTS.file)
        val slice = JsonArraySplit.elements(text)[0]
        val record = Kind.ALIGNMENTS.decodeAll(text)[0]
        val failure = assertFailsWith<IllegalArgumentException>("an alignment is not a spell") { Rows.of(Kind.SPELLS, 0, slice, record, ImportContext.EMPTY) }
        assertTrue(failure.message!!.contains("spells"), "the error names the kind: ${failure.message}")
    }

    @Test
    fun kindKeyPairsAreUniqueAcrossTheBundle() {
        val all = built.values.flatten()
        assertEquals(1992, all.size, "rows across all kinds")
        assertEquals(index.total, all.size, "rows equal index total")
        assertEquals(all.size, all.map { it.record.kind to it.record.key }.toSet().size, "(kind, key) unique")
        assertEquals(all.size, all.map { it.search.kind to it.search.key }.toSet().size, "(kind, key) unique in search rows")
    }

    @Test
    fun lookupKindsCarryOnlyTheEnvelopeColumns() {
        for (kind in listOf(Kind.CONDITIONS, Kind.SKILLS, Kind.LANGUAGES, Kind.DAMAGE_TYPES, Kind.MAGIC_SCHOOLS, Kind.ALIGNMENTS, Kind.PROFICIENCIES, Kind.WEAPON_PROPERTIES, Kind.FEATS, Kind.RACES, Kind.RULES)) {
            for (r in rows(kind)) {
                assertNull(r.level, "${kind.id}/${r.key} level")
                assertNull(r.school, "${kind.id}/${r.key} school")
                assertNull(r.castingTime, "${kind.id}/${r.key} castingTime")
                assertNull(r.concentration, "${kind.id}/${r.key} concentration")
                assertNull(r.ritual, "${kind.id}/${r.key} ritual")
                assertNull(r.classList, "${kind.id}/${r.key} classList")
                assertNull(r.classKey, "${kind.id}/${r.key} classKey")
                assertNull(r.subclassKey, "${kind.id}/${r.key} subclassKey")
                assertNull(r.parentKey, "${kind.id}/${r.key} parentKey")
                assertNull(r.category, "${kind.id}/${r.key} category")
                assertNull(r.subcategory, "${kind.id}/${r.key} subcategory")
                assertNull(r.rarity, "${kind.id}/${r.key} rarity")
                assertNull(r.cr, "${kind.id}/${r.key} cr")
            }
        }
    }

    @Test
    fun sortNameIsTheTrimmedLowercaseName() {
        assertEquals("fire bolt", SortName.of("  Fire Bolt "), "trim and lowercase")
        assertEquals("ability score improvement", SortName.of("Ability Score Improvement"), "plain name")
        assertEquals("", SortName.of("   "), "blank name")
    }
}
