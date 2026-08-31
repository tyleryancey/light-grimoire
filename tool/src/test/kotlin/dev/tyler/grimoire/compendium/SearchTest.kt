package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun mergePutsNameHitsBeforeTextHitsAndDedupesOnKindAndKey() {
        val fireball = ref(Kind.SPELLS, "fireball", "Fireball", 3)
        val fireBolt = ref(Kind.SPELLS, "fire-bolt", "Fire Bolt", 0)
        val burningHands = ref(Kind.SPELLS, "burning-hands", "Burning Hands", 1)
        val sameKeyOtherKind = ref(Kind.MAGIC_ITEMS, "fireball", "Fireball (item)")
        val merged = Search.merge(
            nameHits = listOf(fireball, fireBolt),
            textHits = listOf(burningHands, fireball, sameKeyOtherKind, fireBolt),
        )
        assertEquals(listOf(fireball, fireBolt, burningHands, sameKeyOtherKind), merged, "name hits, then unseen text hits")
    }

    @Test
    fun mergeCapsAtTheLimitAndNameHitsSurviveTheCut() {
        val names = (1..40).map { ref(Kind.CREATURES, "n$it", "Name $it") }
        val texts = (1..40).map { ref(Kind.SPELLS, "t$it", "Text $it") }
        val merged = Search.merge(names, texts)
        assertEquals(50, merged.size, "capped at LIMIT")
        assertEquals(50, Search.LIMIT, "LIMIT")
        assertTrue(names.all { it in merged }, "every name hit survives")
        assertEquals(texts.take(10), merged.filter { it.kind == Kind.SPELLS.id }, "the first ten text hits fill the rest")
        assertEquals(5, Search.merge(names, texts, limit = 5).size, "an explicit limit")
        assertEquals(names.take(5), Search.merge(names, texts, limit = 5), "an explicit limit keeps name hits")
    }

    @Test
    fun mergeGroupsByKindOrderKeepingNameHitsFirstWithinAKind() {
        val fireElemental = ref(Kind.CREATURES, "fire-elemental", "Fire Elemental")
        val fireGiant = ref(Kind.CREATURES, "fire-giant", "Fire Giant")
        val fireball = ref(Kind.SPELLS, "fireball", "Fireball", 3)
        val flameTongue = ref(Kind.MAGIC_ITEMS, "flame-tongue", "Flame Tongue")
        val burningHands = ref(Kind.SPELLS, "burning-hands", "Burning Hands", 1)
        val fireDamage = ref(Kind.DAMAGE_TYPES, "fire", "Fire")
        val salamander = ref(Kind.CREATURES, "salamander", "Salamander")
        val merged = Search.merge(
            nameHits = listOf(fireElemental, fireGiant, fireball, fireDamage),
            textHits = listOf(flameTongue, burningHands, salamander, fireElemental),
        )
        assertEquals(
            listOf(fireball, burningHands, flameTongue, fireElemental, fireGiant, salamander, fireDamage),
            merged,
            "S13 kind order (spells, magic items, creatures, lookup), name hits before text hits within a kind",
        )
        assertEquals(
            listOf(fireElemental, fireGiant, salamander, fireball, burningHands, flameTongue, fireDamage),
            Search.merge(
                nameHits = listOf(fireElemental, fireGiant, fireball, fireDamage),
                textHits = listOf(flameTongue, burningHands, salamander, fireElemental),
                kindOrder = listOf("creatures", "spells", "magic_items", "damage_types"),
            ),
            "a custom kind order is honoured",
        )
        assertEquals(Kind.entries.map { it.id }, Search.KIND_ORDER, "the default kind order is the S13 order")
    }

    @Test
    fun mergeKeepsUnknownKindsLastWithoutDropping() {
        val known = ref(Kind.SPELLS, "fireball", "Fireball", 3)
        val unknown = CompendiumRef(kind = "monsters", key = "x", name = "X", level = null, school = null, category = null, subcategory = null, rarity = null, cr = null, classKey = null)
        assertEquals(listOf(known, unknown), Search.merge(listOf(unknown, known), emptyList()), "unknown kind sorts last")
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
        val merged = Search.merge(Search.rankNames(prefix, daoOrder), textHits)
        assertEquals("Fireball", merged.first().name, "Fireball first")
        assertEquals(
            listOf("Fireball", "Fire Bolt", "Fire Storm", "Fire Shield", "Faerie Fire", "Wall of Fire", "Delayed Blast Fireball", "Burning Hands"),
            merged.filter { it.kind == Kind.SPELLS.id }.map { it.name },
            "spells: string-prefix name hits shortest first, word-prefix name hits shortest first, then the text hit",
        )
        assertEquals(merged, Search.merge(Search.rankNames(prefix, daoOrder.reversed()), textHits), "the DAO's order does not leak into the ranking")
        assertEquals(merged, Search.merge(Search.rankNames(prefix, daoOrder.shuffled(Random(11))), textHits), "the DAO's order does not leak into the ranking, shuffled")
        assertEquals(merged.size, merged.map { it.kind to it.key }.toSet().size, "no duplicates")
        assertEquals(daoOrder.size + 3, merged.size, "every name hit plus the three unseen text hits")
        assertEquals("Fire", merged.last().name, "the exact match of a lookup kind is grouped last by kind, not first")
    }

    @Test
    fun shieldRanksTheExactMatchFirstWithinEachKindOverTheRealBundle() {
        val prefix = assertNotNull(Search.likePrefix("shield"), "the name query argument")
        val merged = Search.merge(Search.rankNames(prefix, daoNameMatches(prefix)), emptyList())
        assertEquals(12, merged.size, "name hits for 'shield' in the bundle")
        assertEquals(
            listOf(Kind.SPELLS.id to "Shield", Kind.SPELLS.id to "Shield of Faith", Kind.SPELLS.id to "Fire Shield", Kind.EQUIPMENT.id to "Shield"),
            merged.take(4).map { it.kind to it.name },
            "the exact match leads its kind, the word-prefix hit follows the string-prefix hits, kinds stay grouped",
        )
        assertEquals(Kind.PROFICIENCIES.id to "Shields", merged.last().let { it.kind to it.name }, "the lookup kind comes last")
    }
}
