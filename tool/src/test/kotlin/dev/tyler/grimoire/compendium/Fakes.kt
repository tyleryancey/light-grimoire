package dev.tyler.grimoire.compendium

/**
 * Test doubles for the seams the compendium layer is built behind (plan D10). Room itself cannot run on the
 * JVM (no Robolectric on the allow-list), so [FakeCompendiumDao] answers the DAO's queries from the rows
 * [Rows.of] derives, with the SQL each query spells out re-read in Kotlin: `LIKE` is a case-insensitive
 * string/word prefix, FTS `MATCH` is every `*`-suffixed term matching the start of some unicode61 token in
 * name or body, and the FTS table returns hits in insertion (rowid) order. It is a reading of the queries,
 * not the database — the device checks in the plan cover the real thing.
 */
class FakeCompendiumDao(built: List<Rows.Built> = emptyList()) : CompendiumDao {
    val records: MutableList<RecordRow> = built.map { it.record }.toMutableList()
    val search: MutableList<SearchRow> = built.map { it.search }.toMutableList()

    /** Every DAO method called, in order — lets a test prove a query was skipped. */
    val calls: MutableList<String> = ArrayList()

    private fun ref(r: RecordRow) = CompendiumRef(
        kind = r.kind,
        key = r.key,
        name = r.name,
        level = r.level,
        school = r.school,
        category = r.category,
        subcategory = r.subcategory,
        rarity = r.rarity,
        cr = r.cr,
    )

    private fun note(name: String) {
        calls += name
    }

    private fun kind(kind: String): List<RecordRow> = records.filter { it.kind == kind }

    override suspend fun insertRecords(rows: List<RecordRow>) {
        note("insertRecords")
        for (row in rows) check(records.none { it.kind == row.kind && it.key == row.key }) { "duplicate (${row.kind}, ${row.key})" }
        records += rows
    }

    override suspend fun insertSearch(rows: List<SearchRow>) {
        note("insertSearch")
        search += rows
    }

    override suspend fun clearRecords() {
        note("clearRecords")
        records.clear()
    }

    override suspend fun clearSearch() {
        note("clearSearch")
        search.clear()
    }

    override suspend fun count(): Int {
        note("count")
        return records.size
    }

    override suspend fun searchCount(): Int {
        note("searchCount")
        return search.size
    }

    override suspend fun countsByKind(): List<KindCount> {
        note("countsByKind")
        return records.groupingBy { it.kind }.eachCount().entries.sortedBy { it.key }.map { KindCount(it.key, it.value) }
    }

    override suspend fun listByName(kind: String, limit: Int): List<CompendiumRef> {
        note("listByName")
        return kind(kind).sortedBy { it.sortName }.take(limit).map(::ref)
    }

    override suspend fun listInOrder(kind: String, limit: Int): List<CompendiumRef> {
        note("listInOrder")
        return kind(kind).sortedBy { it.position }.take(limit).map(::ref)
    }

    override suspend fun children(kind: String, parentKey: String): List<CompendiumRef> {
        note("children")
        return kind(kind).filter { it.parentKey == parentKey }
            .sortedWith(compareBy<RecordRow> { it.position }.thenBy { it.level ?: Int.MIN_VALUE }.thenBy { it.sortName })
            .map(::ref)
    }

    override suspend fun subclassesOf(classKey: String): List<CompendiumRef> {
        note("subclassesOf")
        return kind("subclasses").filter { it.classKey == classKey }.sortedBy { it.sortName }.map(::ref)
    }

    override suspend fun spellsByLevel(level: Int): List<CompendiumRef> {
        note("spellsByLevel")
        return kind("spells").filter { it.level == level }.sortedBy { it.sortName }.map(::ref)
    }

    override suspend fun spellsForClass(classKey: String, maxLevel: Int): List<CompendiumRef> {
        note("spellsForClass")
        return kind("spells").filter { (it.level ?: 99) <= maxLevel && (it.classList ?: "").contains(" $classKey ") }
            .sortedWith(compareBy<RecordRow> { it.level }.thenBy { it.sortName })
            .map(::ref)
    }

