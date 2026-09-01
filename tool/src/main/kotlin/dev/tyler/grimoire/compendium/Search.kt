package dev.tyler.grimoire.compendium

/**
 * The pure half of compendium search (plan D9). The DAO runs two bounded, dumb queries — a `LIKE` name
 * prefix (string-prefix hits before word-prefix hits, then sortName, so the `LIMIT` keeps the better
 * candidates) and an FTS4 `MATCH` — and everything else happens here: normalising the typed text into each
 * query's argument, ranking the name hits ([rankNames]) and splitting the two hit lists into the bounded,
 * kind-grouped two-tier [Results] ([split]). The DAO's order is not the ranking: under sortName "fire bolt"
 * precedes "fireball" because a space sorts before 'b'. Nothing here touches Room, so "fire → Fireball first"
 * is a JVM test. The reader composes `split(rankNames(prefix, nameHits), textHits)`.
 */
object Search {
    /** The bound on every search query and on the two tiers together — the S13 list never grows past it. */
    const val LIMIT = 50

    /**
     * How many candidates each DAO query fetches before any ranking happens — deliberately wider than
     * [LIMIT], because neither query's SQL order is a ranking. The name query orders by `sortName`, so a
     * `LIMIT` of 50 cuts alphabetically: "giant" has 53 name hits and lost **Stone Giant** and **Storm
     * Giant** entirely while keeping fourteen "… Giant Strength" potions. The FTS query has no `ORDER BY`
     * at all and comes back in docid order, which is import order, so "hit points" returned fifty spells and
     * the rules section that answers it was never fetched. Fetching wider lets [rankNames] and [split] make
     * the cut on merit; 250 rows of the [CompendiumRef] projection is a fraction of one 27 KB rule section.
     */
    const val FETCH = 250

    /**
     * The most mentions of any one kind in [split]'s mentions tier. Without it the loudest kind fills the
     * tier — "hit points" drew fifty spell rows and nothing else, and the rule section that answers it never
     * reached the screen. The tier is allowed to come back short of [LIMIT] rather than top itself up with
     * more of the same kind: "where else this appears" is worth five rows a kind, not fifty of one.
     */
    const val MENTIONS_PER_KIND = 5

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
     * The two tiers of one search: [named] is what the name query matched, [mentioned] what only a body
     * mentioned. Both are already deduped, kind-grouped and jointly bounded by [split].
     */
    data class Results(
        val named: List<CompendiumRef>,
        val mentioned: List<CompendiumRef>,
    ) {
        /** Nothing found — the screen's one "No matches." line, not an empty tier. */
        val isEmpty: Boolean get() = named.isEmpty() && mentioned.isEmpty()

        /** Hits across both tiers; never above the `limit` [split] was given. */
        val size: Int get() = named.size + mentioned.size
    }

    /**
     * The two queries' hits as two tiers rather than one blended list. Blending them buried name matches: one
     * list grouped by kind puts a name hit behind every body mention of an earlier kind, and the equipment
     * **Shield** landed at row 23 of the "shield" results, three screenfuls below the spell of the same name.
     * A tier of its own keeps every name match above every mention, whatever kind it belongs to.
     *
     * A (kind, key) seen already is dropped, the name tier winning a duplicate — a record whose name matched is
     * never demoted to a mention. The two tiers together are cut at [limit], name hits taken first so they
     * always survive the cut (50 name hits leave [Results.mentioned] empty). Each tier is then grouped by
     * [kindOrder] with a stable sort of its own, so [rankNames]' order still holds within a kind and a kind
     * absent from [kindOrder] sorts last.
     */
    fun split(
        nameHits: List<CompendiumRef>,
        textHits: List<CompendiumRef>,
        limit: Int = LIMIT,
        kindOrder: List<String> = KIND_ORDER,
        mentionsPerKind: Int = MENTIONS_PER_KIND,
    ): Results {
        val seen = HashSet<Pair<String, String>>()
        val named = ArrayList<CompendiumRef>()
        for (hit in nameHits) {
            if (named.size >= limit) break
            if (seen.add(hit.kind to hit.key)) named += hit
        }
        val mentioned = ArrayList<CompendiumRef>()
        val perKind = HashMap<String, Int>()
        for (hit in textHits) {
            if (named.size + mentioned.size >= limit) break
            if (perKind.getOrElse(hit.kind) { 0 } >= mentionsPerKind) continue
            if (seen.add(hit.kind to hit.key)) {
                mentioned += hit
                perKind[hit.kind] = perKind.getOrElse(hit.kind) { 0 } + 1
            }
        }
        val rank = HashMap<String, Int>()
        for ((i, kind) in kindOrder.withIndex()) rank.putIfAbsent(kind, i)
        fun byKind(hits: List<CompendiumRef>) = hits.sortedBy { rank[it.kind] ?: kindOrder.size }
        return Results(named = byKind(named), mentioned = byKind(mentioned))
    }
}
