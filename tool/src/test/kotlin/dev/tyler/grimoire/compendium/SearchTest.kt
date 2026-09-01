package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan D9: the two DAO queries are bounded and dumb; the input normalisation and the ranking are pure and
 * live here so they can be pinned on the JVM. The DAO's name query only bounds the candidates (string prefix
 * before word prefix, then sortName, LIMIT 50) — that order is *not* the ranking: under it "fire bolt" sorts
 * before "fireball" because a space sorts before 'b'. [Search.rankNames] is the ranking, and the "fire →
 * Fireball first" cases below run it over the real bundle behind a JVM model of the DAO query, so the only
 * thing left to the device check is the SQL itself.
 */
class SearchTest {
    private companion object {
        /** Every record of the bundle as the REF projection the DAO returns; Search reads only kind, key, name. */
        val bundle: List<CompendiumRef> by lazy {
            Kind.entries.flatMap { kind ->
                kind.decodeAll(Fixtures.compendium(kind.file)).map { record ->
                    CompendiumRef(kind = kind.id, key = record.key, name = record.name, level = null, school = null, category = null, subcategory = null, rarity = null, cr = null, classKey = null)
                }
            }
        }
    }

    private fun ref(kind: Kind, key: String, name: String, level: Int? = null): CompendiumRef =
        CompendiumRef(kind = kind.id, key = key, name = name, level = level, school = null, category = null, subcategory = null, rarity = null, cr = null, classKey = null)

    /**
     * The DAO's `nameMatches` (plan §DAO) modelled on the JVM over the real bundle: `name LIKE :p || '%' OR
     * name LIKE '% ' || :p || '%'`, `ORDER BY CASE WHEN name LIKE :p || '%' THEN 0 ELSE 1 END, sortName`,
     * `LIMIT :limit`. SQLite's LIKE folds ASCII case only, which is every letter in the bundle's names (the
     * only non-ASCII character is the × in four carpet sizes), and BINARY collation orders the lowercase
     * sortNames the way String comparison does.
     */
    private fun daoNameMatches(prefix: String, limit: Int = Search.LIMIT): List<CompendiumRef> {
        fun stringPrefix(hit: CompendiumRef) = hit.name.lowercase().startsWith(prefix)
        return bundle
            .filter { stringPrefix(it) || it.name.lowercase().contains(" $prefix") }
            .sortedWith(compareBy({ if (stringPrefix(it)) 0 else 1 }, { SortName.of(it.name) }))
            .take(limit)
    }

    @Test
    fun likePrefixTrimsLowercasesAndStripsTheLikeWildcards() {
        val cases = mapOf(
            "Fire" to "fire",
            "  Fire ball " to "fire ball",
            "%fi_re%" to "fire",
            "ÉPÉE" to "épée",
            "Will-o'-Wisp" to "will-o'-wisp",
        )
        for ((input, expected) in cases) assertEquals(expected, Search.likePrefix(input), "likePrefix('$input')")
    }

    @Test
    fun likePrefixIsNullForFewerThanTwoUsableCharacters() {
        for (input in listOf("", " ", "f", " f ", "%", "%_", "f%")) assertNull(Search.likePrefix(input), "likePrefix('$input')")
        assertEquals("fi", Search.likePrefix("fi"), "two characters are enough")
    }

    @Test
    fun ftsQueryTokenisesLowercasesAndPrefixesEveryTerm() {
        assertEquals("fire* ball*", Search.ftsQuery("Fire ball!"), "two words")
        assertEquals("fire*", Search.ftsQuery("  FIRE  "), "one word, padded")
        assertEquals("fire* bolt*", Search.ftsQuery("fire-bolt"), "hyphen separates")
        assertEquals("don't*", Search.ftsQuery("Don't"), "apostrophe stays inside a token")
        assertEquals("épée* 2*", Search.ftsQuery("Épée 2"), "letters and digits of any script")
        assertEquals("a* b*", Search.ftsQuery("a\"b"), "a quote is a separator")
    }

