package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The condition scanner against the real bundle: every expected list below was verified against
 * the 29 Aug 2026 assets before being pinned (divine-word really mentions deafened before blinded
 * before stunned; sleep reaches unconscious before charmed). A regenerated bundle that changes any
 * of them is SUPPOSED to fail here — re-measure and update the pins, never loosen them.
 */
class CrossRefsTest {
    private fun spell(key: String): SpellRecord =
        Kind.SPELLS.decodeAll(Fixtures.compendium("spells.json")).first { it.key == key } as SpellRecord

    private fun spellProse(key: String): String = spell(key).let { it.text + "\n" + it.higherLevel }

    @Test
    fun conditionKeysPinTheBundledConditionsFileInOrder() {
        val keys = Kind.CONDITIONS.decodeAll(Fixtures.compendium("conditions.json")).map { it.key }
        assertEquals(keys, CrossRefs.CONDITION_KEYS, "CONDITION_KEYS equals conditions.json in file order")
    }

    @Test
    fun spellsMentioningNoConditionScanEmpty() {
        assertEquals(emptyList(), CrossRefs.conditions(spellProse("fireball")), "fireball names no condition")
        assertEquals(emptyList(), CrossRefs.conditions(spellProse("bestow-curse")), "bestow-curse names no condition")
    }

    @Test
    fun holdPersonFindsParalyzed() {
        assertEquals(listOf("paralyzed"), CrossRefs.conditions(spellProse("hold-person")), "hold-person")
    }

    @Test
    fun sleepOrdersByFirstOccurrence() {
        assertEquals(listOf("unconscious", "charmed"), CrossRefs.conditions(spellProse("sleep")), "sleep")
    }

    @Test
    fun divineWordOrdersByFirstOccurrence() {
        assertEquals(
            listOf("deafened", "blinded", "stunned"),
            CrossRefs.conditions(spellProse("divine-word")),
            "divine-word mentions deafened, then blinded, then stunned",
        )
    }

    @Test
    fun invisibilityDoesNotMatchInvisible() {
        assertEquals(
            emptyList(),
            CrossRefs.conditions("The greater invisibility spell hides the target."),
            "the word boundary keeps invisibility from matching invisible",
        )
    }

    @Test
    fun levelsOfExhaustionMatchesExhaustion() {
        assertEquals(
            listOf("exhaustion"),
            CrossRefs.conditions("The target gains two levels of exhaustion."),
            "exhaustion is matched inside the levels-of-exhaustion phrasing",
        )
    }

    @Test
    fun aConditionsOwnTextExcludesItself() {
        val paralyzed = Kind.CONDITIONS.decodeAll(Fixtures.compendium("conditions.json"))
            .first { it.key == "paralyzed" } as TextRecord
        assertEquals(
            listOf("incapacitated"),
            CrossRefs.conditions(paralyzed.text, excludeKey = "paralyzed"),
            "the paralyzed condition links only to incapacitated",
        )
        assertTrue(
            "paralyzed" in CrossRefs.conditions(paralyzed.text),
            "without the exclusion the self-match is present",
        )
    }

    @Test
    fun mummyLordsUnionIsElevenDistinctConditions() {
        val mummyLord = Kind.CREATURES.decodeAll(Fixtures.compendium("creatures.json"))
            .first { it.key == "mummy-lord" }
        val see = ReaderContent.of(Kind.CREATURES, mummyLord).links.single { it.label == "SEE" }.query
        val keys = (see as RefQuery.Keys).keys
        assertEquals(
            listOf(
                "frightened", "paralyzed", "blinded", "stunned", "grappled", "petrified",
                "prone", "restrained", "charmed", "exhaustion", "poisoned",
            ),
            keys,
            "prose hits by first occurrence, then the typed immunities not already present",
        )
        assertEquals(keys.distinct(), keys, "the union is deduplicated")
        assertTrue(keys.size <= CrossRefs.MAX_REFS, "11 distinct conditions stay within MAX_REFS")
    }

    @Test
    fun noRecordInTheBundleExceedsMaxRefs() {
        for (kind in Kind.entries) {
            for (record in kind.decodeAll(Fixtures.compendium(kind.file))) {
                for (link in ReaderContent.of(kind, record).links) {
                    val query = link.query
                    if (link.label != "SEE" || query !is RefQuery.Keys) continue
                    val where = "${kind.id}/${record.key}"
                    assertTrue(query.keys.size <= CrossRefs.MAX_REFS, "$where: ${query.keys.size} SEE refs")
                    assertEquals(query.keys.distinct(), query.keys, "$where: SEE keys deduplicated")
                    assertTrue(query.keys.all { it in CrossRefs.CONDITION_KEYS }, "$where: SEE keys are condition keys")
                }
            }
        }
    }
}
