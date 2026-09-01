package dev.tyler.grimoire.data

import dev.tyler.grimoire.rules.Ac
import dev.tyler.grimoire.rules.Derive
import dev.tyler.grimoire.rules.HitDicePool
import dev.tyler.grimoire.rules.Model
import dev.tyler.grimoire.rules.RulesException
import dev.tyler.grimoire.rules.Tables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * S12 steps 1–2's defaults, over every class the picker can offer.
 *
 * This is the part of the NEW flow M4 keeps when it replaces the two screens either side of it, so the
 * tests are written against what a character must *be* rather than against what today's screens do with
 * it: legal to store, derivable, and carrying the wizard's own stated defaults.
 */
class NewCharacterTest {
    /** The twelve SRD classes, from the engine's own table — the same keys the compendium lists. */
    private val classKeys: List<String> = Tables.HIT_DIE.keys.sorted()

    @Test
    fun theTwelveSrdClassesAreWhatThePickerCanAskFor() {
        assertEquals(12, classKeys.size, "SRD 5.1 ships twelve classes")
    }

    // ---- the defaults ----------------------------------------------------------------------------------------

    @Test
    fun everyClassBuildsALevelOneCharacterWithTheWizardsDefaults() {
        for (key in classKeys) {
            val character = NewCharacter.of("Brother Aldric", key, "id-$key")
            assertEquals("id-$key", character.id, "$key: the id is the caller's")
            assertEquals("Brother Aldric", character.name, "$key: the name is the player's")
            assertEquals(listOf(key to 1), character.classes.map { it.classKey to it.level }, "$key: level 1")
            assertEquals(null, character.classes.single().subclassKey, "$key: no subclass until S12 asks")
            assertEquals(null, character.race, "$key: no race until S12 asks")
            assertEquals(
                listOf(10, 10, 10, 10, 10, 10),
                with(character.abilities) { listOf(str, dex, con, int, wis, cha) },
                "$key: six tens, S12 step 4's default",
            )
            assertEquals(Ac.Manual(NewCharacter.DEFAULT_AC), character.ac, "$key: AC is transcribed, not computed")
            assertEquals(30, character.speed, "$key: the default speed")
            assertEquals(0, character.hp.damage, "$key: undamaged")
            assertEquals(0, character.hp.temp, "$key: no temp HP")
            assertEquals(emptyList(), character.attacks, "$key: nothing transcribed yet")
            assertEquals(emptyList(), character.items, "$key: nothing carried yet")
            assertEquals(null, character.spellcasting, "$key: spells are S12 step 8, which M4 asks")
            assertEquals(emptyList(), character.counters, "$key: class counters are seeded in M4")
        }
    }

    @Test
    fun hitPointsAndHitDiceComeFromTheClassesOwnDie() {
        for (key in classKeys) {
            val die = Tables.HIT_DIE.getValue(key)
            val character = NewCharacter.of("Vessa", key, "id")
            // Level 1 by the average formula is the die's maximum plus the CON modifier, and CON 10 is +0.
            assertEquals(die, character.hp.max, "$key: a level-1 d$die is $die hit points at CON 10")
            assertEquals(
                Derive.hpMaxAverage(character.classes, character.abilities.con),
                character.hp.max,
                "$key: the engine's own formula, never a second copy of it",
            )
            assertEquals(
                listOf(HitDicePool(die = die, total = 1, used = 0)),
                character.hitDice,
                "$key: one unspent d$die",
            )
        }
    }

    // ---- what the rest of the tool needs of it ---------------------------------------------------------------

    @Test
    fun everyBuiltCharacterIsLegalToStore() {
        for (key in classKeys) {
            CharacterLimits.check(NewCharacter.of("Brother Aldric", key, "id-$key"))
        }
    }

    @Test
    fun everyBuiltCharacterDerives() {
        for (key in classKeys) {
            val character = NewCharacter.of("Brother Aldric", key, "id-$key")
            // `Ac.Manual` short-circuits `armorClass`, so an empty armor table is honest here — S1 draws
            // this character with whatever the compendium gives it and this is the half that must not throw.
            val derived = Derive.derive(character, emptyMap())
            assertEquals(1, derived.level, "$key: level 1")
            assertEquals(2, derived.profBonus, "$key: +2 at level 1")
            assertEquals(NewCharacter.DEFAULT_AC, derived.ac, "$key: the transcribed AC")
            assertEquals(character.hp.max, derived.hp.max, "$key: the sheet's own maximum")
        }
    }

    @Test
    fun everyBuiltCharacterRoundTripsThroughTheCodec() {
        for (key in classKeys) {
            val character = NewCharacter.of("Brother Aldric", key, "id-$key")
            assertEquals(character, Model.decode(Model.encode(character)), "$key: stored and read back")
        }
    }

    @Test
    fun theSummaryIsTheClassAndItsLevel() {
        assertEquals(
            "Cleric 1",
            Summaries.summaryOf(NewCharacter.of("Brother Aldric", "cleric", "id")),
            "a character with no race yet has no race half — and no dangling separator",
        )
    }

    // ---- the edges -------------------------------------------------------------------------------------------

    @Test
    fun theNameIsTrimmedAndNothingElse() {
        assertEquals(
            "Vessa Quickfinger",
            NewCharacter.of("  Vessa Quickfinger \n", "rogue", "id").name,
            "surrounding whitespace is a keyboard artefact",
        )
        assertEquals(
            "vessa  quickfinger",
            NewCharacter.of("vessa  quickfinger", "rogue", "id").name,
            "the player's own capitals and spacing are left alone",
        )
    }

    @Test
    fun aBlankNameBuildsButIsRefusedByTheStore() {
        val blank = NewCharacter.of("   ", "cleric", "id")
        assertEquals("", blank.name, "trimmed to nothing")
        val refused = assertFailsWith<RulesException>("the store is what refuses a nameless character") {
            CharacterLimits.check(blank)
        }
        assertEquals("a character needs a name", refused.message, "the sentence a screen shows")
    }

    @Test
    fun aClassTheTablesDoNotKnowIsRefusedWhereEveryOtherIllegalCharacterIs() {
        val refused = assertFailsWith<RulesException>("a custom class has to declare its hit die") {
            NewCharacter.of("Brother Aldric", "blood-hunter", "id")
        }
        assertTrue(
            refused.message.orEmpty().contains("blood-hunter"),
            "the message names the class: ${refused.message}",
        )
    }
}
