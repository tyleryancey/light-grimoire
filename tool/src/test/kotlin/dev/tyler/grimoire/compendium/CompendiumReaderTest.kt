package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.ArmorStats
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The typed read facade over the DAO (plan §Files: CompendiumReader): records decode from their `json`
 * column with the kind's own model, `getAll` restores the caller's order, the armor bridge feeds Derive
 * (plan D11) and `search` composes the two bounded queries with the pure ranking (plan D9). The DAO is
 * [FakeCompendiumDao] over the whole bundle, so the expectations are the sha256-pinned assets, not samples.
 */
class CompendiumReaderTest {
    private companion object {
        val built: List<Rows.Built> by lazy {
            var ctx = ImportContext.EMPTY
            Kind.entries.flatMap { kind ->
                val text = Fixtures.compendium(kind.file)
                val slices = JsonArraySplit.elements(text)
                val records = kind.decodeAll(text)
                if (kind == Kind.RULES) ctx = ImportContext.from(records)
                records.indices.map { Rows.of(kind, it, slices[it], records[it], ctx) }
            }
        }
    }

    private fun dao() = FakeCompendiumDao(built)

    private fun firstKeyOf(kind: Kind): String = built.first { it.record.kind == kind.id }.record.key

    @Test
    fun getDecodesTheRawJsonSliceWithTheKindsModel() = runBlocking {
        val reader = CompendiumReader(dao())
        val fireball = reader.spell("fireball")
        val slice = built.single { it.record.kind == "spells" && it.record.key == "fireball" }.record.json
        assertEquals<CompendiumRecord?>(Kind.SPELLS.decodeOne(slice), fireball, "fireball decodes from its json column")
        assertEquals(3, fireball?.level, "fireball level")
        assertNull(reader.spell("no-such-spell"), "an unknown key is null")
        assertNull(reader.condition("fireball"), "a key of another kind is null")
        val blinded = assertIs<TextRecord>(reader.get(Kind.CONDITIONS, "blinded"), "the untyped get uses the kind's serializer")
        assertEquals("Blinded", blinded.name, "blinded name")
    }

    @Test
    fun everyPerKindAccessorPairsItsKindWithItsModel() = runBlocking {
        val reader = CompendiumReader(dao())
        val cases: List<Triple<Kind, suspend (String) -> CompendiumRecord?, (CompendiumRecord) -> Boolean>> = listOf(
            Triple(Kind.SPELLS, reader::spell) { it is SpellRecord },
            Triple(Kind.CONDITIONS, reader::condition) { it is TextRecord },
            Triple(Kind.RULES, reader::rule) { it is RuleRecord },
            Triple(Kind.RULE_SECTIONS, reader::ruleSection) { it is TextRecord },
            Triple(Kind.CLASSES, reader::characterClass) { it is ClassRecord },
            Triple(Kind.SUBCLASSES, reader::subclass) { it is SubclassRecord },
            Triple(Kind.FEATURES, reader::feature) { it is FeatureRecord },
            Triple(Kind.RACES, reader::race) { it is RaceRecord },
            Triple(Kind.SUBRACES, reader::subrace) { it is SubraceRecord },
            Triple(Kind.TRAITS, reader::trait) { it is TraitRecord },
            Triple(Kind.BACKGROUNDS, reader::background) { it is BackgroundRecord },
            Triple(Kind.FEATS, reader::feat) { it is FeatRecord },
            Triple(Kind.EQUIPMENT, reader::equipment) { it is EquipmentRecord },
            Triple(Kind.WEAPON_PROPERTIES, reader::weaponProperty) { it is TextRecord },
            Triple(Kind.MAGIC_ITEMS, reader::magicItem) { it is MagicItemRecord },
            Triple(Kind.CREATURES, reader::creature) { it is CreatureRecord },
            Triple(Kind.SKILLS, reader::skill) { it is SkillRecord },
            Triple(Kind.LANGUAGES, reader::language) { it is LanguageRecord },
            Triple(Kind.DAMAGE_TYPES, reader::damageType) { it is TextRecord },
            Triple(Kind.MAGIC_SCHOOLS, reader::magicSchool) { it is TextRecord },
            Triple(Kind.ALIGNMENTS, reader::alignment) { it is AlignmentRecord },
            Triple(Kind.PROFICIENCIES, reader::proficiency) { it is ProficiencyRecord },
        )
        assertEquals(Kind.entries.toList(), cases.map { it.first }, "one accessor per kind, in Kind order")
        for ((kind, accessor, isModel) in cases) {
            val key = firstKeyOf(kind)
            val record = accessor(key)
            assertEquals(key, record?.key, "${kind.id}: accessor resolves '$key'")
            assertTrue(isModel(record!!), "${kind.id}: '$key' decodes with the ${kind.id} model")
            assertEquals(kind.decodeOne(built.single { it.record.kind == kind.id && it.record.key == key }.record.json), record, "${kind.id}: '$key' equals the slice decoded directly")
        }
    }

