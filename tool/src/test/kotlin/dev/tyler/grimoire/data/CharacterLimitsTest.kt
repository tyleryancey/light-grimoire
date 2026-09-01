package dev.tyler.grimoire.data

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.Ability
import dev.tyler.grimoire.rules.Attack
import dev.tyler.grimoire.rules.AttackGroup
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.ClassEntry
import dev.tyler.grimoire.rules.Item
import dev.tyler.grimoire.rules.Model
import dev.tyler.grimoire.rules.Note
import dev.tyler.grimoire.rules.RulesException
import dev.tyler.grimoire.rules.Spellcasting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Every cap of `pipeline/schema/character.schema.json` that [CharacterLimits] enforces, plus the
 * attunement invariant from docs/PRD.md F10. The exact message is pinned, not just the throw: it is what
 * the wizard puts on screen when it refuses a transcription, and a player who is told "13 attacks (at
 * most 12)" knows what to remove, while "invalid character" tells them nothing.
 */
class CharacterLimitsTest {
    private fun character(ref: String) = Model.decode(Fixtures.character(ref))

    private fun attack(n: Int) = Attack(
        id = "a$n",
        name = "Attack $n",
        group = AttackGroup.ACTION,
        ability = Ability.STR,
        damage = "1d8",
    )

    private fun item(n: Int, attuned: Boolean = false) = Item(id = "i$n", name = "Item $n", qty = 1, attuned = attuned)

    private fun refusal(character: Character): String =
        assertFailsWith<RulesException> { CharacterLimits.check(character) }.message.orEmpty()

    @Test
    fun theThreeFixtureCharactersAreLegal() {
        for (ref in listOf("cleric-5-life", "rogue-3-thief", "paladin-6-warlock-2")) {
            CharacterLimits.check(character(ref))
        }
    }

    @Test
    fun aNameIsRequiredAndBounded() {
        val aldric = character("cleric-5-life")
        assertEquals("a character needs a name", refusal(aldric.copy(name = "")), "empty")
        assertEquals("a character needs a name", refusal(aldric.copy(name = "   ")), "whitespace only")
        assertEquals(
            "name is 41 characters (at most 40)",
            refusal(aldric.copy(name = "A".repeat(41))),
            "one over the schema's maxLength",
        )
        CharacterLimits.check(aldric.copy(name = "A".repeat(40)))
    }

    @Test
    fun aCharacterHasBetweenOneAndThreeClasses() {
        val maelis = character("paladin-6-warlock-2")
        assertEquals(
            "a character needs at least one class",
            refusal(maelis.copy(classes = emptyList())),
            "the schema's minItems",
        )
        val extra = ClassEntry(classKey = "rogue", level = 1)
        CharacterLimits.check(maelis.copy(classes = maelis.classes + extra))
        assertEquals(
            "4 classes (at most 3)",
            refusal(maelis.copy(classes = maelis.classes + extra + ClassEntry(classKey = "bard", level = 1))),
            "one over the schema's maxItems",
        )
    }

    @Test
    fun attacksStopAtTwelve() {
        val vessa = character("rogue-3-thief")
        CharacterLimits.check(vessa.copy(attacks = (1..12).map(::attack)))
        assertEquals("13 attacks (at most 12)", refusal(vessa.copy(attacks = (1..13).map(::attack))), "one over")
    }

    @Test
    fun itemsStopAtSixty() {
        val vessa = character("rogue-3-thief")
        CharacterLimits.check(vessa.copy(items = (1..60).map { item(it) }))
        assertEquals("61 items (at most 60)", refusal(vessa.copy(items = (1..61).map { item(it) })), "one over")
    }

    @Test
    fun notesStopAtTwenty() {
        val aldric = character("cleric-5-life")
        val notes = (1..21).map { Note(title = "Note $it", text = "…") }
        CharacterLimits.check(aldric.copy(notes = notes.take(20)))
        assertEquals("21 notes (at most 20)", refusal(aldric.copy(notes = notes)), "one over")
    }

    /** PRD F10: three attuned items, however many the pack holds. */
    @Test
    fun attunementStopsAtThree() {
        val aldric = character("cleric-5-life")
        val attuned = (1..4).map { item(it, attuned = true) } + item(5)
        CharacterLimits.check(aldric.copy(items = attuned.take(3) + item(9)))
        assertEquals("4 attuned items (at most 3)", refusal(aldric.copy(items = attuned)), "one over")
    }

    @Test
    fun spellSlotsAreExactlyNineLevels() {
        val aldric = character("cleric-5-life")
        val casting = aldric.spellcasting ?: Spellcasting()
        CharacterLimits.check(aldric.copy(spellcasting = casting.copy(slotsUsed = List(9) { 0 })))
        assertEquals(
            "8 spell slot levels (expected 9)",
            refusal(aldric.copy(spellcasting = casting.copy(slotsUsed = List(8) { 0 }))),
            "short",
        )
        assertEquals(
            "10 spell slot levels (expected 9)",
            refusal(aldric.copy(spellcasting = casting.copy(slotsUsed = List(10) { 0 }))),
            "long",
        )
        CharacterLimits.check(character("rogue-3-thief").copy(spellcasting = null))
    }

    /** One "this is not a legal character" signal across the whole tool, so one thing to catch. */
    @Test
    fun everyRefusalIsARulesException() {
        val nameless = character("cleric-5-life").copy(name = "")
        assertEquals(
            "a character needs a name",
            assertFailsWith<RulesException> { CharacterLimits.check(nameless) }.message,
            "the refusal is a RulesException",
        )
        val asArgument = runCatching { CharacterLimits.check(nameless) }.exceptionOrNull() as? IllegalArgumentException
        assertEquals("a character needs a name", asArgument?.message, "and so an IllegalArgumentException as well")
    }

    @Test
    fun theStoreHoldsSixCharacters() {
        assertEquals(6, CharacterLimits.MAX_CHARACTERS, "MAX_CHARACTERS — the repository's create enforces it")
    }
}