    @Test
    fun ftsQueryIsNullWhenNoTokenSurvives() {
        for (input in listOf("", "   ", "!?", "\"*\"", "-")) assertNull(Search.ftsQuery(input), "ftsQuery('$input')")
    }

    @Test
    fun ftsQueryKeepsAtMostFourTerms() {
        assertEquals("a* b* c* d*", Search.ftsQuery("a b c d e f"), "the first four terms")
        assertEquals("one* two* three*", Search.ftsQuery("one two three"), "three terms stay three")
    }

    @Test
    fun splitKeepsNameHitsInTheirOwnTierAndDedupesOnKindAndKey() {
        val fireball = ref(Kind.SPELLS, "fireball", "Fireball", 3)
        val fireBolt = ref(Kind.SPELLS, "fire-bolt", "Fire Bolt", 0)
        val burningHands = ref(Kind.SPELLS, "burning-hands", "Burning Hands", 1)
        val sameKeyOtherKind = ref(Kind.MAGIC_ITEMS, "fireball", "Fireball (item)")
        val results = Search.split(
            nameHits = listOf(fireball, fireBolt),
            textHits = listOf(burningHands, fireball, sameKeyOtherKind, fireBolt),
        )
        assertEquals(listOf(fireball, fireBolt), results.named, "the name hits")
        assertEquals(
            listOf(burningHands, sameKeyOtherKind),
            results.mentioned,
            "the unseen text hits; a record already named is never demoted to a mention",
        )
        assertEquals(4, results.size, "hits across both tiers")
        assertFalse(results.isEmpty, "something was found")
        assertTrue(Search.split(emptyList(), emptyList()).isEmpty, "neither tier found anything")
    }

    @Test
    fun splitCapsTheTwoTiersTogetherAndNameHitsSurviveTheCut() {
        val names = (1..40).map { ref(Kind.CREATURES, "n$it", "Name $it") }
        val texts = (1..40).map { ref(Kind.SPELLS, "t$it", "Text $it") }
        // Every text hit here is one kind, so the per-kind quota would decide this instead of the cap;
        // lifted, to keep the assertion about the bound it names. The quota has its own test below.
        val loose = Int.MAX_VALUE
        val results = Search.split(names, texts, mentionsPerKind = loose)
        assertEquals(50, results.size, "capped at LIMIT across both tiers")
        assertEquals(50, Search.LIMIT, "LIMIT")
        assertEquals(names, results.named, "every name hit survives")
        assertEquals(texts.take(10), results.mentioned, "the first ten text hits fill the rest")
        val tight = Search.split(names, texts, limit = 5, mentionsPerKind = loose)
        assertEquals(names.take(5), tight.named, "an explicit limit keeps name hits")
        assertEquals(emptyList(), tight.mentioned, "and leaves no room for a mention")
        val flooded = Search.split((1..60).map { ref(Kind.CREATURES, "n$it", "Name $it") }, texts, mentionsPerKind = loose)
        assertEquals(50, flooded.named.size, "fifty name hits fill the whole budget")
        assertEquals(emptyList(), flooded.mentioned, "so nothing is merely mentioned")
    }

    @Test
    fun splitLetsNoOneKindFillTheMentionsTier() {
        // The FTS query returns hits in import order, so without a quota the first kind takes every slot —
        // "hit points" drew fifty spells and the rule section that answers it never reached the screen.
        val spells = (1..40).map { ref(Kind.SPELLS, "s$it", "Spell $it") }
        val sections = (1..40).map { ref(Kind.RULE_SECTIONS, "r$it", "Section $it") }
        val results = Search.split(emptyList(), spells + sections)
        assertEquals(
            Search.MENTIONS_PER_KIND,
            results.mentioned.count { it.kind == Kind.SPELLS.id },
            "the loud kind takes its quota and no more",
        )
        assertEquals(
            Search.MENTIONS_PER_KIND,
            results.mentioned.count { it.kind == Kind.RULE_SECTIONS.id },
            "so the kind behind it is reachable",
        )
        // The tier comes back short of the budget rather than topping itself up with more of one kind.
        assertEquals(2 * Search.MENTIONS_PER_KIND, results.mentioned.size, "ten mentions, not fifty")
    }