    @Test
    fun getAllRestoresTheCallersOrderAndDropsUnknownKeys() = runBlocking {
        val reader = CompendiumReader(dao())
        val spells = reader.getAll(Kind.SPELLS, listOf("magic-missile", "fireball", "no-such-spell", "acid-arrow"), SpellRecord.serializer())
        assertEquals(listOf("Magic Missile", "Fireball", "Acid Arrow"), spells.map { it.name }, "caller order, unknown key dropped")
        val reversed = reader.getAll(Kind.SPELLS, listOf("acid-arrow", "fireball", "magic-missile"), SpellRecord.serializer())
        assertEquals(listOf("Acid Arrow", "Fireball", "Magic Missile"), reversed.map { it.name }, "the order is the caller's, not the table's")
        assertEquals(emptyList(), reader.getAll(Kind.SPELLS, listOf("no-such-spell"), SpellRecord.serializer()), "only unknown keys")
    }

    @Test
    fun getAllIsBoundedAndSkipsTheQueryForNoKeys() = runBlocking {
        val dao = dao()
        val reader = CompendiumReader(dao)
        assertEquals(emptyList(), reader.getAll(Kind.SPELLS, emptyList(), SpellRecord.serializer()), "no keys, no records")
        assertEquals(emptyList(), dao.calls, "no keys, no query")
        val tooMany = (1..CompendiumReader.MAX_KEYS + 1).map { "key-$it" }
        assertFailsWith<IllegalArgumentException>("more than MAX_KEYS keys is a programming error") {
            reader.getAll(Kind.SPELLS, tooMany, SpellRecord.serializer())
        }
        val atTheBound = (1..CompendiumReader.MAX_KEYS).map { "key-$it" }
        assertEquals(emptyList(), reader.getAll(Kind.SPELLS, atTheBound, SpellRecord.serializer()), "exactly MAX_KEYS keys is allowed")
        assertEquals(listOf("getAll"), dao.calls, "one query for MAX_KEYS keys")
    }

    @Test
    fun armorTableComesFromTheThirteenArmorRowsAndMatchesTheFixtures() = runBlocking {
        val dao = dao()
        val table = CompendiumReader(dao).armorTable()
        assertEquals(Fixtures.armorTable(), table, "armor table through the DAO seam")
        assertEquals(13, table.size, "thirteen armor rows")
        assertEquals(ArmorStats(base = 16, dexBonus = false, maxBonus = null), table["chain-mail"], "chain-mail")
        assertEquals(listOf("bySubcategory", "getAll"), dao.calls, "one list query for the armor refs, one fetch of their json")
    }

    @Test
    fun searchPutsFireballFirstForFireAndNeverRepeatsARecord() = runBlocking {
        val hits = CompendiumReader(dao()).search("fire")
        assertEquals("Fireball", hits.first().name, "Fireball leads the 'fire' search")
        assertEquals("spells", hits.first().kind, "the leading hit is the spell")
        assertTrue(hits.any { it.kind == "spells" && it.key == "fire-bolt" }, "Fire Bolt is among the hits")
        assertTrue(hits.any { it.kind == "damage_types" && it.key == "fire" }, "the fire damage type is among the hits")
        assertEquals(hits.size, hits.map { it.kind to it.key }.toSet().size, "no (kind, key) twice")
        assertTrue(hits.size <= Search.LIMIT, "bounded by Search.LIMIT")
        val kindRank = hits.map { Search.KIND_ORDER.indexOf(it.kind) }
        assertEquals(kindRank.sorted(), kindRank, "grouped by S13 kind order")
    }

