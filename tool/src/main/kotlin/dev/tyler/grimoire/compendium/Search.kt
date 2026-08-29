package dev.tyler.grimoire.compendium

/**
 * The pure half of compendium search (plan D9). The DAO runs two bounded, dumb queries — a `LIKE` name
 * prefix (string-prefix hits before word-prefix hits, then sortName, so the `LIMIT` keeps the better
 * candidates) and an FTS4 `MATCH` — and everything else happens here: normalising the typed text into each
 * query's argument, ranking the name hits ([rankNames]) and merging the two hit lists into one bounded,
 * kind-grouped result ([merge]). The DAO's order is not the ranking: under sortName "fire bolt" precedes
 * "fireball" because a space sorts before 'b'. Nothing here touches Room, so "fire → Fireball first" is a
 * JVM test. The reader composes `merge(rankNames(prefix, nameHits), textHits)`.
 */
object Search {
    /** The bound on every search query and on the merged result — the S13 list never grows past it. */
    const val LIMIT = 50

    /** Result grouping: the 22 kinds in S13 order. */
    val KIND_ORDER: List<String> = Kind.entries.map { it.id }

    private const val MAX_TERMS = 4
    private val TOKEN = Regex("[\\p{L}\\p{N}']+")

    /**
     * The argument of the name query: trimmed, lowercased, the `LIKE` wildcards `%` and `_` removed;
     * null when fewer than two characters remain (a one-letter prefix matches half the bundle).
     */
    fun likePrefix(input: String): String? {
        val cleaned = input.filterNot { it == '%' || it == '_' }.trim().lowercase()
        return cleaned.takeIf { it.length >= 2 }
    }

    /**
     * The argument of the FTS `MATCH`: up to four `[\p{L}\p{N}']+` tokens, lowercased, each `*`-suffixed
     * (prefix match) and space-joined (implicit AND); null when no token survives.
     */
    fun ftsQuery(input: String): String? {
        val terms = TOKEN.findAll(input).map { it.value.lowercase() }.take(MAX_TERMS).toList()
        return if (terms.isEmpty()) null else terms.joinToString(" ") { "$it*" }
    }

    /**
     * The ranking of the name query's hits for [prefix] (the [likePrefix] the query was given): hits whose
     * sortName starts with the prefix come before hits that only contain it at a word start; within a tier
     * the shortest sortName first — the fewest characters beyond what was typed, so an exact match leads
     * and "fireball" beats "fire bolt" — and sortName breaks the remaining ties. Independent of the input
     * order, so the DAO's `ORDER BY` only decides which candidates survive its `LIMIT`.
     */
    fun rankNames(prefix: String, hits: List<CompendiumRef>): List<CompendiumRef> =
        hits.sortedWith(
            compareBy<CompendiumRef> { if (SortName.of(it.name).startsWith(prefix)) 0 else 1 }
                .thenBy { SortName.of(it.name).length }
                .thenBy { SortName.of(it.name) },
        )

    /**
     * One result list from the two queries. Name hits are taken first, in the order [rankNames] gave them,
     * then text hits; a (kind, key) already present is dropped; the list is cut at [limit] (so name hits
     * survive the cut); finally the survivors are grouped by [kindOrder] with a stable sort, so within a kind
     * the name hits still precede the text hits and a kind absent from [kindOrder] sorts last.
     */
    fun merge(
        nameHits: List<CompendiumRef>,
        textHits: List<CompendiumRef>,
        limit: Int = LIMIT,
        kindOrder: List<String> = KIND_ORDER,
    ): List<CompendiumRef> {
        val seen = HashSet<Pair<String, String>>()
        val picked = ArrayList<CompendiumRef>()
        for (hit in nameHits.asSequence() + textHits.asSequence()) {
            if (picked.size >= limit) break
            if (seen.add(hit.kind to hit.key)) picked += hit
        }
        val rank = HashMap<String, Int>()
        for ((i, kind) in kindOrder.withIndex()) rank.putIfAbsent(kind, i)
        return picked.sortedBy { rank[it.kind] ?: kindOrder.size }
    }
}
