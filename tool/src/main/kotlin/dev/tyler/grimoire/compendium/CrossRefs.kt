package dev.tyler.grimoire.compendium

/**
 * Condition cross-reference scanner (plan D10). PURE: kotlin stdlib only. [ReaderContent] scans
 * exactly the prose a record's body renders and turns the hits into one SEE link section, so a
 * player reading hold-person can jump straight to the paralyzed condition.
 *
 * Matching is one precompiled case-insensitive whole-word regex per condition key: "levels of
 * exhaustion" matches exhaustion, while "invisibility" never matches invisible (`\b` plus the
 * differing suffix). Results are ordered by first occurrence in the prose, deduplicated by
 * construction (one hit per key), the record's own key removed when asked, and capped at
 * [MAX_REFS].
 */
object CrossRefs {
    /** The 15 SRD 5.1 condition keys, in conditions.json file order (CrossRefsTest pins the asset). */
    val CONDITION_KEYS: List<String> = listOf(
        "blinded",
        "charmed",
        "deafened",
        "exhaustion",
        "frightened",
        "grappled",
        "incapacitated",
        "invisible",
        "paralyzed",
        "petrified",
        "poisoned",
        "prone",
        "restrained",
        "stunned",
        "unconscious",
    )

    /** Bound on any SEE section; the bundle maximum is mummy-lord's union of 11. */
    const val MAX_REFS = 12

    private val patterns: Map<String, Regex> =
        CONDITION_KEYS.associateWith { key -> Regex("\\b$key\\b", RegexOption.IGNORE_CASE) }

    /** Condition keys mentioned in [prose], by first occurrence; [excludeKey] drops a record's self-match. */
    fun conditions(prose: String, excludeKey: String? = null): List<String> =
        CONDITION_KEYS
            .mapNotNull { key -> patterns.getValue(key).find(prose)?.let { it.range.first to key } }
            .sortedBy { it.first }
            .map { it.second }
            .filterNot { it == excludeKey }
            .take(MAX_REFS)
}