    @Test
    fun searchSkipsTheNameQueryForAOneCharacterInputAndBothForNothing() = runBlocking {
        val dao = dao()
        val reader = CompendiumReader(dao)
        val single = reader.search("f")
        assertEquals(listOf("textMatches"), dao.calls, "one character: the FTS query only")
        assertTrue(single.isNotEmpty(), "'f*' still matches text")
        dao.calls.clear()
        assertEquals(emptyList(), reader.search("   "), "blank input finds nothing")
        assertEquals(emptyList(), dao.calls, "blank input runs no query")
        dao.calls.clear()
        assertEquals(emptyList(), reader.search("%_"), "wildcards alone find nothing")
        assertEquals(emptyList(), dao.calls, "wildcards alone run no query")
    }

    @Test
    fun searchScopedToKindsUsesTheScopedQueriesAndReturnsOnlyThoseKinds() = runBlocking {
        val dao = dao()
        val hits = CompendiumReader(dao).search("fire", listOf(Kind.SPELLS, Kind.MAGIC_ITEMS))
        assertEquals(listOf("nameMatchesIn", "textMatchesIn"), dao.calls, "the kind-scoped pair of queries")
        assertTrue(hits.isNotEmpty(), "scoped hits exist")
        assertEquals(setOf("spells", "magic_items"), hits.map { it.kind }.toSet(), "only the requested kinds")
        assertEquals("Fireball", hits.first().name, "Fireball still leads")
        val unscopedDao = dao()
        val unscoped = CompendiumReader(unscopedDao).search("fire")
        assertEquals(listOf("nameMatches", "textMatches"), unscopedDao.calls, "the unscoped pair of queries")
        assertTrue(unscoped.size >= hits.size, "the unscoped search is a superset in size")
        assertEquals(emptyList(), CompendiumReader(dao()).search("fire", listOf(Kind.CONDITIONS)), "no condition mentions fire")
    }

    @Test
    fun listPassThroughsReachTheDaoUnchanged() = runBlocking {
        val dao = dao()
        val reader = CompendiumReader(dao)
        val classes = reader.listInOrder(Kind.CLASSES, 12)
        assertEquals(
            listOf("barbarian", "bard", "cleric", "druid", "fighter", "monk", "paladin", "ranger", "rogue", "sorcerer", "warlock", "wizard"),
            classes.map { it.key },
            "the twelve classes in asset order",
        )
        val counts = reader.countsByKind().associate { it.kind to it.n }
        val index = Fixtures.compendiumIndex()
        for (kind in Kind.entries) {
            assertEquals(index.files.getValue(kind.file).count, counts[kind.id], "${kind.id} count")
        }
        assertEquals(13, reader.bySubcategory(Kind.EQUIPMENT, "armor").size, "armor refs")
        assertEquals(37, reader.bySubcategory(Kind.EQUIPMENT, "weapon").size, "weapon refs")
        assertEquals(123, reader.bySubcategory(Kind.MAGIC_ITEMS, "variant").size, "variant magic items")
        assertEquals(24, reader.spellsByLevel(0).size, "cantrips")
        assertTrue(reader.subclassesOf("cleric").any { it.key == "life" }, "the Life domain is a cleric subclass")
        assertTrue(reader.children(Kind.SUBRACES, "elf").any { it.key == "high-elf" }, "high elf is a child of elf")
        assertEquals(listOf("listInOrder", "countsByKind", "bySubcategory", "bySubcategory", "bySubcategory", "spellsByLevel", "subclassesOf", "children"), dao.calls, "each pass-through is one DAO call")
    }

    @Test
    fun refsCarryClassKeyForFeatureDisambiguation() = runBlocking {
        val reader = CompendiumReader(dao())
        val rage = reader.classFeatures("barbarian", 1).single { it.key == "rage" }
        assertEquals("barbarian", rage.classKey, "a feature ref carries its class for the list subtitle")
        assertEquals(1, rage.level, "rage level")
        val fireball = reader.spellsByLevel(3).single { it.key == "fireball" }
        assertNull(fireball.classKey, "a spell ref has no classKey")
    }
}