    @Test
    fun splitGroupsEachTierByKindOrderKeepingTheRankingWithinAKind() {
        val fireElemental = ref(Kind.CREATURES, "fire-elemental", "Fire Elemental")
        val fireGiant = ref(Kind.CREATURES, "fire-giant", "Fire Giant")
        val fireball = ref(Kind.SPELLS, "fireball", "Fireball", 3)
        val flameTongue = ref(Kind.MAGIC_ITEMS, "flame-tongue", "Flame Tongue")
        val burningHands = ref(Kind.SPELLS, "burning-hands", "Burning Hands", 1)
        val fireDamage = ref(Kind.DAMAGE_TYPES, "fire", "Fire")
        val salamander = ref(Kind.CREATURES, "salamander", "Salamander")
        val nameHits = listOf(fireElemental, fireGiant, fireball, fireDamage)
        val textHits = listOf(flameTongue, burningHands, salamander, fireElemental)
        val results = Search.split(nameHits, textHits)
        assertEquals(
            listOf(fireball, fireElemental, fireGiant, fireDamage),
            results.named,
            "S13 kind order (spells, creatures, lookup), the rankNames order kept within a kind",
        )
        assertEquals(
            listOf(burningHands, flameTongue, salamander),
            results.mentioned,
            "the mentions are grouped by the same kind order, even though S13.4 draws them flat",
        )
        val custom = Search.split(nameHits, textHits, kindOrder = listOf("creatures", "spells", "magic_items", "damage_types"))
        assertEquals(listOf(fireElemental, fireGiant, fireball, fireDamage), custom.named, "a custom kind order is honoured")
        assertEquals(listOf(salamander, burningHands, flameTongue), custom.mentioned, "in both tiers")
        assertEquals(Kind.entries.map { it.id }, Search.KIND_ORDER, "the default kind order is the S13 order")
    }

    @Test
    fun splitKeepsUnknownKindsLastWithoutDropping() {
        val known = ref(Kind.SPELLS, "fireball", "Fireball", 3)
        val unknown = CompendiumRef(kind = "monsters", key = "x", name = "X", level = null, school = null, category = null, subcategory = null, rarity = null, cr = null, classKey = null)
        assertEquals(listOf(known, unknown), Search.split(listOf(unknown, known), emptyList()).named, "unknown kind sorts last")
        val otherUnknown = CompendiumRef(kind = "vehicles", key = "y", name = "Y", level = null, school = null, category = null, subcategory = null, rarity = null, cr = null, classKey = null)
        assertEquals(
            listOf(known, otherUnknown),
            Search.split(emptyList(), listOf(otherUnknown, known)).mentioned,
            "and in the mention tier too",
        )
    }

    @Test
    fun rankNamesPutsStringPrefixHitsFirstShortestFirstThenBySortName() {
        val fireball = ref(Kind.SPELLS, "fireball", "Fireball", 3)
        val fireBolt = ref(Kind.SPELLS, "fire-bolt", "Fire Bolt", 0)
        val fireStorm = ref(Kind.SPELLS, "fire-storm", "Fire Storm", 7)
        val fireGiant = ref(Kind.CREATURES, "fire-giant", "Fire Giant")
        val fireDamage = ref(Kind.DAMAGE_TYPES, "fire", "Fire")
        val wallOfFire = ref(Kind.SPELLS, "wall-of-fire", "Wall of Fire", 4)
        val faerieFire = ref(Kind.SPELLS, "faerie-fire", "Faerie Fire", 1)
        // What the DAO hands over: CASE (string prefix first), then sortName — Fireball last of its tier.
        val daoOrder = listOf(fireDamage, fireBolt, fireGiant, fireStorm, fireball, faerieFire, wallOfFire)
        val expected = listOf(fireDamage, fireball, fireBolt, fireGiant, fireStorm, faerieFire, wallOfFire)
        assertEquals(expected, Search.rankNames("fire", daoOrder), "exact, then string prefix shortest first (a tie by sortName), then word prefix shortest first")
        assertEquals(expected, Search.rankNames("fire", daoOrder.reversed()), "the input order does not matter")
        assertEquals(expected, Search.rankNames("fire", daoOrder.shuffled(Random(7))), "the input order does not matter, shuffled")
        assertEquals(emptyList(), Search.rankNames("fire", emptyList()), "nothing to rank")
        assertEquals(listOf(fireBolt, fireball), Search.rankNames("fire bolt", listOf(fireball, fireBolt)), "a two-word prefix ranks its string-prefix hit first")
    }

