package dev.tyler.grimoire.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * The one DAO of the character store, shaped like [dev.tyler.grimoire.compendium.CompendiumDao]: every
 * method is `suspend`, and every projection is spelled out in the SQL rather than shared through a
 * constant, because Room verifies each query string at KSP time and a shared constant would hide which
 * query a verifier error belongs to.
 *
 * Finite without a `LIMIT` almost everywhere — the table holds at most [CharacterLimits.MAX_CHARACTERS]
 * rows — and [summaries] takes one anyway so the S0 list can never draw more rows than it budgeted for.
 * Only [json] reads the document column, and it reads the column rather than the row: loading a character
 * needs nothing else, and the denormalised columns would only be a second copy of what decoding gives.
 *
 * Room cannot run in a JVM unit test, so this is an interface and the tests implement it (the pattern
 * `compendium/Fakes.kt` established).
 */
@Dao
interface CharacterDao {
    /** The S0 list, most recently saved first. Never selects `json`. */
    @Query("SELECT id, name, summary, updatedAt FROM characters ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun summaries(limit: Int): List<CharacterSummaryRow>

    /** The stored document, or null when no such character exists. */
    @Query("SELECT json FROM characters WHERE id = :id")
    suspend fun json(id: String): String?

    /** What the repository's `create` checks against [CharacterLimits.MAX_CHARACTERS]. */
    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int

    /** Whether a row is still there — a debounced save must not resurrect a character just deleted. */
    @Query("SELECT EXISTS(SELECT 1 FROM characters WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    /** Insert or replace in one statement: every save writes the whole row, new or not. */
    @Upsert
    suspend fun upsert(row: CharacterRow)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun delete(id: String)
}
