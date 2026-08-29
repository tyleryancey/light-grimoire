# 03 — Corpus & licensing: what a Light Phone 5E tool may bundle, from where, and how

Research date: 29 August 2026. Scope: an open-source, non-commercial, fully offline Light Phone 3 tool
(Kotlin, public GitHub repo, built and signed by Light from the public commit, reviewed for the Tool
Library). Every license claim below was checked at the primary source — the SRD PDFs' own legal pages,
WotC's SRD page and FAQ, the CC-BY-4.0 legal code, `LICENSE` files fetched raw from each repository,
Paizo's ORC text, and publishers' own announcement pages. Every dataset size is a measurement made
today on downloaded files, not a repo-page estimate. Where a claim could not be verified it is marked
**[unverified]**.

Platform facts (asset allow-list, dependency allow-list, `readAsset`, `buildDatabase`) were read from the
local `light-sdk` checkout at `/home/claude/dnd-companion/light-sdk-src` (commit `3df3c24`, 2026-08-25);
see `04-light-sdk-state.md` §D.5 for the fuller treatment.

---

## Executive summary

1. **Bundle SRD 5.2.1 and/or SRD 5.1. Both are CC-BY-4.0, irrevocable, explicitly usable in software/VTTs,
   and may be combined in one product.** WotC's FAQ: "Can I use both SRD 5.1 and SRD 5.2 in the same
   product? Yes." The only obligation is a fixed attribution sentence per SRD (exact text in §3.6).
2. **The attribution text is prescribed and closed.** WotC: "Please do not include any other attribution
   to Wizards or its parent or affiliates other than that provided above. You may, however, include a
   statement on your work indicating that it is 'compatible with fifth edition' or '5E compatible.'"
   That is the entire permitted branding vocabulary. Do not put "D&D", "Dungeons & Dragons" or "Dungeon
   Master" in the tool's name, label, description or UI chrome; the SRD 5.2 text itself switched to
   "Game Master".
3. **No open dataset is a complete, structured SRD 5.2.1 today.** Open5e v2 has 339 spells / 331
   creatures / all classes for `srd-2024` but **no magic items**; 5e-bits' 2024 set has magic items
   (from 5.2, missing the 15 items restored in 5.2.1) but **no spells and 3 monsters**; the two complete
   5.2.1 sources are Markdown (downfallx, single-commit, unaudited) and Foundry's compendia (JSON, but with
   non-redistributable token art beside it). Expect to merge two or three sources and reconcile against
   the PDF.
4. **SRD 5.1 is fully covered** by Open5e v1 (`wotc-srd`, 2.24 MB JSON incl. 237 magic items) and
   5e-bits 2014 (3.87 MB JSON incl. 362 item rows with variants).
5. **Never ingest 5e.tools data.** Its code is MIT, but its contribution rule is "Only 'official' (that is,
   published by WotC) data is to be included" — i.e. verbatim PHB/MM/DMG text; WotC DMCA'd two mirrors
   on 7 Aug 2024. The same applies to the FightClub5eXML `Sources/` files (PHB p.32 text) and to any
   D&D Beyond export other than a player's own character.
6. **Sizes are small.** SRD 5.2.1 core content is ~3.5 MB of pretty-printed JSON, ~0.3 MB gzipped;
   a prototype SQLite with FTS4 index of spells+creatures+items+features+rules is ~3.2 MB (1.5 MB without
   FTS). Both SRDs together fit in well under 10 MB of assets.
7. **Light's builder forbids `.db`/`.sqlite`/`.gz`/`.zip` assets and caps files at 5 MiB**, so ship
   `.json` (or `.bin`) chunks committed under `tool/src/main/assets/` and import into Room on first launch
   — or open a prebuilt SQLite shipped as `.bin` via `android.database.sqlite` (not blocked). Generation
   must happen on the desktop and be committed; Light's server only compiles `tool/`.
8. **Third-party open content exists but complicates licensing.** Kobold Press's Black Flag Reference
   Document and EN Publishing's A5E SRD are CC-BY-4.0 (also ORC); Sly Flourish's Lazy GM's Resource
   Document is CC-BY-4.0. Everything Kobold published under OGL 1.0a (Tome of Beasts 1–3, Creature Codex,
   Deep Magic…) would drag the OGL's full-text and §7 no-compatibility-claim rules into the APK. ORC-only
   content cannot be re-licensed into a CC-BY dataset. Recommend SRD-only for v1.
9. **Red flags:** (a) Foundry's `tokens/` art is "may not be redistributed"; (b) 5e-bits' README still says
   the underlying material is OGL — it is not wrong but is stale; (c) Open5e's `a5e-mm` document is tagged
   OGL-only although EN Publishing's own A5ESRD page licenses the same content CC-BY — source A5E from
   a5esrd.com, not from Open5e; (d) SRD text contains a handful of literal "D&D" mentions — reproducing
   them inside quoted SRD text is licensed, using them in branding is not.
10. **Nothing new from WotC in 2026:** the SRD page (updated 2 Mar 2026) lists 5.2.1 (1 May 2025) as
    current, no 5.2.2/5.3; localized 5.2.1 (DE/ES/FR/IT) shipped 8 Dec 2025; Sigil closes at end of
    October 2026; D&D Beyond still has no supported JSON export (moderator, Jan 2026: "Just PDF").

---

## Part 1 — Wizards of the Coast System Reference Documents

### 1.1 SRD 5.1 (2014 rules)