    @Test
    fun fireRanksFireballFirstOverTheRealBundle() {
        val prefix = assertNotNull(Search.likePrefix("Fire"), "the name query argument")
        val daoOrder = daoNameMatches(prefix)
        assertEquals(25, daoOrder.size, "name hits for 'fire' in the bundle")
        assertEquals(
            listOf("fire", "fire-bolt", "fire-elemental", "elemental-gem-fire", "fire-giant", "fire-shield", "fire-storm", "fireball"),
            daoOrder.take(8).map { it.key },
            "the DAO's own order: the string-prefix tier by sortName, where a space sorts before 'b' and Fireball comes last",
        )
        val textHits = listOf(
            ref(Kind.SPELLS, "burning-hands", "Burning Hands", 1),
            daoOrder.single { it.key == "fireball" },
            ref(Kind.MAGIC_ITEMS, "flame-tongue", "Flame Tongue"),
            ref(Kind.CREATURES, "adult-red-dragon", "Adult Red Dragon"),
        )
        val results = Search.split(Search.rankNames(prefix, daoOrder), textHits)
        assertEquals("Fireball", results.named.first().name, "Fireball first")
        assertEquals(
            listOf("Fireball", "Fire Bolt", "Fire Storm", "Fire Shield", "Faerie Fire", "Wall of Fire", "Delayed Blast Fireball"),
            results.named.filter { it.kind == Kind.SPELLS.id }.map { it.name },
            "named spells: string-prefix hits shortest first, then word-prefix hits shortest first",
        )
        assertEquals(listOf("Burning Hands"), results.mentioned.filter { it.kind == Kind.SPELLS.id }.map { it.name }, "the text hit is a mention")
        assertEquals(results, Search.split(Search.rankNames(prefix, daoOrder.reversed()), textHits), "the DAO's order does not leak into the ranking")
        assertEquals(results, Search.split(Search.rankNames(prefix, daoOrder.shuffled(Random(11))), textHits), "the DAO's order does not leak into the ranking, shuffled")
        val all = results.named + results.mentioned
        assertEquals(all.size, all.map { it.kind to it.key }.toSet().size, "no duplicates")
        assertEquals(daoOrder.size, results.named.size, "every name hit is named")
        assertEquals(3, results.mentioned.size, "the three unseen text hits are mentions")
        assertEquals("Fire", results.named.last().name, "the exact match of a lookup kind is grouped last by kind, not first")
    }

    @Test
    fun shieldRanksTheExactMatchFirstWithinEachKindOverTheRealBundle() {
        val prefix = assertNotNull(Search.likePrefix("shield"), "the name query argument")
        val results = Search.split(Search.rankNames(prefix, daoNameMatches(prefix)), emptyList())
        assertEquals(12, results.named.size, "name hits for 'shield' in the bundle")
        assertEquals(emptyList(), results.mentioned, "no text hits were given")
        assertEquals(
            listOf(Kind.SPELLS.id to "Shield", Kind.SPELLS.id to "Shield of Faith", Kind.SPELLS.id to "Fire Shield", Kind.EQUIPMENT.id to "Shield"),
            results.named.take(4).map { it.kind to it.name },
            "the exact match leads its kind, the word-prefix hit follows the string-prefix hits, kinds stay grouped",
        )
        assertEquals(Kind.PROFICIENCIES.id to "Shields", results.named.last().let { it.kind to it.name }, "the lookup kind comes last")
    }
}
