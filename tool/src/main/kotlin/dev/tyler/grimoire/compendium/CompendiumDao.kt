package dev.tyler.grimoire.compendium

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * The one DAO of the compendium (plan §DAO). Every query is kind-scoped and finite by the bundle — the
 * compendium is a fixed 1 992 rows, the large kinds take an explicit `limit` — and only the three
 * reader queries ([get], [getAll], [refs]'s siblings) ever select `json` — list queries return the
 * [CompendiumRef] projection so a 27 KB rule section never meets the cursor window. The column list of
 * that projection is spelled out in every query on purpose: Room verifies each string at compile time,
 * and a shared constant would hide which query a verifier error is about.
 *
 * `key` is an SQL keyword and is written in backticks everywhere. The kind literals (`'spells'`,
 * `'features'`, …) are [Kind.id] values; RowsTest pins the columns each kind fills.
 */
@Dao
interface CompendiumDao {
    // ---- import (only the writer seam calls these) ----------------------------------------------------------

    @Insert
    suspend fun insertRecords(rows: List<RecordRow>)

    @Insert
    suspend fun insertSearch(rows: List<SearchRow>)

    @Query("DELETE FROM records")
    suspend fun clearRecords()

    @Query("DELETE FROM search_index")
    suspend fun clearSearch()

    @Query("SELECT COUNT(*) FROM records")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM search_index")
    suspend fun searchCount(): Int

    // ---- kind list -------------------------------------------------------------------------------------------

    @Query("SELECT kind, COUNT(*) AS n FROM records GROUP BY kind ORDER BY kind")
    suspend fun countsByKind(): List<KindCount>

    // ---- filtered lists --------------------------------------------------------------------------------------

    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = :kind ORDER BY sortName LIMIT :limit",
    )
    suspend fun listByName(kind: String, limit: Int): List<CompendiumRef>

    /** Asset order — classes, races, rules, backgrounds, feats, conditions. */
    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = :kind ORDER BY position LIMIT :limit",
    )
    suspend fun listInOrder(kind: String, limit: Int): List<CompendiumRef>

    /** Rule sections of a chapter, subraces of a race, trait variants, feature options. */
    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = :kind AND parentKey = :parentKey ORDER BY position, level, sortName",
    )
    suspend fun children(kind: String, parentKey: String): List<CompendiumRef>

    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = 'subclasses' AND classKey = :classKey ORDER BY sortName",
    )
    suspend fun subclassesOf(classKey: String): List<CompendiumRef>

    /**
     * The rules chapter that owns a rule section — the S10 reader's CHAPTER link. The owner is the derived
     * `parentKey` column [Rows.of] fills from rules.json, which no other query exposes, so the subquery reads
     * it back. Null when the section is unknown or its `parentKey` is null: `key = (NULL)` matches no row.
     */
    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = 'rules' AND `key` = (SELECT parentKey FROM records " +
            "WHERE kind = 'rule_sections' AND `key` = :sectionKey)",
    )
    suspend fun chapterOfSection(sectionKey: String): CompendiumRef?

    /** The S13 spell wheel: one level at a time. */
    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = 'spells' AND level = :level ORDER BY sortName",
    )
    suspend fun spellsByLevel(level: Int): List<CompendiumRef>

    /** A class's list up to a slot level — the M3 prepare list and M4 step 8. */
    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = 'spells' AND level <= :maxLevel AND classList LIKE '% ' || :classKey || ' %' " +
            "ORDER BY level, sortName",
    )
    suspend fun spellsForClass(classKey: String, maxLevel: Int): List<CompendiumRef>

    /** The class's own features up to a level — no subclass features, no options of a parent feature. */
    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = 'features' AND classKey = :classKey AND level <= :maxLevel " +
            "AND subclassKey IS NULL AND parentKey IS NULL ORDER BY level, sortName",
    )
    suspend fun classFeatures(classKey: String, maxLevel: Int): List<CompendiumRef>

    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = 'features' AND subclassKey = :subclassKey ORDER BY level, sortName",
    )
    suspend fun subclassFeatures(subclassKey: String): List<CompendiumRef>

    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = :kind AND category = :category ORDER BY sortName",
    )
    suspend fun byCategory(kind: String, category: String): List<CompendiumRef>

    /** equipment `armor` (13) / `weapon` (37); magic_items `base` hides the 123 variants. */
    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = :kind AND subcategory = :subcategory ORDER BY sortName",
    )
    suspend fun bySubcategory(kind: String, subcategory: String): List<CompendiumRef>

    @Query("SELECT category, COUNT(*) AS n FROM records WHERE kind = :kind GROUP BY category ORDER BY category")
    suspend fun categoriesOf(kind: String): List<CategoryCount>

    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = 'creatures' AND cr BETWEEN :minCr AND :maxCr ORDER BY cr, sortName",
    )
    suspend fun creaturesByCr(minCr: Double, maxCr: Double): List<CompendiumRef>

    // ---- reader (the only queries that return json) ----------------------------------------------------------

    @Query("SELECT * FROM records WHERE kind = :kind AND `key` = :key")
    suspend fun get(kind: String, key: String): RecordRow?

    /** At most [CompendiumReader.MAX_KEYS] keys; table order — the reader restores the caller's. */
    @Query("SELECT * FROM records WHERE kind = :kind AND `key` IN (:keys)")
    suspend fun getAll(kind: String, keys: List<String>): List<RecordRow>

    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind = :kind AND `key` IN (:keys) ORDER BY sortName",
    )
    suspend fun refs(kind: String, keys: List<String>): List<CompendiumRef>

    // ---- search (plan D9: two dumb bounded queries; Search ranks and merges) ----------------------------------

    /** String-prefix hits before word-prefix hits so the `LIMIT` keeps the better candidates. */
    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE name LIKE :prefix || '%' OR name LIKE '% ' || :prefix || '%' " +
            "ORDER BY CASE WHEN name LIKE :prefix || '%' THEN 0 ELSE 1 END, sortName LIMIT :limit",
    )
    suspend fun nameMatches(prefix: String, limit: Int): List<CompendiumRef>

    @Query(
        "SELECT kind, `key`, name, level, school, category, subcategory, rarity, cr, classKey FROM records " +
            "WHERE kind IN (:kinds) AND (name LIKE :prefix || '%' OR name LIKE '% ' || :prefix || '%') " +
            "ORDER BY CASE WHEN name LIKE :prefix || '%' THEN 0 ELSE 1 END, sortName LIMIT :limit",
    )
    suspend fun nameMatchesIn(kinds: List<String>, prefix: String, limit: Int): List<CompendiumRef>

    /** FTS4 `MATCH` over name + body, joined back to `records` for the projection; hits in rowid order. */
    @Query(
        "SELECT r.kind, r.`key`, r.name, r.level, r.school, r.category, r.subcategory, r.rarity, r.cr, r.classKey " +
            "FROM search_index JOIN records r ON r.kind = search_index.kind AND r.`key` = search_index.`key` " +
            "WHERE search_index MATCH :match LIMIT :limit",
    )
    suspend fun textMatches(match: String, limit: Int): List<CompendiumRef>

    @Query(
        "SELECT r.kind, r.`key`, r.name, r.level, r.school, r.category, r.subcategory, r.rarity, r.cr, r.classKey " +
            "FROM search_index JOIN records r ON r.kind = search_index.kind AND r.`key` = search_index.`key` " +
            "WHERE search_index.kind IN (:kinds) AND search_index MATCH :match LIMIT :limit",
    )
    suspend fun textMatchesIn(kinds: List<String>, match: String, limit: Int): List<CompendiumRef>
}