**License.** Released under CC-BY-4.0 on 27 January 2023 ([WotC, "OGL 1.0a & Creative Commons"](https://www.dndbeyond.com/posts/1439-ogl-1-0a-creative-commons)):
"We are also making the entire SRD 5.1 available under a Creative Commons license … We don't control that
license and cannot alter or revoke it. It's open and irrevocable in a way that doesn't require you to take
our word for it." The CC PDF is [SRD_CC_v5.1.pdf](https://media.wizards.com/2023/downloads/dnd/SRD_CC_v5.1.pdf)
(403 pages). An OGL 1.0a edition of the same document remains available on the SRD page.

**Required attribution — verbatim from the PDF's Legal Information page:**

> This work includes material taken from the System Reference Document 5.1 ("SRD 5.1") by Wizards of the
> Coast LLC and available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is
> licensed under the Creative Commons Attribution 4.0 International License available at
> https://creativecommons.org/licenses/by/4.0/legalcode.

followed by: "Please do not include any other attribution regarding Wizards other than that provided above.
You may, however, include a statement on your work that it is 'compatible with fifth edition' or '5E
compatible.'" and "Section 5 of CC-BY-4.0 includes a Disclaimer of Warranties and Limitation of Liability
that limits our liability to you."

**Contents (counts measured from the two structured datasets, which agree unless noted):**

| Category | Count | Notes |
|---|---|---|
| Classes / subclasses | 12 / 12 | one subclass each: Berserker, Lore, Life, Land, Champion, Open Hand, Devotion, Hunter, Thief, Draconic Bloodline, The Fiend, Evocation |
| Races / subraces | 9 / 4 | Dwarf (Hill), Elf (High), Halfling (Lightfoot), Human, Dragonborn, Gnome (Rock), Half-Elf, Half-Orc, Tiefling |
| Backgrounds | 1 | Acolyte |
| Feats | 1 | Grappler |
| Spells | 319 | Open5e v1/v2 and 5e-bits agree |
| Monsters | 322–334 | Open5e v1 322, Open5e v2 325, 5e-bits 334 — the spread is how NPC-appendix entries and variant forms are split |
| Magic items | 239 base (362 rows with +1/+2/+3 and variant expansions) | Open5e v1 stores 237 base items |
| Conditions | 15 | |
| Rules text | 45 sections (Open5e) / 33 rule-sections + 6 top-level rules (5e-bits) | ~326 KB of prose |

**Notable omissions vs. the 2014 PHB** (WotC's own FAQ: "The goal of the SRD is to allow users to create
new content, not to replicate the text of the whole game"; inclusion criteria were content that "(1) was
in the 3E SRD, (2) has an equivalent in fifth edition D&D, and (3) is vital to how a class, magic item, or
monster works"): only one subclass per class, one background, one feat; named spells appear under
generic names (Acid Arrow, Arcane Hand, Tiny Hut, Hideous Laughter, Black Tentacles…); Product-Identity
monsters (beholder, mind flayer, displacer beast, yuan-ti, githyanki, slaad, umber hulk, carrion crawler,
kuo-toa) are absent; most magic items and all setting material are absent.

### 1.2 SRD 5.2 and 5.2.1 (2024 rules)

**Dates.** SRD 5.2 published 22 April 2025; SRD 5.2.1 published 1 May 2025; localized 5.2.1 in German,
Spanish, French and Italian published 8 December 2025 ([dndbeyond.com/srd](https://www.dndbeyond.com/srd),
page last updated 2 March 2026). PDF: [SRD_CC_v5.2.1.pdf](https://media.dndbeyond.com/compendium-images/srd/5.2/SRD_CC_v5.2.1.pdf)
(reported as 344 pages by the fetch; the table of contents runs Playing the Game p.5 … Magic Items p.204,
Monsters p.254). A conversion guide, [converting-to-srd-5.2.1.pdf](https://media.dndbeyond.com/compendium-images/srd/guide/converting-to-srd-5.2.1.pdf),
tags every change [New Rule] / [Revised Rule] / [New Name].

**License.** CC-BY-4.0 only — the 5.2 line has no OGL edition. WotC's FAQ: "Can SRD 5.2 ever be revoked
or changed? No. Once a document is published under the Creative Commons Attribution 4.0 International
License (CC-BY-4.0), it is permanently available under those terms." Also: "SRD 5.2 can be used
commercially under Creative Commons, including for crowdfunding and ongoing support platforms."

**Required attribution — verbatim from the PDF's Legal Information page:**

> This work includes material from the System Reference Document 5.2.1 ("SRD 5.2.1") by Wizards of the
> Coast LLC, available at https://www.dndbeyond.com/srd. The SRD 5.2.1 is licensed under the Creative
> Commons Attribution 4.0 International License, available at
> https://creativecommons.org/licenses/by/4.0/legalcode.

The surrounding text reads: "The System Reference Document 5.2.1 ("SRD 5.2.1") is provided to you free of
charge by Wizards of the Coast LLC ("Wizards") under the terms of the Creative Commons Attribution 4.0
International License ("CC-BY-4.0"). You are free to use the content in this document in any manner
permitted under CC-BY-4.0, provided that you include the following attribution statement in any of your
work: […] Please do not include any other attribution to Wizards or its parent or affiliates other than
that provided above. You may, however, include a statement on your work indicating that it is "compatible
with fifth edition" or "5E compatible." Section 5 of CC-BY-4.0 includes a Disclaimer of Warranties and
Limitation of Liability that limits our liability to you."

**The 5.2 → 5.2.1 errata story.** Nine days after release WotC announced: "SRD 5.2.1 is now live! It
includes 15 magic items mistakenly left out of 5.2, plus other small corrections"
([D&D Beyond on X](https://x.com/DnDBeyond/status/1917972286771994835)). The
[Community Update](https://www.dndbeyond.com/community-update) changelog lists the 15 items (Bead of
Nourishment, Cloak of Invisibility, Elixir of Health, Energy Bow, Gloves of Thievery, Hat of Many Spells,
Potion of Invulnerability, Potion of Longevity, Potion of Vitality, Quarterstaff of the Acrobat, Rod of
Resurrection, Sending Stones, Sentinel Shield, Shield of the Cavalier, Thunderous Greatclub) plus:
"Replaced duplicate Iron Golem stat block with the Knight stat block", "Added Octopus stat block",
"Updated page numbers", "Added blank line to legal text for readability". Community reporting of the gap:
[EN World, 29 Apr 2025](https://www.enworld.org/threads/the-5-2-srd-pdf-has-new-content-over-5-1-but-the-15-newly-added-magic-items-are-all-missing.713128/).
WotC's stated policy going forward: "When changes are made to SRD 5.2 its version number will be updated,
and SRD 5.2.1 will be published as a separate document under Creative Commons" — so **pin the version
string** ("5.2.1") in both data and attribution, and expect 5.2.2 someday.

**Why this matters for datasets:** every structured 2024 dataset checked today was built from 5.2, not
5.2.1 — 5e-bits' 2024 magic items (262 rows) lack all 15 restored items; Open5e's `srd-2024` has the
Octopus and Knight but no magic items at all; Sly Flourish's 5.2 Markdown lacks both. Only the downfallx
Markdown and Foundry's `SRD 5.2` packs [Foundry unverified item-by-item] include them.

**Contents of 5.2.1 (measured from Open5e `srd-2024`, 5e-bits `2024`, and the 5.2.1 Markdown):**

| Category | Count | Detail |
|---|---|---|
| Classes / subclasses | 12 / 12 | Path of the Berserker, College of Lore, Life Domain, Circle of the Land, Champion, Warrior of the Open Hand, Oath of Devotion, Hunter, Thief, Draconic Sorcery, Fiend Patron, Evoker |
| Species | 9 | Dragonborn, Dwarf, Elf, Gnome, Goliath, Halfling, Human, Orc, Tiefling (Half-Elf and Half-Orc gone; Aasimar excluded) |
| Backgrounds | 4 | Acolyte, Criminal, Sage, Soldier — each with its Origin feat |
| Feats | 17 | Origin: Alert, Magic Initiate, Savage Attacker, Skilled · General: Ability Score Improvement, Grappler · Fighting Style: Archery, Defense, Great Weapon Fighting, Two-Weapon Fighting · Epic Boons: Combat Prowess, Dimensional Travel, Fate, Irresistible Offense, Spell Recall, the Night Spirit, Truesight |
| Spells | 339 | 20 new vs 5.1 (Charm Monster, Chromatic Orb, Dissonant Whispers, Divine Smite, Dragon's Breath, Elementalism, Ensnaring Strike, Hex, Ice Knife, Mind Spike, Phantasmal Force, Power Word Heal, Ray of Sickness, Searing Smite, Sorcerous Burst, Starry Wisp, Summon Dragon, Tsunami, Vitriolic Sphere…) |
| Creature stat blocks | ~331 | 17 new (Allosaurus, Ankylosaurus, Archelon, Bugbear Stalker, Goblin Boss, Goblin Minion, Guard Captain, Hippopotamus, Hobgoblin Captain, Pirate, Pirate Captain, Pteranodon, Sphinx of Wonder, Swarm of Crawling Claws, Tough Boss, Troll Limb, Vampire Familiar) plus 5.2.1's Octopus and Knight; Duergar, Drow and Lizardfolk dropped (guide suggests Spy / Priest Acolyte / Scout) |
| Magic items | ≈258 | 243 base items in 5.2 (5e-bits count) + 15 restored in 5.2.1; renamed: Deck of Many Things → Mysterious Deck, Orb of Dragonkind → Dragon Orb |
| Weapon Mastery properties | 8 | Cleave, Graze, Nick, Push, Sap, Slow, Topple, Vex |
| Conditions | 15 | |
| Rules | Playing the Game, Combat, Character Creation, Spellcasting, Rules Glossary, Gameplay Toolbox | Rules Glossary is new; Planes/cosmology and "Between Adventures" sections are gone |

**What is new vs 5.1** ([Tribality](https://www.tribality.com/2025/04/23/dd-system-reference-document-v5-2/),
[EN World](https://www.enworld.org/threads/dungeons-dragons-srd-5-2-is-officially-live.713038/), conversion
guide): 2024 class designs and weapon mastery; species replace races with Goliath and Orc added; four
backgrounds with origin feats; 16 new feats; the Rules Glossary; exploration/"Rhythm of Play" text; renamed
terms (Race → Species, Hit Dice → Hit Point Dice, Inspiration → Heroic Inspiration, Armor proficiency →
Armor training, Madness → Fear and Mental Stress, School of Evocation → Evoker, Way of the Open Hand →
Warrior of the Open Hand, The Fiend → Fiend Patron, drow poison → Spider's Sting); "Dungeon Master" → "Game
Master" throughout; Strahd, Orcus and other named IP removed.

**What remains excluded** (WotC FAQ): "some classes (such as the Artificer), species (like Aasimar), and
monsters (including the Beholder) have been excluded" on grounds of "brand identity protection, licensing
strategy, and intellectual property rights." Only 12 of the PHB's 48 subclasses and 17 of its 75 feats;
12 of 16 backgrounds absent; ~50 PHB spells absent.

### 1.3 What CC-BY-4.0 actually requires (legal code, §3(a))

Per the [CC-BY-4.0 legal code](https://creativecommons.org/licenses/by/4.0/legalcode): retain the creator
identification, copyright notice, license notice, disclaimer-of-warranties notice and a URI to the
material "if supplied by the Licensor"; **"indicate if You modified the Licensed Material and retain an
indication of any previous modifications"**; include the license text or a link to it; satisfy all of it
"in any reasonable manner based on the medium, means, and context" (§3(a)(2)). §2(a)(6): "Nothing in this
Public License constitutes or may be construed as permission to assert or imply that You are, or that Your
use of the Licensed Material is, connected with, or sponsored, endorsed, or granted official status by, the
Licensor." §2(b)(2): patent and trademark rights are not licensed. §4 covers sui generis database rights,
which matters because the bundle is literally a database.

Practical consequence: because the pipeline restructures SRD prose into fields and may trim or reflow it,
the attribution block must carry a modification notice (§3.6 includes one). Rendering the attribution on
an About screen reachable from the tool's menu, plus the repo `README`/`ATTRIBUTION.md`, is a "reasonable
manner" for a phone app.

### 1.4 Trademarks: what the tool may call itself

Three sources bear on names:

- **The SRD legal page** (both versions): the only permitted extra statement is "compatible with fifth
  edition" or "5E compatible", and "Please do not include any other attribution to Wizards or its parent or
  affiliates other than that provided above."
- **CC-BY-4.0 §2(b)(2)** withholds trademark rights, and **§2(a)(6)** forbids implying endorsement.
- **The Wizards Fan Content Policy** ([company.wizards.com](https://company.wizards.com/en/legal/fancontentpolicy))
  governs non-SRD fan use and is instructive even though the tool won't rely on it: "Don't use Wizards'
  logos and trademarks" / "You may not incorporate any Wizards of the Coast logos and trademarks in your Fan
  Content without our prior, written consent." It also requires free distribution and its own disclaimer
  text — none of which should be copied into an SRD-only tool, because WotC has asked SRD users not to add
  "any other attribution regarding Wizards".

The SRD text itself contains a few literal "D&D"s ("The three main pillars of D&D play are social
interaction, exploration, and combat") — noted publicly on
[EN World](https://www.enworld.org/threads/some-terms-removed-from-2024-srd-but-is-d-d-really-supposed-to-be-in-there.713077/).
Reproducing them inside quoted rules text is a copyright use CC-BY permits; lifting them into the tool's
identity is a trademark use CC-BY does not touch. WotC's own renames (Game Master, Mysterious Deck, Dragon
Orb, Spider's Sting) are a clear signal of what they consider protected.

**Recommended posture**

| Where | Do | Don't |
|---|---|---|
| `lighttool.toml` label / Tool Library name | a plain noun: "Compendium", "Grimoire", "Spellbook", "Bestiary" (optionally "5E Compendium") | "D&D …", "Dungeons & Dragons …", "DM Screen", "DnD", "PHB", "Monster Manual" |
| Description / README first line | "A 5E-compatible offline rules reference built from the System Reference Documents 5.1 and 5.2.1." | "the D&D rules", "official", any WotC logo, the ampersand dragon, red/black trade dress |
| UI labels | "Game Master"/"GM", "species", "Hit Point Dice" (5.2 terms) | "Dungeon Master"/"DM" as a product term, "Forgotten Realms" |
| About screen | the two attribution sentences + "5E compatible" | a "©/™ Wizards" trademark line (WotC explicitly asks for no additional attribution) |
| Bundled content | SRD text only; homebrew samples with original names | any monster/spell/item not in an SRD, any WotC artwork or Fan Site Kit assets |

"SRD" and "System Reference Document" are descriptive terms used freely by Open5e, 5e-bits ("5e SRD API"),
Foundry and Roll20; using them is low-risk.

### 1.5 The OGL 1.0a episode and why CC-BY is the basis

Timeline ([Wikipedia summary](https://en.wikipedia.org/wiki/Open_Game_License), primary WotC post above):
5 Jan 2023 a leaked OGL 1.1 draft (royalties above $750k, revenue reporting, deauthorization of 1.0a)
was reported by Gizmodo/io9; the #OpenDnD petition passed 66,000 signatures and a D&D Beyond
cancellation campaign followed; 12 Jan Paizo announced the ORC license; 13 Jan and 18–19 Jan WotC
published walk-backs and an OGL 1.2 draft; on 27 Jan WotC reversed entirely: "We are leaving OGL 1.0a in
place, as is. Untouched," and released SRD 5.1 under CC-BY-4.0. WotC's FAQ today: "The OGL places more
requirements on creators and contains more restrictions on what they are permitted to do. Creative Commons
provides a more modern license, more freedom for creators, and more certainty that the content released
under the license will remain available under those terms forever."

For a software bundle CC-BY is strictly better than OGL 1.0a: no requirement to embed the full license
text and Section 15 chain, no "Product Identity" carve-outs to police, no §7 ban on compatibility claims,
no revocation controversy, and the 2024 rules are only available this way.

### 1.6 2025–2026 developments checked

- **No SRD after 5.2.1.** The SRD page (updated 2 Mar 2026) lists 5.2.1 as current; searches for
  "SRD 5.2.2"/"5.3" return nothing.
- **Localized SRD 5.2.1** (DE/ES/FR/IT, 8 Dec 2025) — a future i18n option under the same license.
- **No machine-readable SRD from WotC.** A D&D Beyond thread requesting JSON/CSV ran May 2025 → March 2026
  with no official response ([thread](https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/221238-srd-5-2-1-data-in-technically-usable-formats)).
- **Sigil** (WotC's 3D VTT) was sunset on 24 Oct 2025; "At the end of October, 2026, Sigil's servers will
  shut down" ([WotC](https://www.dndbeyond.com/posts/2086-closing-the-chapter-on-sigil-and-thanking-the)).
  No effect on licensing.
- **D&D Beyond export policy** unchanged: "D&D Beyond does not officially support exporting to roll20, nor
  exporting to json at all. Just PDF" (moderator, 28–29 Jan 2026,
  [thread](https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/235583-exporting-sheet-to-roll20)).
- **D&D Beyond's free "Basic Rules" are not licensed**: "The Basic Rules cannot be used in content creation."
- **DMCA against 5etools mirrors** (7 Aug 2024,
  [github/dmca](https://github.com/github/dmca/blob/master/2024/08/2024-08-07-wizards-of-the-coast.md))
  remains the clearest statement of what WotC enforces: "content copied verbatim from Wizards' D&D
  publications, including the core rulebooks, supplements, and campaign settings."

---

## Part 2 — Machine-readable sources

### 2.1 Open5e (`open5e/open5e-api`, api.open5e.com)

**Code license.** [`LICENSE.md`](https://raw.githubusercontent.com/open5e/open5e-api/main/LICENSE.md) is a
"Modified MIT License … excepting artistic images included in this repository and SRD content provided by
3rd-parties"; images are CC-BY-NC-4.0; "This software makes no claims to license of included SRD and OGL
content". **Content license is per document** — the v2 `/documents/` endpoint carries a `licenses` array
per source, resolving to `cc-by-40`, `ogl-10a` or `cc0` (`/v2/licenses/`).

**Documents present on 29 Aug 2026 (24, from `/v2/documents/`):**

| key | Source | Publisher | License(s) | System |
|---|---|---|---|---|
| `srd-2014` | System Reference Document 5.1 | WotC | CC-BY-4.0, OGL-1.0a | 5e 2014 |
| `srd-2024` | System Reference Document 5.2 | WotC | CC-BY-4.0 | 5e 2024 |
| `bfrd` | Black Flag SRD | Kobold Press | CC-BY-4.0 | 5e 2014 |
| `a5e-ag`, `a5e-ddg`, `a5e-gpg` | Adventurer's Guide, Dungeon Delver's Guide, Gate Pass Gazette | EN Publishing | CC-BY-4.0, OGL-1.0a | A5E |
| `a5e-mm` | Monstrous Menagerie | EN Publishing | **OGL-1.0a only** (see red flag §2.6) | A5E |
| `tob`, `tob-2023`, `tob2`, `tob3`, `ccdx`, `deepm`, `deepmx`, `toh`, `vom`, `wz`, `kp` | Tome of Beasts 1/1-2023/2/3, Creature Codex, Deep Magic, Deep Magic Extended, Tome of Heroes, Vault of Magic, Warlock Zine, KP Compilation | Kobold Press | OGL-1.0a | 5e 2014 |
| `tdcs` | Tal'dorei Campaign Setting | Green Ronin | OGL-1.0a | 5e 2014 |
| `spells-that-dont-suck` | Spells That Don't Suck | Somanyrobots | CC-BY-4.0 | 5e 2014 |
| `open5e`, `open5e-2024` | Open5e Originals | Open5e | OGL-1.0a | both |
| `elderberry-inn-icons` | icon set | Open5e | CC0 | — |
| `core` | 5e Core Concepts | Open5e | CC-BY-4.0, OGL-1.0a | 5e 2014 |

**API.** v1 is in maintenance ("will not receive content or functionality updates"; calls need the `/v1/`
prefix); v2 is primary. v1 filters by `document__slug=wotc-srd`; v2 by `document__key=srd-2024`. v2 exposes
richer, typed models (`Spell` with `casting_options`, `Creature` with separate `actions`/`traits`/`attacks`,
`CharacterClass` + `ClassFeature` + `ClassFeatureItem` with `data_for_class_table`), but v2's `Item` model
holds **mundane equipment only** — `/v2/items/` returns 440 rows (203 `srd-2024` + 237 `srd-2014`), every
one with `rarity: null`; magic items exist only in v1 (`/v1/magicitems/`, 237 SRD 5.1 rows).

**Data repo layout** (`data/` in the repo; Django fixture JSON `{"model","pk","fields"}` per record):

- `data/v1/<document-slug>/{Document,Spell,Monster,MonsterSpell,MagicItem,CharClass,Archetype,Race,Subrace,Background,Feat,Condition,Section,Weapon,Armor,Plane,SpellList}.json`
  — for `wotc-srd`: 319 spells, 322 monsters, 237 magic items, 12 classes, 12 archetypes, 9 races,
  4 subraces, 45 sections, 15 conditions; **2.24 MB total, 0.43 MB gzipped**.
- `data/v2/<publisher>/<document-key>/{Document,Spell,SpellCastingOption,Creature,CreatureAction,CreatureActionAttack,CreatureTrait,CharacterClass,ClassFeature,ClassFeatureItem,Species,SpeciesTrait,Background,BackgroundBenefit,Feat,Item,Weapon,WeaponProperty,Armor,Rule,RuleSet,…}.json`
  — for `wizards-of-the-coast/srd-2024`: 339 spells (576 KB) + 671 casting options (230 KB), 331 creatures
  (867 KB) + 525 KB actions + 255 KB attacks + 111 KB traits, 24 class/subclass rows, 352 class features
  (225 KB) + 1,811 feature items (393 KB), 9 species + 51 traits, 4 backgrounds + 20 benefits, 17 feats,
  203 mundane items, 38 weapons, 17 weapon properties, 13 armors, 56 rules + 11 rule sets (104 KB);
  **3.50 MB total, 0.32 MB gzipped**. `srd-2014` in v2 is 3.77 MB / 0.41 MB.
- Ingestion: `uv run python manage.py quicksetup` loads every fixture into SQLite and (optionally) builds
  the search index; `CONTRIBUTING.md` documents adding sources via `Document.json` (v1) or the admin
  interface + export (v2). Publication metadata (`publisher`, `licenses`, `permalink`, `publication_date`)
  lives in each `Document.json`.

**Does it include 2024 content?** Yes, substantially: spells, creatures (including the 5.2.1 Octopus and
Knight), classes/features, species, backgrounds, feats, rules glossary entries, equipment. **Missing:**
magic items (all editions in v2), and the `Document.json` still calls it "System Reference Document 5.2"
with a placeholder `publication_date` of 2024-01-01. The `srd-2024` fixtures also carry some empty
`experience_points_integer`/`proficiency_bonus` fields — expect to derive those from CR.

### 2.2 5e-bits (`5e-bits/5e-database`, dnd5eapi.co)

**License.** [`LICENSE.md`](https://raw.githubusercontent.com/5e-bits/5e-database/main/LICENSE.md) is plain
MIT (Adrian Padua, Christopher Ward, 2018–2020). The README adds: "The underlying material is released
using the Open Gaming License Version 1.0a" — accurate for its origin, but the same SRD 5.1 text is now
also CC-BY, and the 2024 data can only be CC-BY; the repo has not updated that sentence. Treat the
*data* as SRD text under WotC's CC-BY terms and the *JSON structure/scripts* as MIT.

**Layout.** `src/{2014,2024}/{locale}/5e-SRD-<Collection>.json` (locale-nested since the translation
work; `scripts/dbUtils.ts` derives collection names like `2014-spells`); `npm run db:refresh` loads
MongoDB for the API. `package.json` version 5.10.0.

**2014 set (complete):** 25 files, **3.87 MB, 0.50 MB gzipped** — 319 spells (608 KB), 334 monsters
(1.34 MB), 362 magic-item rows (435 KB; 239 base + 123 variants), 12 classes (151 KB), 12 subclasses,
407 features (356 KB), 290 level rows (225 KB), 9 races, 4 subraces, 38 traits, 1 background, 1 feat,
15 conditions, 33 rule-sections (197 KB), 6 rules, 237 equipment, 39 equipment categories, 117
proficiencies, 18 skills, 16 languages, 13 damage types, 9 alignments, 8 magic schools, 11 weapon
properties.

**2024 set (partial, 29 Aug 2026):** 22 files, 1.34 MB — 12 classes (191 KB), 12 subclasses, 232
features, 287 level rows, 17 feats, 9 species, 67 traits, 4 backgrounds, 262 magic-item rows (243 base;
**built from 5.2 — none of the 15 items restored in 5.2.1 are present, though "Mysterious Deck" and
"Dragon Orb" are**), 182 equipment, 8 weapon-mastery properties, 15 conditions, 19 languages.
**No spells file; monsters file has 3 entries (Aboleth, Adult Black Dragon, Adult Blue Dragon)** —
confirmed live at `/api/2024/monsters` (`count: 3`) and by the `/api/2024` index, which has no `spells`,
`rules` or `rule-sections` endpoint.

**Record shapes (2014).** Spell: `index, name, desc[], higher_level[], range, components[], material,
ritual, duration, concentration, casting_time, level, attack_type, damage{damage_type, damage_at_slot_level},
school{index,name,url}, classes[], subclasses[], url`. Monster: `index, name, size, type, alignment,
armor_class[], hit_points, hit_dice, hit_points_roll, speed{}, strength…charisma, proficiencies[],
damage_vulnerabilities/resistances/immunities[], condition_immunities[], senses{}, languages,
challenge_rating, proficiency_bonus, xp, special_abilities[], actions[], legendary_actions[], image, url`.
Class: `index, name, hit_die, proficiency_choices[], proficiencies[], saving_throws[], starting_equipment[],
starting_equipment_options[], class_levels (url), multi_classing{}, subclasses[], url`; 2024 adds
`primary_ability`. Cross-references are relative API URLs (`/api/2014/damage-types/acid`), which a bundler
must rewrite to keys.

### 2.3 Foundry VTT `dnd5e` system (`foundryvtt/dnd5e`)

`LICENSE.txt` is MIT (software). The README carries both CC-BY attributions verbatim ("This work includes
material taken from the System Reference Document 5.1 …" and "… System Reference Document 5.2 …") and
says "Images and other assets are distributed under various terms, please see their LICENSE files".
`system.json` (v5.3.3, Foundry v13–14) declares 13 SRD 5.1 packs (`monsters`, `items`, `spells`,
`classes`, `subclasses`, `classfeatures`, `races`, `backgrounds`, `monsterfeatures`, `rules`, `tables`,
`tradegoods`, `heroes`) and 9 SRD 5.2 packs (`actors24`, `classes24`, `origins24`, `feats24`,
`spells24`, `equipment24`, `monsterfeatures24`, `content24`, `tables24`), each flagged
`sourceBook: "SRD 5.1"` / `"SRD 5.2"`. Sources are JSON per document under `packs/_source/` (Foundry
Document format: `{name, type, system:{…}, items:[…], effects:[…], flags}`), compiled to LevelDB at
build. **Red flag:** `tokens/LICENSE` — Forgotten Adventures token art "may not be redistributed or used
outside of Foundry Virtual Tabletop"; `icons/LICENSE` lists game-icons.net SVGs under CC-BY-3.0/CC0. Any
extraction must strip `img`/`texture` paths and take text only. It is the most complete structured
2024 source, but the data model is Foundry-specific (activities, advancement, effects) and heavy to
normalize.

### 2.4 Other repositories and documents

| Source | URL | License (verified where) | Format | Edition | Completeness / last activity |
|---|---|---|---|---|---|
| downfallx/dnd-5e-srd-markdown | github.com/downfallx/dnd-5e-srd-markdown | CC-BY-4.0 (`LICENSE` carries the 5.2.1 attribution) | 16 Markdown files, ~1.9 MB | **SRD 5.2.1** | Contains all 15 restored items, Octopus, 12 subclasses, 339 spell headings; README overclaims "500+ spells, 400+ monsters"; single commit; no provenance of conversion — use as a cross-check, not a primary |
| sycarion/5e-2024-SRD | github.com/sycarion/5e-2024-SRD | CC-BY-4.0 | Markdown | claims 5.2.1 | Fork of OldManUmby's 5.1 conversion (18 Jun 2025); `Changelog.md` still lists "Update material to reflect 5.2.1 SRD" as **to-do** — not yet a 5.2.1 dataset |
| OldManUmby/DND.SRD.Wiki | github.com/OldManUmby/DND.SRD.Wiki | CC-BY-4.0 (license switched 1 Jun 2023) | Markdown "reForged" | SRD 5.1 | Complete, hand-remastered; mirrors: palikhov/cc-srd5-1, palikhov/DND5E.SRD.Wiki |
| mshea/lazy_gm_tools → 5e Artisanal Database | github.com/mshea/lazy_gm_tools | code CC0; content per-source (`licensing.html` reproduces the SRD 5.1, SRD 5.2, A5ESRD and BFRD 1.0 CC-BY attributions and the OGL §15 list for Kobold books) | Markdown + HTML + lunr index; also a 10 MB single-file "5.2 SRD Rules Search" HTML with 8,456 embedded rule chunks | SRD 5.1, **SRD 5.2 (not 5.2.1)**, A5E, BFRD, KP OGL | 5.2 rules Markdown = 1.35 MB excluding classes; lacks the 15 items/Octopus |
| BTMorton/dnd-5e-srd | github.com/BTMorton/dnd-5e-srd | OGL 1.0a (`LICENSE` says SRD 5.0) | JSON + Markdown | SRD 5.0 (2016) | Stale; skip |
| Scurvy_Platypus editable 5.2.1 | enworld.org/threads/editable-5-2-1-srd.716978 | CC (attachment) | DOCX, 1.6 MB | 5.2.1 | 31 Dec 2025; "redid the whole thing with proper styles" |
| DMDocs (ScavieFae) | dmdocs.vercel.app | **[unverified]** | MDX site | 5.2.1 | mentioned in the same thread |
| your5e/5e-srd-markdown, katekorsaro/dnd5e-srd | github | **[unverified]** | Markdown | 5.1 | not fetched |
| bfrd.net | bfrd.net | ORC + CC-BY-4.0 (site legal block) | HTML | Black Flag v0.1 web, Jan 2026 | no JSON export |
| Kobold BFRD PDF | koboldpress.com/wp-content/uploads/2025/07/Black-Flag-Roleplaying-v04_2025_07_01.pdf | CC-BY-4.0 + ORC ([announcement 16 Jul 2025](https://koboldpress.com/kobold-press-releases-the-black-flag-reference-document-bfrd-in-creative-commons-updated-with-new-material-for-gms/)) | PDF | Black Flag 1.0 | includes GM's Guide material |
| Sly Flourish LGMRD | slyflourish.com/lazy_gm_resource_document.html | CC-BY-4.0 | HTML, EPUB, Markdown, JSON (GitHub) | system-light GM tools | updated 24 Dec 2024 |
| a5esrd.com | a5esrd.com/a5esrd | CC-BY-4.0 + OGL + ORC | PDF/ZIP only | A5E | no machine-readable release from EN Publishing; Open5e and 5eADB hold conversions |

Searches for "srd52", "srd-2024 json", "5e-database 2024 branch", "dnd5e-srd-2024" found nothing beyond
the above; there is no SQLite or CSV release of 5.2.1 anywhere public today.

### 2.5 5e.tools — why its data must not be used

The site's code (5etools-mirror-3/5etools-src) is MIT and its README says so. Its content is not.
`CONTRIBUTING.md`: "Only 'official' (that is, published by WotC) data is to be included in the site.
Anything else should be added to the homebrew repository." That is a declaration that the `data/` JSON is
the copyrighted text of the PHB, MM, DMG and every supplement, transcribed — exactly what WotC's counsel
described in the [7 Aug 2024 DMCA notice](https://github.com/github/dmca/blob/master/2024/08/2024-08-07-wizards-of-the-coast.md)
against 5etools-mirror-1/2: "content copied verbatim from Wizards' D&D publications, including the core
rulebooks, supplements, and campaign settings." An MIT license on a repository cannot license text the
repository's authors do not own; bundling any of it in a public, Light-signed APK would expose the tool,
the repo, and Light to a takedown. The same reasoning applies to kinkofer/FightClub5eXML's `Sources/` (the
fetched `races-phb.xml` cites "Source: Player's Handbook p. 32" and reproduces PHB flavor text) and to
D&D Beyond's monster/spell services. Use these projects only as **schema references** for import.

### 2.6 Third-party open content and the ORC question

**Kobold Press.** Back-catalog (Tome of Beasts 1/2/3 and the 2023 edition of ToB1, Creature Codex, Deep
Magic, Vault of Magic, Tome of Heroes, Warlock zines) is OGL 1.0a — both Open5e's document metadata and
Sly Flourish's OGL §15 list confirm it. The Black Flag Reference Document (the open core of Tales of the
Valiant) was ORC at launch (16 Oct 2023, [announcement](https://koboldpress.com/black-flag-reference-document/))
and became **dual ORC + CC-BY-4.0 on 16 Jul 2025**; Kobold: this "dual licensing means publishers and
creators can choose the license that best fits their goals or even publish under both licenses." The
"Tales of the Valiant" mark is not open — using the ToV logo requires "signing a compatibility license"
([State of Play, 31 Jul 2025](https://koboldpress.com/state-of-play-creative-commons-orc-and-making-black-flag-as-open-as-possible/)).
BFRD content is 5e-2014-shaped (lineages/heritages/talents/luck) and ~400 monsters; an obvious
optional pack for a bestiary, under the attribution Sly Flourish already uses: "This work includes
material taken from the Black Flag Reference Document 1.0 ("BFRD 1.0") by Kobold Press and available at
https://koboldpress.com/Black-Flag-Roleplaying. The BFRD 1.0 is licensed under the Creative Commons
Attribution 4.0 International License…"

**Level Up: Advanced 5th Edition.** [a5esrd.com](https://a5esrd.com/a5esrd) offers the A5ESRD under
three concurrent licenses — CC-BY-4.0 (attribution: "This work includes material taken from the A5E System
Reference Document (A5ESRD) by EN Publishing and available at A5ESRD.com, based on Level Up: Advanced 5th
Edition, available at www.levelup5e.com."), OGL 1.0a, and ORC — covering Adventurer's Guide, Trials &
Treasures, Monstrous Menagerie, Dungeon Delver's Guide, Voidrunner's Codex and Gate Pass Gazette. Red flag:
Open5e tags its `a5e-mm` (Monstrous Menagerie) document OGL-only, presumably because it was ingested from
the book; if A5E monsters are wanted, cite and source the CC-BY A5ESRD text, not Open5e's `a5e-mm` rows.

**Sly Flourish.** The Lazy GM's Resource Document is CC-BY-4.0 with the attribution "This work includes
material taken from the Lazy GM's Resource Document by Michael E. Shea of SlyFlourish.com, available under
a Creative Commons Attribution 4.0 International License." Sections: prep checklist, encounter building,
improvised damage/DC tables, monster templates, quick monster stats, random generators — ideal GM-screen
material. Its JSON/Markdown live in `mshea/lazy_gm_tools`; the 5eADB code is CC0.

**Other CC-BY 5e content seen:** "Spells That Don't Suck" (Somanyrobots, CC-BY-4.0, in Open5e). The
"5e Artisanal Database" is Sly Flourish's compilation, not a separate license. KibblesTasty and most
homebrew compendia are all-rights-reserved or OGL — skip.

**ORC vs CC-BY for bundling.** From the [ORC License](https://paizo.com/orclicense) and Paizo's
[ORC AxE FAQ](https://downloads.paizo.com/ORC_AxE_FINAL.pdf): ORC licenses "expressions reasonably
necessary to convey functional ideas and methods of operation of a game" (Licensed Material) and withholds
"trademarks, trade dress, and creative expressions that are not essential" (Reserved Material); the grant is
"worldwide, royalty-free, non-sublicensable, non-exclusive, irrevocable"; the licensee must publish an ORC
Notice ("This product is licensed under the ORC License located at the Library of Congress at TX 9-307-067
and available online"), an attribution notice, a Reserved Material notice, and must offer its own Adapted
Licensed Material back under ORC. "Nothing in this license restricts the platform on which the Licensed
Material can be used," but "No license is granted under the ORC to specific software code." On mixing:
"It is possible to include CC-BY licensed content in an ORC Licensed Work. The ORC License does not create
any restrictions on downstream licensee use of the CC-BY licensed content." Conversely: "No. If you are
granted the right to use game mechanics under the ORC License, you can't license that content out under
CC-BY unless the licensor … also agree[s] to the transition." And OGL content cannot move to ORC at all.

Consequence: an app that bundles ORC-only material must ship an ORC Notice and treat its derived rules
data as ORC-licensed (the Kotlin code is unaffected). Because both BFRD and A5ESRD are *also* CC-BY, the
tool can take everything it wants under CC-BY and never invoke ORC. OGL-only Kobold books would force the
full OGL text, a §15 chain, an Open Game Content designation, and OGL §7's ban on compatibility claims into
the APK — which collides with the "5E compatible" line. Recommendation: **v1 = SRD 5.1 + 5.2.1 only; later
optional packs = BFRD, A5ESRD, LGMRD under CC-BY; no OGL, no ORC-only.**

### 2.7 Homebrew and character formats a player might bring

| Format | Structure (verified) | Offline on-device import? |
|---|---|---|
| **Fight Club 5e / Game Master 5e XML** | Root `<compendium version="5">`; children `<spell>` (`name, classes, level, school` [A/C/D/EN/EV/I/N/T], `ritual` YES/NO, `time, range, components, duration, text, roll*`), `<monster>` (`name, size` T/S/M/L/H/G, `type, alignment, ac, hp, speed, str…cha, save, skill, resist, vulnerable, immune, conditionImmune, senses, passive, languages, cr, environment, description, trait*/action*/reaction*/legendary*` each `{name,text,attack*}`, `spells, slots`), `<item>` (`name, type, detail, weight, value, text, property, dmg1/dmg2/dmgType, roll`), `<race>` (`name, size, speed, ability, proficiency, spellAbility, trait*`), `<class>` (`hd, proficiency, numSkills, armor, weapons, tools, wealth, autolevel[@level]{feature{name,text}, slots, counter}`), `<feat>` (`prerequisite, text, modifier`), `<background>` (`proficiency, trait*`). Collections are merged with `Utilities/merge.xslt` (kinkofer/FightClub5eXML). | **Yes** — flat XML, `org.xmlpull.v1.XmlPullParser` is in the platform and not blocked. Best single homebrew target: hundreds of community compendia exist. |
| **5etools homebrew JSON** | `{ "_meta": { "sources":[{json, abbreviation, full, url, version, authors}], "edition", "dateAdded", … }, "spell":[…], "monster":[…], "item":[…], "race":[…], "class":[…], "subclass":[…], "background":[…], "feat":[…], "optionalfeature":[…] }`; entries use the site's rich `entries` tree with `{@spell fireball|PHB}` tags. JSON Schemas at TheGiddyLimit/5etools-utils `schema/brew`. | **Partial** — kotlinx-serialization can parse it; rendering `entries` faithfully needs a small tag-stripping renderer. Homebrew files may embed copyrighted conversions; import only what the user supplies, never bundle. |
| **D&D Beyond character JSON** | `GET https://character-service.dndbeyond.com/character/v5/character/{id}` (ddb-proxy `config.js`); works for characters shared publicly; not supported by DDB ("Just PDF"). Body `{id, success, data:{id, name, race, classes[{level, definition, subclassDefinition}], stats[], bonusStats[], overrideStats[], baseHitPoints, inventory[{definition, quantity, equipped}], classSpells[], spells{}, modifiers{race,class,background,item,feat,condition}, feats[], background, currencies, traits, …}}` **[field list from community importers; verify against a live export]**. | **Desktop companion only** — needs network + a character the user owns; definitions embed DDB's copyrighted text, so import into the user's private store, never ship. |
| **Foundry actor/item JSON** | Foundry Document export: `{name, type:"character"|"npc"|"spell"…, system:{abilities, attributes{hp, ac, movement}, details{cr, type, level}, traits, …}, items:[…], effects:[…], flags}`; version-specific (`dnd5e` 5.x). | **Feasible** for stats; heavy schema; convert on desktop. |
| **Homebrewery Markdown** | Markdown + custom blocks: `\page`, `\column`, `{{monster,frame …}}`, `{{note}}`, `{{descriptive}}`, `___` stat-block separators, tables (naturalcrit/homebrewery, MIT, v3.22.0). Not a schema — stat blocks are prose conventions. | **No** (heuristic parse); desktop converter at best. |
| **Roll20 character export** | Roll20 has no native JSON export; the VTT Enhancement Suite exports `{schema_version, type:"character", character:{name, attribs:[{name,current,max}], abilities, bio…}}` **[unverified]**. | **Desktop companion only.** |

Given the LP3's constraints (no file picker or `READ_MEDIA_*` permission; `com.google.zxing:core` is on
the dependency allow-list; `CAMERA` is on the permission allow-list; a LightOS File Manager is promised
but not in the SDK), the realistic on-device path is **paste or QR** of a compact, tool-native JSON
produced by a desktop converter (`fc5e-xml | 5etools-json | ddb-json → compendium-homebrew.json →
chunked QR`), with direct on-device XML parsing as a stretch goal once file transfer exists.

---

## Part 3 — Sizes and pipeline

### 3.1 Measured sizes (29 Aug 2026)

| Dataset | Raw | gzip -9 | What it covers |
|---|---|---|---|
| Open5e v1 `wotc-srd` (SRD 5.1, 17 files) | 2.24 MB | 0.43 MB | complete incl. 237 magic items, 45 rule sections |
| Open5e v2 `srd-2014` (24 files) | 3.77 MB | 0.41 MB | complete except magic items |
| Open5e v2 `srd-2024` (22 files) | 3.50 MB | 0.32 MB | complete except magic items |
| 5e-bits 2014 (25 files) | 3.87 MB | 0.50 MB | complete (verbose cross-reference URLs) |
| 5e-bits 2024 (22 files) | 1.34 MB | 0.17 MB | partial (no spells, 3 monsters) |
| downfallx 5.2.1 Markdown (spells, monsters, items, classes, README) | 1.53 MB | 0.28 MB | ~1.9 MB for all 16 files |
| Sly Flourish 5.2 Markdown (13 files, no classes) | 1.35 MB | 0.32 MB | — |
| **Prototype SQLite** built today from Open5e `srd-2024` (339 spells, 331 creatures with actions/traits, 352 class features, 56 rules) + 237 v1 magic items, **no FTS** | **1.47 MB** | 0.33 MB | after `VACUUM` |
| same **with FTS4** index over name+body | **3.16 MB** | 0.90 MB | FTS roughly doubles the file |
| same with FTS5 | 3.11 MB | 0.89 MB | Room has no FTS5 annotation; use `@Fts4` |

Rule of thumb: one SRD edition ≈ 1.5 MB of SQLite pages, ≈ 3 MB with a full-text index, ≈ 0.3–0.5 MB
as compressed JSON. Both editions plus BFRD would still be under 10 MB uncompressed. Text dominates:
spells ≈ 0.5 MB, creatures ≈ 1.7 MB (with actions), magic items ≈ 0.25 MB, class features ≈ 0.6 MB,
rules ≈ 0.1–0.3 MB per edition. Individual JSON files are all far below the builder's 5 MiB cap.

### 3.2 Platform constraints that shape the pipeline (from `light-sdk` source)

- Light's builder extracts only `tool/build.gradle.kts`, `tool/lighttool.toml` and
  `tool/src/main/{kotlin,java,res,assets}/**`; assets must have one of `.png .jpg .jpeg .webp .gif .svg
  .json .txt .md .ttf .otf .bin .dat .csv .html .css`; any other extension **aborts the build**; per-file
  cap 5 MiB, 100 MiB total (`builder/lightbuilder/allowlist.py`, `extract.py`). So: **no `.db`, `.sqlite`,
  `.gz`, `.zip`**, and no build-time generation outside `tool/` — the pipeline runs on the desktop and its
  outputs are committed.
- Tool code gets `lightContext.readAsset(path): ByteArray` (whole file) and `lightContext.filesDir`
  (`LightActivity.kt:231-234`); Room via `lightContext.buildDatabase(MyDb::class.java, "name.db")`
  (`LightDb.kt`, wraps `Room.databaseBuilder(...).build()` — **no `createFromAsset` hook**, and `Context`
  is unreachable so you cannot build your own). `androidx.room` (+ `room-compiler` via KSP),
  `org.jetbrains.kotlinx:kotlinx-serialization`, `kotlinx-io` and `com.google.zxing:core` are on the
  dependency allow-list (`LightSdkPlugin.kt:17-45`). `android.database.sqlite` is **not** in
  `BLOCKED_IMPORTS`.
- No APK size limit exists in SDK or builder; the 5 MiB cap is per source file.
- The submission bar is aesthetic/ethos as much as legal; a clear `LICENSE` split and an About screen are
  cheap credibility.

### 3.3 Recommended build-time pipeline

```
pipeline/  (desktop-only, Python 3.12, outside tool/ so the builder never sees it)
├── sources.lock.json        # pinned upstream commits + SRD version strings + sha256 of every input
├── fetch.py                 # curl raw.githubusercontent.com fixtures into pipeline/cache/<source>@<sha>/
├── normalize/               # one adapter per source → unified schema
│   ├── open5e_v2.py         # spells, creatures(+actions/traits/attacks), classes/features, species, backgrounds, feats, rules
│   ├── open5e_v1.py         # SRD 5.1 magic items, sections, conditions
│   ├── fivebits_2024.py     # magic items (5.2) — then patch in the 15 restored items from 5.2.1 text
│   └── srd521_markdown.py   # cross-check + fill gaps from the PDF-derived Markdown
├── schema/compendium.schema.json   # unified schema (JSON Schema draft 2020-12)
├── validate.py              # schema + referential integrity + edition-specific expected counts (339/331/258/17/9/4/12/12)
├── emit.py                  # → tool/src/main/assets/compendium/{spells,creatures,items,classes,features,species,backgrounds,feats,rules,conditions}.json (+ index.json with edition, srdVersion, sha256, counts)
└── legal.py                 # → tool/src/main/assets/legal/ATTRIBUTION.md, LICENSE-CC-BY-4.0.txt (copied verbatim), sources.md
```

Design points:

1. **Unified schema**: one record type per category with `key`, `name`, `edition` ("2014"|"2024"),
   `source` ("srd-5.1"|"srd-5.2.1"|…), `license` ("CC-BY-4.0"), searchable `text` fields, and typed
   fields where the UI needs them (level, school, CR, rarity, attunement). Keep original Open5e/5e-bits
   keys in `xref` for traceability; keep prose as Markdown-lite (bold/italic/tables only).
2. **Merge policy for 5.2.1**: Open5e v2 for spells/creatures/classes/species/backgrounds/feats/rules;
   5e-bits 2024 for magic items; add the 15 restored items and the Octopus/Knight from the 5.2.1 Markdown
   (or by hand from the PDF); assert the final counts against §1.2's table so a silent regression fails CI.
3. **Emit JSON, not a DB.** kotlinx-serialization reads the chunks; on first launch the tool bulk-inserts
   into Room inside one transaction (a few thousand rows — expect ~1–3 s on the LP3; show the Light
   spinner) and records `index.json`'s sha256 in DataStore; on upgrade, a differing hash triggers a
   re-import into fresh tables. Room `@Fts4` entities give offline search. Alternative if first-launch
   import proves slow: ship the prebuilt SQLite as `compendium.sqlite.bin`, copy it to `filesDir` via
   `readAsset`, and open read-only with `android.database.sqlite.SQLiteDatabase.openDatabase(path, null,
   OPEN_READONLY)` — legal under the allow-list, but name it honestly and mention it in the submission;
   Light has been asked whether `.db` will be allowed (see 04 §open questions).
4. **Attribution files travel with the data**: `assets/legal/ATTRIBUTION.md` is rendered on the About
   screen with `readAsset`; the same text lives in the repo README; `LICENSES/` holds MIT (code, inherited
   from light-sdk) and CC-BY-4.0 (data) side by side, and the repo `LICENSE` points to both.
5. **Reproducibility check in CI**: `python -m pipeline` on a clean clone must produce byte-identical
   assets (sorted keys, fixed indentation, pinned upstream commits) — `git diff --exit-code
   tool/src/main/assets` is the test. This is what lets a reviewer trust that the committed data equals
   the pinned public sources.
6. **Diffing on SRD updates**: bump `sources.lock.json` (new Open5e/5e-bits commits, new SRD version
   string), re-run, and review `git diff` of the emitted JSON — because records are keyed and sorted, a
   5.2.2 that touches ten spells shows as ten hunks. Also diff the SRD PDFs themselves: WotC's
   changelogs are prose, so keep a `pipeline/srd-changelog.md` with the official list per version
   (5.2.1's 15 items + Knight + Octopus is the first entry). Attribution must name the exact version
   bundled; if 5.1 and 5.2.x coexist, both sentences appear.
7. **Reference pipelines**: Open5e's `manage.py quicksetup` (fixtures → SQLite → search index) and
   5e-bits' `scripts/dbRefresh.ts` (JSON → Mongo collections named `<year>-<collection>`) are the two
   ingestion loops worth reading; Sly Flourish's 5eADB shows a lunr index prebuilt at generation time,
   the same trick as shipping an FTS table.

### 3.4 What can go in the APK

| Source | License | Attribution obligation | Edition | Allowed? |
|---|---|---|---|---|
| SRD 5.2.1 (text via Open5e v2 / 5e-bits 2024 / Markdown / PDF) | CC-BY-4.0 | WotC's 5.2.1 sentence, license link, modification note | 2024 | **Yes** |
| SRD 5.1 (text via Open5e v1 / 5e-bits 2014 / OldManUmby) | CC-BY-4.0 | WotC's 5.1 sentence, license link, modification note | 2014 | **Yes** |
| Open5e / 5e-bits JSON *structure and scripts* | Modified MIT / MIT | copyright + permission notice if code is copied; not needed for data | — | Yes |
| Foundry `dnd5e` pack text | CC-BY (SRD) via MIT-licensed system | SRD sentences; strip all image/token references | both | With caveat |
| Foundry `tokens/` art, any WotC art, Fan Site Kit images | proprietary / FCP | — | — | **No** |
| 5e.tools `data/`, FightClub5eXML `Sources/`, D&D Beyond compendium services, Basic Rules | unlicensed copyrighted text | — | — | **No** |
| Kobold Press BFRD 1.0 | CC-BY-4.0 (also ORC) | Kobold's BFRD sentence | 2014-shaped (ToV) | Yes, optional pack |
| Level Up A5ESRD (from a5esrd.com) | CC-BY-4.0 (also OGL, ORC) | EN Publishing's A5ESRD sentence | A5E | Yes, optional pack |
| Kobold Tome of Beasts 1–3, Creature Codex, Deep Magic, Vault of Magic (Open5e `ogl-10a` docs) | OGL 1.0a | full OGL text + §15 chain + OGC designation; §7 bars "compatible with" claims | 2014 | Avoid |
| Tales of the Valiant ORC-only books, other ORC material | ORC | ORC Notice + Reserved Material notice; derived data becomes ORC | — | Avoid for v1 |
| Lazy GM's Resource Document | CC-BY-4.0 | Sly Flourish sentence | system-light | Yes, optional |
| "Spells That Don't Suck" | CC-BY-4.0 | author attribution per Open5e document | 2014 | Yes, optional |
| User-supplied homebrew (FC5 XML, 5etools JSON, DDB character) | user's own / mixed | none (private store, never redistributed) | any | Import only |
| WotC trademarks in tool name/branding | trademark, not licensed | — | — | **No**; "5E compatible" only |

### 3.5 Ready-to-paste attribution / legal block

For `README.md` (section "Licenses and attribution") and, verbatim, the in-app About screen
(`assets/legal/ATTRIBUTION.md`, rendered with `readAsset`):

```
This tool is 5E compatible. It bundles rules text from the System Reference Documents
published by Wizards of the Coast under the Creative Commons Attribution 4.0 International
License (CC-BY-4.0). The text has been reformatted and reorganized into a searchable database;
wording may have been abridged or restructured for display on a small screen, and some entries
are merged from more than one edition of the document as indicated in the entry.

This work includes material from the System Reference Document 5.2.1 ("SRD 5.2.1") by Wizards
of the Coast LLC, available at https://www.dndbeyond.com/srd. The SRD 5.2.1 is licensed under
the Creative Commons Attribution 4.0 International License, available at
https://creativecommons.org/licenses/by/4.0/legalcode.

This work includes material taken from the System Reference Document 5.1 ("SRD 5.1") by
Wizards of the Coast LLC and available at
https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under
the Creative Commons Attribution 4.0 International License available at
https://creativecommons.org/licenses/by/4.0/legalcode.

The full text of CC-BY-4.0 is included as LICENSE-CC-BY-4.0.txt. The bundled data is provided
"as is"; see Section 5 of CC-BY-4.0 for the disclaimer of warranties and limitation of
liability.

Application source code is licensed under the MIT License (see LICENSE). This tool is a
derivative of lightphone/light-sdk, also MIT-licensed.
```

Drop the 5.1 paragraph if only 5.2.1 ships (and vice-versa). If an optional Black Flag pack is added,
append Kobold's sentence from §2.6; if A5E, EN Publishing's; if Lazy GM content, Sly Flourish's. Do not
add a "Dungeons & Dragons is a trademark of Wizards of the Coast" line — WotC's legal text asks that no
attribution beyond the prescribed sentence be included, and the Fan Content Policy disclaimer belongs only
to works that use non-SRD IP under that policy.

Repo `LICENSE` file, top lines:

```
Code:  MIT (see LICENSES/MIT.txt)
Data:  tool/src/main/assets/compendium/** is derived from CC-BY-4.0 material;
       see tool/src/main/assets/legal/ATTRIBUTION.md and LICENSES/CC-BY-4.0.txt
```

---

## Sources

WotC / D&D Beyond
- SRD page, versions and dates: https://www.dndbeyond.com/srd
- SRD 5.2.1 PDF (legal text, contents): https://media.dndbeyond.com/compendium-images/srd/5.2/SRD_CC_v5.2.1.pdf
- SRD 5.1 CC PDF (legal text): https://media.wizards.com/2023/downloads/dnd/SRD_CC_v5.1.pdf
- Conversion guide 5.1 → 5.2.1: https://media.dndbeyond.com/compendium-images/srd/guide/converting-to-srd-5.2.1.pdf
- "You Can Now Publish Your Own Creations Using the New Core Rules": https://www.dndbeyond.com/posts/1949-you-can-now-publish-your-own-creations-using-the
- Community Update (5.2.1 changelog): https://www.dndbeyond.com/community-update
- D&D Beyond on X, 5.2.1 announcement: https://x.com/DnDBeyond/status/1917972286771994835
- "OGL 1.0a & Creative Commons" (27 Jan 2023): https://www.dndbeyond.com/posts/1439-ogl-1-0a-creative-commons
- Sigil sunset (24 Oct 2025): https://www.dndbeyond.com/posts/2086-closing-the-chapter-on-sigil-and-thanking-the
- Forum: SRD 5.2 discussion: https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/219273-official-srd-5-2-discussion-thread-srd-5-2-1-now
- Forum: SRD 5.2.1 data formats (May 2025–Mar 2026): https://www.dndbeyond.com/forums/d-d-beyond-general/general-discussion/221238-srd-5-2-1-data-in-technically-usable-formats
- Forum: export to Roll20/JSON (Jan 2026): https://www.dndbeyond.com/forums/d-d-beyond-general/d-d-beyond-feedback/235583-exporting-sheet-to-roll20
- Wizards Fan Content Policy: https://company.wizards.com/en/legal/fancontentpolicy
- CC-BY-4.0 legal code: https://creativecommons.org/licenses/by/4.0/legalcode

Reporting on SRD 5.2 / OGL
- Tribality, SRD 5.2 breakdown: https://www.tribality.com/2025/04/23/dd-system-reference-document-v5-2/
- Roll20, "D&D SRD 5.2 – What You Need to Know": https://pages.roll20.net/dnd-srd
- EN World, SRD 5.2 live: https://www.enworld.org/threads/dungeons-dragons-srd-5-2-is-officially-live.713038/
- EN World, terms removed / "D&D" in the SRD: https://www.enworld.org/threads/some-terms-removed-from-2024-srd-but-is-d-d-really-supposed-to-be-in-there.713077/
- EN World, 15 missing magic items: https://www.enworld.org/threads/the-5-2-srd-pdf-has-new-content-over-5-1-but-the-15-newly-added-magic-items-are-all-missing.713128/
- EN World, editable 5.2.1 DOCX: https://www.enworld.org/threads/editable-5-2-1-srd.716978/
- Wikipedia, Open Game License (2023 timeline): https://en.wikipedia.org/wiki/Open_Game_License
- Wikipedia, System Reference Document: https://en.wikipedia.org/wiki/System_Reference_Document
- GitHub DMCA notice vs 5etools mirrors (7 Aug 2024): https://github.com/github/dmca/blob/master/2024/08/2024-08-07-wizards-of-the-coast.md

Data repositories and APIs (raw files fetched)
- open5e/open5e-api LICENSE.md, README.md, .github/CONTRIBUTING.md, data/v1/wotc-srd/*.json, data/v2/wizards-of-the-coast/{srd-2014,srd-2024}/*.json: https://github.com/open5e/open5e-api
- Open5e API: https://api.open5e.com/v2/documents/ · /v2/licenses/ · /v2/spells/?document__key=srd-2024 · /v2/creatures/?document__key=srd-2024 · /v2/classes/?document__key=srd-2024 · /v2/items/ · /v1/magicitems/?document__slug=wotc-srd
- 5e-bits/5e-database LICENSE.md, README.md, package.json, scripts/dbRefresh.ts, scripts/dbUtils.ts, src/{2014,2024}/en/*.json: https://github.com/5e-bits/5e-database
- dnd5eapi.co 2024 index and monsters: https://www.dnd5eapi.co/api/2024 · https://www.dnd5eapi.co/api/2024/monsters · docs https://5e-bits.github.io/docs/
- foundryvtt/dnd5e LICENSE.txt, README.md, system.json, icons/LICENSE, tokens/LICENSE, CONTRIBUTING.md: https://github.com/foundryvtt/dnd5e
- downfallx/dnd-5e-srd-markdown (README, LICENSE, spells/monsters/items/classes/animals/feats/glossary .md): https://github.com/downfallx/dnd-5e-srd-markdown
- sycarion/5e-2024-SRD (README, Legal.md, Changelog.md): https://github.com/sycarion/5e-2024-SRD
- OldManUmby/DND.SRD.Wiki: https://github.com/OldManUmby/DND.SRD.Wiki
- BTMorton/dnd-5e-srd (LICENSE = OGL, SRD 5.0): https://github.com/BTMorton/dnd-5e-srd
- mshea/lazy_gm_tools (README, 5e_artisanal_database/README.md, licensing.html, rules/md/5.2 SRD/*.md, stand-alone 5.2 rules search HTML): https://github.com/mshea/lazy_gm_tools
- 5etools-mirror-3/5etools-src README.md and CONTRIBUTING.md: https://github.com/5etools-mirror-3/5etools-src
- TheGiddyLimit/homebrew README and sample spell JSON; schemas at https://github.com/TheGiddyLimit/5etools-utils/tree/master/schema/brew
- kinkofer/FightClub5eXML README, Collections/CoreOnly.xml, Utilities/merge.xslt, Sources/* (schema only): https://github.com/kinkofer/FightClub5eXML
- MrPrimate/ddb-proxy config.js (D&D Beyond character-service v5 endpoints): https://github.com/MrPrimate/ddb-proxy
- naturalcrit/homebrewery license, package.json, themes/V3/5ePHB/snippets: https://github.com/naturalcrit/homebrewery

Third-party licenses
- Paizo ORC License: https://paizo.com/orclicense · ORC AxE FAQ: https://downloads.paizo.com/ORC_AxE_FINAL.pdf
- Kobold Press BFRD launch under ORC (16 Oct 2023): https://koboldpress.com/black-flag-reference-document/
- Kobold Press BFRD in Creative Commons (16 Jul 2025): https://koboldpress.com/kobold-press-releases-the-black-flag-reference-document-bfrd-in-creative-commons-updated-with-new-material-for-gms/
- Kobold Press "State of Play" (31 Jul 2025): https://koboldpress.com/state-of-play-creative-commons-orc-and-making-black-flag-as-open-as-possible/
- bfrd.net legal block: https://bfrd.net/
- EN Publishing A5ESRD licensing: https://a5esrd.com/a5esrd
- Sly Flourish Lazy GM's Resource Document: https://slyflourish.com/lazy_gm_resource_document.html

Light SDK (local checkout, commit 3df3c24)
- builder/lightbuilder/allowlist.py (asset extensions, 5 MiB cap), plugin/…/LightSdkPlugin.kt (ALLOWED_DEPENDENCIES, BLOCKED_IMPORTS), sdk/client/…/LightActivity.kt (`readAsset`, `filesDir`), sdk/client/…/LightDb.kt (`buildDatabase`); summarized in 04-light-sdk-state.md §D.5.
