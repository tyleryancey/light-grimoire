package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.compendium.CompendiumRef
import dev.tyler.grimoire.compendium.Kind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The right-detail formatter (docs/UI-SPEC.md S13.1's table, S13.4's disambiguator). Pure, so the edge cases
 * are built by hand; the sweeps run over the real bundle's refs so the strings pinned here are the ones the
 * screens will actually draw.
 */
class RefDetailTest {
    private fun ref(
        kind: String = "creatures",
        name: String = "Goblin",
        level: Int? = null,
        school: String? = null,
        category: String? = null,
        subcategory: String? = null,
        rarity: String? = null,
        cr: Double? = null,
        classKey: String? = null,
    ) = CompendiumRef(
        kind = kind,
        key = "k",
        name = name,
        level = level,
        school = school,
        category = category,
        subcategory = subcategory,
        rarity = rarity,
        cr = cr,
        classKey = classKey,
    )

    // ---- NONE ------------------------------------------------------------------------------------------------

    @Test
    fun noneShowsNothingHoweverFullTheRefIs() {
        val full = ref(school = "evocation", rarity = "Rare", cr = 5.0, classKey = "cleric", level = 3, category = "tools")
        assertNull(RefDetail.of(full, DetailStyle.NONE), "the groups whose rows are already unambiguous draw no detail")
    }

    // ---- KIND ------------------------------------------------------------------------------------------------

    @Test
    fun everyKindHasItsOwnLabel() {
        val labels = Kind.entries.associateWith { RefDetail.label(it) }
        for ((kind, label) in labels) {
            assertTrue(label.isNotBlank(), "${kind.id} has a label")
        }
        assertEquals(Kind.entries.size, labels.values.toSet().size, "all 22 labels are distinct")
    }

    @Test
    fun kindLabelsReadAsTheThingTheRowIs() {
        assertEquals("Spell", RefDetail.of(ref(kind = "spells"), DetailStyle.KIND), "a spell row")
        assertEquals("Magic item", RefDetail.of(ref(kind = "magic_items"), DetailStyle.KIND), "a magic item row")
        assertEquals("Rule", RefDetail.of(ref(kind = "rules"), DetailStyle.KIND), "a rules chapter row")
        assertEquals("Rule section", RefDetail.of(ref(kind = "rule_sections"), DetailStyle.KIND), "a section row")
        assertEquals("Creature", RefDetail.of(ref(kind = "creatures"), DetailStyle.KIND), "a creature row")
        assertEquals("Class feature", RefDetail.of(ref(kind = "features"), DetailStyle.KIND), "a feature row")
        assertEquals("Weapon property", RefDetail.of(ref(kind = "weapon_properties"), DetailStyle.KIND), "a property row")
    }

    @Test
    fun anUnknownKindStringHasNoLabel() {
        assertNull(RefDetail.of(ref(kind = "spelsl"), DetailStyle.KIND), "a kind outside the 22 is simply not labelled")
    }

    // ---- SCHOOL ----------------------------------------------------------------------------------------------

    /** The eight schools the bundle ships, abbreviated as S13.2's wireframe draws them. */
    private val schools = mapOf(
        "abjuration" to "Abj",
        "conjuration" to "Conj",
        "divination" to "Div",
        "enchantment" to "Ench",
        "evocation" to "Evo",
        "illusion" to "Illu",
        "necromancy" to "Nec",
        "transmutation" to "Tran",
    )

    @Test
    fun aSchoolIsAbbreviatedTheWayTheWireframeDrawsIt() {
        // The four the S13.2 wireframe pins by name (docs/UI-SPEC.md S13.2).
        assertEquals("Abj", RefDetail.of(ref(school = "abjuration"), DetailStyle.SCHOOL), "Counterspell's row reads Abj")
        assertEquals("Illu", RefDetail.of(ref(school = "illusion"), DetailStyle.SCHOOL), "Fear's reads Illu")
        assertEquals("Evo", RefDetail.of(ref(school = "evocation"), DetailStyle.SCHOOL), "Fireball's reads Evo")
        assertEquals("Tran", RefDetail.of(ref(school = "transmutation"), DetailStyle.SCHOOL), "Fly's reads Tran")
        assertNull(RefDetail.of(ref(school = null), DetailStyle.SCHOOL), "a row with no school shows nothing")
        assertNull(RefDetail.of(ref(school = "  "), DetailStyle.SCHOOL), "and never an empty string")
    }

    @Test
    fun allEightSchoolsAbbreviateAndNoneRunsPastFourCharacters() {
        for ((slug, abbreviation) in schools) {
            val detail = RefDetail.of(ref(school = slug), DetailStyle.SCHOOL)
            assertEquals(abbreviation, detail, "$slug abbreviates")
            // Four is the widest the wireframe itself draws (Illu, Tran); every character past it is a
            // character `NavRow` takes off the spell name, which is the whole point of abbreviating.
            assertTrue(abbreviation.length <= 4, "$abbreviation stays inside the wireframe's width")
        }
        assertEquals(8, schools.values.toSet().size, "and no two schools collide on one abbreviation")
    }