    override suspend fun classFeatures(classKey: String, maxLevel: Int): List<CompendiumRef> {
        note("classFeatures")
        return kind("features")
            .filter { it.classKey == classKey && (it.level ?: 99) <= maxLevel && it.subclassKey == null && it.parentKey == null }
            .sortedWith(compareBy<RecordRow> { it.level }.thenBy { it.sortName })
            .map(::ref)
    }

    override suspend fun subclassFeatures(subclassKey: String): List<CompendiumRef> {
        note("subclassFeatures")
        return kind("features").filter { it.subclassKey == subclassKey }
            .sortedWith(compareBy<RecordRow> { it.level }.thenBy { it.sortName })
            .map(::ref)
    }

    override suspend fun byCategory(kind: String, category: String): List<CompendiumRef> {
        note("byCategory")
        return kind(kind).filter { it.category == category }.sortedBy { it.sortName }.map(::ref)
    }

    override suspend fun bySubcategory(kind: String, subcategory: String): List<CompendiumRef> {
        note("bySubcategory")
        return kind(kind).filter { it.subcategory == subcategory }.sortedBy { it.sortName }.map(::ref)
    }

    override suspend fun categoriesOf(kind: String): List<CategoryCount> {
        note("categoriesOf")
        return kind(kind).groupingBy { it.category }.eachCount().entries
            .sortedWith(compareBy(nullsFirst()) { it.key })
            .map { CategoryCount(it.key, it.value) }
    }

    override suspend fun creaturesByCr(minCr: Double, maxCr: Double): List<CompendiumRef> {
        note("creaturesByCr")
        return kind("creatures").filter { val cr = it.cr; cr != null && cr >= minCr && cr <= maxCr }
            .sortedWith(compareBy<RecordRow> { it.cr }.thenBy { it.sortName })
            .map(::ref)
    }

    override suspend fun get(kind: String, key: String): RecordRow? {
        note("get")
        return records.firstOrNull { it.kind == kind && it.key == key }
    }

    override suspend fun getAll(kind: String, keys: List<String>): List<RecordRow> {
        note("getAll")
        val wanted = keys.toSet()
        return kind(kind).filter { it.key in wanted }
    }

    override suspend fun refs(kind: String, keys: List<String>): List<CompendiumRef> {
        note("refs")
        val wanted = keys.toSet()
        return kind(kind).filter { it.key in wanted }.sortedBy { it.sortName }.map(::ref)
    }

    private fun nameLike(rows: List<RecordRow>, prefix: String, limit: Int): List<CompendiumRef> {
        val p = prefix.lowercase()
        return rows.filter { val n = it.name.lowercase(); n.startsWith(p) || n.contains(" $p") }
            .sortedWith(compareBy<RecordRow> { if (it.name.lowercase().startsWith(p)) 0 else 1 }.thenBy { it.sortName })
            .take(limit)
            .map(::ref)
    }

    override suspend fun nameMatches(prefix: String, limit: Int): List<CompendiumRef> {
        note("nameMatches")
        return nameLike(records, prefix, limit)
    }

    override suspend fun nameMatchesIn(kinds: List<String>, prefix: String, limit: Int): List<CompendiumRef> {
        note("nameMatchesIn")
        return nameLike(records.filter { it.kind in kinds }, prefix, limit)
    }

    private val token = Regex("[\\p{L}\\p{N}']+")

    private fun ftsMatch(rows: List<SearchRow>, match: String, limit: Int): List<CompendiumRef> {
        val terms = match.split(' ').filter { it.isNotEmpty() }.map { it.removeSuffix("*").lowercase() }
        val byKey = records.associateBy { it.kind to it.key }
        return rows.asSequence()
            .filter { row ->
                val tokens = token.findAll(row.name + "\n" + row.body).map { it.value.lowercase() }.toList()
                terms.all { term -> tokens.any { it.startsWith(term) } }
            }
            .mapNotNull { byKey[it.kind to it.key] }
            .take(limit)
            .map(::ref)
            .toList()
    }

    override suspend fun textMatches(match: String, limit: Int): List<CompendiumRef> {
        note("textMatches")
        return ftsMatch(search, match, limit)
    }

    override suspend fun textMatchesIn(kinds: List<String>, match: String, limit: Int): List<CompendiumRef> {
        note("textMatchesIn")
        return ftsMatch(search.filter { it.kind in kinds }, match, limit)
    }
}
