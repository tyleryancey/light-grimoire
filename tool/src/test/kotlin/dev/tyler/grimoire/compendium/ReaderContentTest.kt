package dev.tyler.grimoire.compendium

import dev.tyler.grimoire.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Per-kind reader composition against the real bundle. Every pinned string was verified against
 * the 29 Aug 2026 assets first (fireball's real castingTime/range/components, goblin's real stat
 * lines, acolyte's 8/6/6/6 characteristic lists); a regenerated bundle that changes them is
 * supposed to fail here — re-measure, never loosen.
 */
class ReaderContentTest {
    private fun record(kind: Kind, key: String): CompendiumRecord =
        kind.decodeAll(Fixtures.compendium(kind.file)).first { it.key == key }

    private fun doc(kind: Kind, key: String): ReaderDoc = ReaderContent.of(kind, record(kind, key))

    private fun fields(doc: ReaderDoc): List<Block.Field> = doc.blocks.filterIsInstance<Block.Field>()

    private fun plain(spans: List<Span>): String =
        spans.filterIsInstance<Span.Text>().joinToString("") { it.text }

    private fun headings(doc: ReaderDoc, level: Int): List<String> =
        doc.blocks.filterIsInstance<Block.Heading>().filter { it.level == level }.map { plain(it.spans) }

    // ---- spells ----------------------------------------------------------------------------------------------

    @Test
    fun fireballHeadersAndHigherLevelRunIn() {
        val doc = doc(Kind.SPELLS, "fireball")
        assertEquals(
            listOf(
                Block.Field("3rd-level evocation"),
                Block.Field("1 action · 150 feet · V S M"),
                Block.Field("Instantaneous"),
                Block.Field("M: A tiny ball of bat guano and sulfur.", secondary = true),
                Block.Field("Sorcerer, Wizard", secondary = true),
            ),
            fields(doc),
            "the five fireball header lines",
        )
        assertTrue(
            doc.blocks.any { it is Block.Para && it.spans.firstOrNull() == Span.Text("At higher levels. ", bold = true) },
            "higherLevel renders as a bold run-in paragraph",
        )
        assertEquals(emptyList(), doc.links, "fireball names no condition")
    }

    @Test
    fun holdPersonConcentrationDurationAndSeeLink() {
        val doc = doc(Kind.SPELLS, "hold-person")
        assertEquals("Concentration, up to 1 minute", fields(doc)[2].text, "duration decapitalized under the prefix")
        assertEquals(
            listOf(LinkSection("SEE", RefQuery.Keys(Kind.CONDITIONS, listOf("paralyzed")))),
            doc.links,
            "hold-person links to paralyzed",
        )
    }

    @Test
    fun acidSplashIsAConjurationCantrip() {
        assertEquals("Conjuration cantrip", fields(doc(Kind.SPELLS, "acid-splash"))[0].text, "cantrip line")
    }

    // ---- equipment and magic items ---------------------------------------------------------------------------

    @Test
    fun chainMailIsHeaderOnly() {
        val doc = doc(Kind.EQUIPMENT, "chain-mail")
        assertEquals(
            listOf(
                Block.Field("75 gp · 55 lb.", secondary = true),
                Block.Field("Heavy armor · AC 16 · Str 13 · Stealth disadvantage"),
            ),
            doc.blocks,
            "cost/weight plus the armor line, empty body",
        )
        assertEquals(emptyList(), doc.links, "armor has no link sections")
    }

    @Test
    fun longswordWeaponLinesAndPropertiesLink() {
        val doc = doc(Kind.EQUIPMENT, "longsword")
        assertEquals(
            listOf(
                Block.Field("15 gp · 3 lb.", secondary = true),
                Block.Field("Martial melee weapon"),
                Block.Field("1d8 slashing (versatile 1d10)"),
            ),
            doc.blocks,
            "weapon header lines, empty body",
        )
        assertEquals(
            listOf(LinkSection("PROPERTIES", RefQuery.Keys(Kind.WEAPON_PROPERTIES, listOf("versatile")))),
            doc.links,
            "weapon properties link",
        )
    }

    @Test
    fun scaleMailPinsTheDexBonusArmorLine() {
        // The five Medium armors (breastplate, chain-shirt, half-plate, hide, scale-mail) are the
        // bundle's only dexBonus+maxBonus records; scale mail is UI-SPEC's pinned example.
        assertEquals(
            Block.Field("Medium armor · AC 14 + Dex (max 2) · Stealth disadvantage"),
            doc(Kind.EQUIPMENT, "scale-mail").blocks[1],
            "the dex-bonus armor line exactly as UI-SPEC writes it",
        )
        assertTrue(
            fields(doc(Kind.EQUIPMENT, "chain-shirt")).any { it.text == "Medium armor · AC 13 + Dex (max 2)" },
            "no Str or Stealth suffix when the record carries neither",
        )
    }

    @Test
    fun equipmentProseGetsTheConditionScan() {
        assertEquals(
            listOf(LinkSection("SEE", RefQuery.Keys(Kind.CONDITIONS, listOf("prone")))),
            doc(Kind.EQUIPMENT, "ball-bearings-bag-of-1000").links,
            "ball bearings knock creatures prone",
        )
        // "the poisoned weapon" is a participle, not the condition — the whole-word scan links it
        // anyway, exactly as documented. These two are the only equipment hits in the bundle.
        assertEquals(
            listOf(LinkSection("SEE", RefQuery.Keys(Kind.CONDITIONS, listOf("poisoned")))),
            doc(Kind.EQUIPMENT, "poison-basic-vial").links,
            "basic poison mentions poisoned",
        )
    }

    @Test
    fun adamantineArmorHeadlineVerbatimAndNoVariants() {
        val doc = doc(Kind.MAGIC_ITEMS, "adamantine-armor")
        assertEquals(
            Block.Field("Armor (medium or heavy, but not hide), uncommon", secondary = true),
            doc.blocks.first(),
            "the pipeline headline is shown verbatim",
        )
        // adamantine-armor is a base item (isVariant = false) but its variants list is EMPTY in the
        // bundle, so no VARIANTS section is emitted — the query would find nothing.
        assertTrue(doc.links.none { it.label == "VARIANTS" }, "no VARIANTS section for an empty variants list")
        assertTrue(doc.blocks.any { it is Block.Para }, "body prose present")
    }

    // ---- creatures -------------------------------------------------------------------------------------------

    @Test
    fun goblinStatBlock() {
        val doc = doc(Kind.CREATURES, "goblin")
        assertEquals(
            listOf(
                "Small humanoid (goblinoid), neutral evil",
                "AC 15 (armor)",
                "HP 7 (2d6)",
                "Speed 30 ft.",
                "Skills Stealth +6",
                "Senses darkvision 60 ft., passive Perception 9",
                "Languages Common, Goblin",
                "CR 1/4 · 50 XP · Prof +2",
            ),
            fields(doc).map { it.text },
            "goblin header lines in classic SRD order",
        )
        assertTrue(fields(doc).last().secondary, "the CR line is secondary")
        val monos = doc.blocks.filterIsInstance<Block.Mono>()
        assertEquals(2, monos.size, "ability grid lowered to header + one score row")
        assertTrue(monos[0].secondary, "the STR..CHA header row is secondary")
        assertTrue(monos[0].text.startsWith("STR"), "grid header row")
        assertTrue(monos[1].text.startsWith("8 (-1)"), "goblin Str 8 renders as 8 (-1)")
        assertTrue("14 (+2)" in monos[1].text, "goblin Dex 14 renders as 14 (+2)")
        assertTrue(
            doc.blocks.any { it is Block.Para && it.spans.firstOrNull() == Span.Text("Nimble Escape. ", bold = true) },
            "traits render as bold run-ins",
        )
        assertEquals(listOf("Actions"), headings(doc, 3), "one Actions heading, no empty Reactions/Legendary sections")
    }

    @Test
    fun everyCreatureAbilityGridStaysAGrid() {
        // The bundle maximum packed width is exactly GRID_COMPACT_MAX (48): tarrasque, kraken,
        // solar and the three ancient dragons sit right on the boundary with zero margin. Measured
        // 31 Aug 2026; a regenerated bundle that widens any grid (a three-digit score, a format
        // change) fails here — re-measure, never loosen, and revisit the boundary before letting
        // any creature silently fall to stacked mode.
        var widest = 0
        for (record in Kind.CREATURES.decodeAll(Fixtures.compendium("creatures.json"))) {
            val monos = ReaderContent.of(Kind.CREATURES, record).blocks.filterIsInstance<Block.Mono>()
            assertEquals(2, monos.size, "${record.key}: ability grid is header + one score row, never stacked")
            for (line in monos) {
                assertTrue(line.text.length <= TableLayout.GRID_COMPACT_MAX, "${record.key}: grid line fits 48")
            }
            widest = maxOf(widest, monos.maxOf { it.text.length })
        }
        assertEquals(TableLayout.GRID_COMPACT_MAX, widest, "the widest ability grid packs to exactly 48")
    }

    // ---- classes, subclasses, features -----------------------------------------------------------------------

    @Test
    fun barbarianIsHeaderOnlyWithClassLinks() {
        val doc = doc(Kind.CLASSES, "barbarian")
        assertEquals(
            listOf(
                Block.Field("Hit die d12"),
                Block.Field("Saves Str, Con"),
                Block.Field("Light armor, Medium armor, Shields, Simple weapons, Martial weapons", secondary = true),
            ),
            doc.blocks,
            "no spellcasting means no body prose blocks at all",
        )
        assertEquals(
            listOf(
                LinkSection("SUBCLASSES", RefQuery.SubclassesOf("barbarian")),
                LinkSection("FEATURES", RefQuery.ClassFeatures("barbarian")),
            ),
            doc.links,
            "class links",
        )
    }

    @Test
    fun wizardSpellcastingSections() {
        val doc = doc(Kind.CLASSES, "wizard")
        assertTrue(fields(doc).any { it.text == "Spellcasting: Int (from level 1)" }, "spellcasting header line")
        assertEquals(
            listOf(
                "Cantrips", "Spellbook", "Preparing and Casting Spells",
                "Spellcasting Ability", "Ritual Casting", "Spellcasting Focus",
            ),
            headings(doc, 3),
            "each spellcasting info entry becomes a level-3 section",
        )
    }

    @Test
    fun berserkerFlavorLineAndFeaturesLink() {
        val doc = doc(Kind.SUBCLASSES, "berserker")
        assertEquals(Block.Field("Primal Path", secondary = true), doc.blocks.first(), "flavor line")
        assertTrue(
            LinkSection("FEATURES", RefQuery.SubclassFeatures("berserker")) in doc.links,
            "subclass features link",
        )
    }

    @Test
    fun rageCarriesItsClassAndLevel() {
        val doc = doc(Kind.FEATURES, "rage")
        assertEquals("Barbarian 1", fields(doc)[0].text, "classKey titlecased plus level")
        assertTrue(
            LinkSection("CLASS", RefQuery.Keys(Kind.CLASSES, listOf("barbarian"))) in doc.links,
            "feature links back to its class",
        )
    }

    // ---- races -----------------------------------------------------------------------------------------------

    @Test
    fun elfHeaderAndLinks() {
        val doc = doc(Kind.RACES, "elf")
        assertEquals(
            listOf(
                Block.Field("Medium · Speed 30 ft."),
                Block.Field("+2 Dex"),
                Block.Field("Languages: Common, Elvish", secondary = true),
            ),
            fields(doc),
            "race header lines",
        )
        assertEquals(
            listOf(
                LinkSection("TRAITS", RefQuery.Keys(Kind.TRAITS, listOf("darkvision", "fey-ancestry", "trance", "keen-senses"))),
                LinkSection("SUBRACES", RefQuery.Keys(Kind.SUBRACES, listOf("high-elf"))),
            ),
            doc.links,
            "trait and subrace links",
        )
    }

    @Test
    fun lightfootHalflingsIdiomaticProneIsNotALink() {
        // "Lightfoots are more prone to wanderlust" is the bundle's only idiomatic condition word —
        // subraces deliberately skip the see() scan so it never becomes a wrong SEE link.
        assertEquals(
            listOf(
                LinkSection("TRAITS", RefQuery.Keys(Kind.TRAITS, listOf("naturally-stealthy"))),
                LinkSection("RACE", RefQuery.Keys(Kind.RACES, listOf("halfling"))),
            ),
            doc(Kind.SUBRACES, "lightfoot-halfling").links,
            "structural links only, no SEE section",
        )
    }

    // ---- backgrounds and feats -------------------------------------------------------------------------------

    @Test
    fun acolyteFeatureAndSuggestedCharacteristics() {
        val doc = doc(Kind.BACKGROUNDS, "acolyte")
        assertEquals(Block.Field("Skills: Insight, Religion", secondary = true), doc.blocks.first(), "skills line")
        assertEquals(listOf("Shelter of the Faithful", "Suggested Characteristics"), headings(doc, 3), "sections")
        assertEquals(listOf("Personality Traits", "Ideals", "Bonds", "Flaws"), headings(doc, 4), "characteristic groups")
        val numbered = doc.blocks.filterIsInstance<Block.Numbered>()
        assertEquals(26, numbered.size, "8 personality traits + 6 ideals + 6 bonds + 6 flaws")
        assertEquals((1..8).toList(), numbered.take(8).map { it.number }, "numbering restarts per group")
    }

    @Test
    fun grapplerPrerequisiteLine() {
        assertEquals("Prerequisite: Str 13", fields(doc(Kind.FEATS, "grappler"))[0].text, "feat prerequisite")
    }

    // ---- rules -----------------------------------------------------------------------------------------------

    @Test
    fun combatDropsItsLeadingChapterHeading() {
        val doc = doc(Kind.RULES, "combat")
        // combat's whole text IS its chapter heading — dropping it leaves a links-only doc,
        // which is exactly what proves the drop happened.
        assertEquals(emptyList(), doc.blocks, "the leading # Combat is dropped and nothing else follows it")
        assertEquals(listOf(LinkSection("SECTIONS", RefQuery.SectionsOf("combat"))), doc.links, "sections link")

        // A chapter with prose after its heading keeps the prose and only loses the h1.
        val equipment = doc(Kind.RULES, "equipment")
        assertTrue(equipment.blocks.any { it is Block.Para }, "equipment chapter prose present")
        assertEquals(emptyList(), headings(equipment, 1), "its leading # Equipment is dropped")
    }

    @Test
    fun everyRuleSectionDropsTheLeadingHeadingThatRepeatsItsName() {
        // 33 of the 40 sections open "## <Name>" (none open "#"), so the drop must not be level-1-only:
        // otherwise every one of those pages draws its own title again under a top bar that says it.
        // A regenerated bundle that changes this is SUPPOSED to fail here — re-measure, never loosen.
        var withLeadingHeading = 0
        for (record in Kind.RULE_SECTIONS.decodeAll(Fixtures.compendium(Kind.RULE_SECTIONS.file))) {
            val source = Markdown.parse((record as TextRecord).text).firstOrNull()
            if (source is Block.Heading && plain(source.spans) == record.name) withLeadingHeading++
            val head = ReaderContent.of(Kind.RULE_SECTIONS, record).blocks.firstOrNull()
            assertTrue(
                head !is Block.Heading || plain(head.spans) != record.name,
                "${record.key} must not open with a heading repeating its own name",
            )
        }
        assertEquals(33, withLeadingHeading, "sections whose text opens with a heading equal to their name")
    }

    @Test
    fun multiclassingKeepsItsMidTextH1s() {
        val doc = doc(Kind.RULE_SECTIONS, "multiclassing")
        assertEquals(listOf("Proficiency Bonus", "Proficiencies"), headings(doc, 1), "mid-text h1s survive")
        assertTrue(
            LinkSection("CHAPTER", RefQuery.Chapter("multiclassing")) in doc.links,
            "rule section links to its chapter",
        )
    }

    // ---- lookup kinds ----------------------------------------------------------------------------------------

    @Test
    fun lookupKindHeaders() {
        assertEquals("Ability: Dexterity", fields(doc(Kind.SKILLS, "acrobatics"))[0].text, "skill ability line")
        assertEquals(
            listOf(
                Block.Field("Exotic · Script: Infernal"),
                Block.Field("Typical speakers: Demons", secondary = true),
            ),
            doc(Kind.LANGUAGES, "abyssal").blocks,
            "language header, empty body",
        )
        val alignment = doc(Kind.ALIGNMENTS, "chaotic-evil")
        assertEquals(Block.Field("CE", secondary = true), alignment.blocks.first(), "abbreviation line")
        assertTrue(alignment.blocks.any { it is Block.Para }, "alignment body prose present")
        assertEquals(
            listOf(
                Block.Field("Armor"),
                Block.Field("Classes: Fighter, Paladin", secondary = true),
            ),
            doc(Kind.PROFICIENCIES, "all-armor").blocks,
            "a proficiency is header-only — the kind has no prose field",
        )
    }

    @Test
    fun proseOnlyKindsHaveNoHeaderFields() {
        for ((kind, key) in listOf(
            Kind.WEAPON_PROPERTIES to "ammunition",
            Kind.DAMAGE_TYPES to "acid",
            Kind.MAGIC_SCHOOLS to "abjuration",
        )) {
            val doc = doc(kind, key)
            assertTrue(doc.blocks.isNotEmpty(), "${kind.id}/$key: body present")
            assertTrue(doc.blocks.none { it is Block.Field }, "${kind.id}/$key: body only, no header fields")
            assertEquals(emptyList(), doc.links, "${kind.id}/$key: no link sections")
        }
    }

    // ---- sweep -----------------------------------------------------------------------------------------------

    @Test
    fun readerContentComposesEveryBundledRecord() {
        var count = 0
        for (kind in Kind.entries) {
            for (record in kind.decodeAll(Fixtures.compendium(kind.file))) {
                val where = "${kind.id}/${record.key}"
                val doc = ReaderContent.of(kind, record)
                count++
                // Empty prose may leave a doc header/links-only; the invariants are on what IS emitted.
                for (block in doc.blocks) {
                    if (block is Block.Field) assertTrue(block.text.isNotBlank(), "$where: blank header field")
                    if (block is Block.Heading) assertTrue(plain(block.spans).isNotBlank(), "$where: blank heading")
                }
                for (link in doc.links) {
                    assertTrue(link.label.isNotBlank(), "$where: blank link label")
                    val query = link.query
                    if (query is RefQuery.Keys) assertTrue(query.keys.isNotEmpty(), "$where: empty ${link.label} key list")
                }
            }
        }
        assertEquals(1992, count, "every bundled record composes")
    }
}