    @Test
    fun aSchoolOutsideTheEightStillReads() {
        assertEquals(
            "Chronomancy",
            RefDetail.of(ref(school = "chronomancy"), DetailStyle.SCHOOL),
            "an unlisted school falls back to its titlecased slug rather than vanishing",
        )
    }

    @Test
    fun everyBundledSpellHasASchoolToShow() = runBlocking {
        val reader = Bundle.reader()
        val spells = (0..9).flatMap { reader.spellsByLevel(it) }
        assertEquals(319, spells.size, "the whole spell list")
        for (spell in spells) {
            val detail = RefDetail.of(spell, DetailStyle.SCHOOL)
            assertTrue(detail in schools.values, "${spell.name} draws one of the eight abbreviations: $detail")
        }
        val fireball = spells.single { it.key == "fireball" }
        assertEquals("Evo", RefDetail.of(fireball, DetailStyle.SCHOOL), "Fireball is evocation")
        // The fallback above is unreachable from the bundle: every school the assets carry is one of the eight.
        assertEquals(
            schools.keys,
            spells.mapNotNull { it.school }.toSortedSet().toSet(),
            "the bundle ships exactly the eight schools the table names",
        )
    }

    // ---- RARITY ----------------------------------------------------------------------------------------------

    @Test
    fun aRarityIsShownAsTheBundleWritesIt() {
        assertEquals("Very Rare", RefDetail.of(ref(rarity = "Very Rare"), DetailStyle.RARITY), "verbatim, not re-cased")
        assertEquals("Varies", RefDetail.of(ref(rarity = "Varies"), DetailStyle.RARITY), "including the odd one out")
        assertNull(RefDetail.of(ref(rarity = null), DetailStyle.RARITY), "no rarity, no detail")
    }

    // ---- CR --------------------------------------------------------------------------------------------------

    @Test
    fun challengeRatingsReadAsTheBookPrintsThem() {
        assertEquals("1/8", RefDetail.of(ref(cr = 0.125), DetailStyle.CR), "an eighth")
        assertEquals("1/4", RefDetail.of(ref(cr = 0.25), DetailStyle.CR), "a quarter")
        assertEquals("1/2", RefDetail.of(ref(cr = 0.5), DetailStyle.CR), "a half")
        assertEquals("0", RefDetail.of(ref(cr = 0.0), DetailStyle.CR), "zero has no decimal point")
        assertEquals("5", RefDetail.of(ref(cr = 5.0), DetailStyle.CR), "nor does a whole rating")
        assertEquals("21", RefDetail.of(ref(cr = 21.0), DetailStyle.CR), "nor a two-digit one")
        assertEquals("30", RefDetail.of(ref(cr = 30.0), DetailStyle.CR), "nor the Tarrasque's")
        assertNull(RefDetail.of(ref(cr = null), DetailStyle.CR), "a creature-less row shows nothing")
    }

    @Test
    fun everyBundledCreatureFormatsToAFractionOrAWholeNumber() = runBlocking {
        val creatures = Bundle.reader().listByName(Kind.CREATURES, 334)
        assertEquals(334, creatures.size, "the whole bestiary")
        val shaped = Regex("^(0|[1-9][0-9]*|1/8|1/4|1/2)$")
        for (creature in creatures) {
            val detail = RefDetail.of(creature, DetailStyle.CR)
            assertTrue(detail != null && shaped.matches(detail), "${creature.name} reads as a rating, not a decimal: $detail")
        }
        fun detailOf(name: String) = RefDetail.of(creatures.first { it.name == name }, DetailStyle.CR)
        assertEquals("1/8", detailOf("Bandit"), "the Bandit is CR 1/8")
        assertEquals("1/4", detailOf("Acolyte"), "the Acolyte is CR 1/4")
        assertEquals("1/2", detailOf("Ape"), "the Ape is CR 1/2")
        assertEquals("0", detailOf("Badger"), "the Badger is CR 0")
        assertEquals("30", detailOf("Tarrasque"), "the Tarrasque is CR 30")
    }

    // ---- CLASS_LEVEL -----------------------------------------------------------------------------------------

    @Test
    fun aFeatureReadsAsItsClassAndLevel() {
        assertEquals("Barbarian 4", RefDetail.of(ref(classKey = "barbarian", level = 4), DetailStyle.CLASS_LEVEL), "class then level")
        assertEquals(
            "Fighting Style 2",
            RefDetail.of(ref(classKey = "fighting-style", level = 2), DetailStyle.CLASS_LEVEL),
            "a hyphenated key titlecases every word",
        )
        assertEquals("4", RefDetail.of(ref(classKey = null, level = 4), DetailStyle.CLASS_LEVEL), "no class, the level alone")
        assertEquals("Barbarian", RefDetail.of(ref(classKey = "barbarian", level = null), DetailStyle.CLASS_LEVEL), "no level, the class alone")
        assertNull(RefDetail.of(ref(classKey = null, level = null), DetailStyle.CLASS_LEVEL), "neither, nothing")
    }

    @Test
    fun theBundlesRageFeatureReadsBarbarian1() = runBlocking {
        val rage = Bundle.reader().classFeatures("barbarian", 20).single { it.key == "rage" }
        assertEquals("Barbarian 1", RefDetail.of(rage, DetailStyle.CLASS_LEVEL), "the S13.4 disambiguator, from the bundle")
    }


}
