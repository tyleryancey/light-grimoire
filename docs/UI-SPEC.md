# Grimoire — UI specification (screen by screen)

Canvas: LP3 3.92" 1080×1240, `LightGrid` 27 × 31 units (1 unit ≈ 40 px ≈ 15.2 dp). Top bar
3 units, bottom bar 4 units plus its own 1-unit top margin (`LightBottomBar.kt:18-20`) →
23 content units ≈ 9 rows of `Copy` text or ~15 rows of `Detail`. Type scale is multiplied by
`screenHeightDp/600` (≈ 0.79 on the LP3), so `Copy` renders ≈ 24 sp, `Paragraph` ≈ 19 sp,
`Detail` ≈ 16 sp, `Subtitle` ≈ 41 sp, `Title` ≈ 90 sp.

Wireframes below are 27 characters wide = one grid unit per character. `▔` top bar, `▁`
bottom bar, `●`/`○` filled/hollow pip, `▸` row that navigates, `◂` its mirror (S13.2's level
stepper), `■`/`□` toggle on/off.

Component mapping (all `sdk:ui`, verified 29 Aug 2026 — see `.claude/skills/lp3-ui-patterns`):

| Need | Build from |
|---|---|
| Row list | `LightScrollView` (mixed heights) or `LightLazyScrollView` (uniform rows) + `LightText` + `lightClickable` |
| Toggle / pip | `LightIcon(TOGGLE_STATE_ON/OFF)` or `CIRCLE`/`STAR_OUTLINE` glyph in a clickable row; never a Material switch |
| Number pad | `LightText(Subtitle)` value + two glyph buttons (`UP`/`DOWN` or `ADD`/`DOWN`) + wheel via `LightKeyHandler` |
| Text entry | `LightTextField` (display) → `navigateTo(EditorScreen)` → `LightTextInputEditor(singleLine=true, initialCaps=true)` |
| Confirm | a `SimpleLightScreen<Boolean>` with CONFIRM/CANCEL in the bottom bar |
| Transient result | `LightModalManager.show(RollModal, 2 s)` for dice results; the originating row keeps the last result inline |
| Long text | `LightText(Paragraph)` inside `LightScrollView`; wheel scrolls |
| Bars | `LightTopBar(BACK · title · action)` / `LightBottomBar(≤ 5 items, ≤ 3 if any Text)` |

Wheel contract (`LightViewModel.onKeyDown`, key codes 317 up / 318 down / 319 press, from
`LightDeviceKeys`): on a list screen turns scroll; on a number pad turns nudge the focused
number by ±1; press = the primary action of the screen (roll / confirm / spend). Return
`true` only when the screen claims the event, so unhandled keys still reach LightOS
(compendium screens amend this — the next paragraph).
**Verified on hardware** 28 Aug 2026 on retail LightOS 572 (317 toward the top of the phone,
318 toward the bottom, 319 press, one DOWN/UP pair per detent) — the emulator still does not
emit 317–319, which is why S13.2 also draws tap arrows for its wheel job.

On compendium screens the tool owns the wheel: turns perform the screen's stated job (scroll,
or the level-step on the spells list) and the press is consumed as a no-op wherever the
screen defines no primary action — an unconsumed wheel event is forwarded to LightOS, which
foregrounds itself and relaunches the tool, a destructive context switch mid-reading. Volume
(24/25) and camera (80/27) keys are never consumed and still reach LightOS.

Global rules: no colour literals; state by weight/glyph; every list bounded; BACK is always
the top bar's left button (the examples' convention — the SDK draws no back bar); the top
bar centre is the screen name in `Fine`, except S13.2's two-line level stepper (`Detail`,
the SDK's own `LightTopBarCenter.TwoLineDetail` weight); the bottom bar holds screen actions
only.

---

## S0 Home

```
▔▔▔▔▔▔▔▔ GRIMOIRE ▔▔▔▔▔▔▔▔▔▔
Brother Aldric            ▸
  Cleric 5 · Hill Dwarf
Vessa Quickfinger         ▸
  Rogue 3 · Halfling
                            
COMPENDIUM                ▸
JOURNAL                   ▸
DICE                      ▸
                            
▁▁▁▁▁▁ NEW ▁▁▁ ABOUT ▁▁▁▁▁▁
```
Characters first (≤ 6), then the three utilities. Tap a character → S1. `NEW` → S12.
If exactly one character exists the tool still opens here (predictable, one extra tap).

First launch (and after a bundle or schema change): until the compendium is imported the list
is replaced by one line, `Preparing the rules…`, over a determinate `LightProgressBar` that
advances per kind (22 steps, ≈ 2.5 s on the LP3 — `sdk:ui` has no spinner); a failed import
shows its reason in that line and the next show retries. The list above appears only once the
store is Ready.

**M2 interim (31 Aug 2026):** until characters and the M3/M4 screens exist, the Ready branch
shows one `COMPENDIUM ▸` row in place of the character list, and a bottom bar with the single
text item `ABOUT`; `NEW`, `JOURNAL`, `DICE` and characters return with M3/M4.

## S1 Sheet hub

```
▔▔ BACK ▔▔ ALDRIC ▔▔ EDIT ▔▔
Cleric 5 · Hill Dwarf   ★ 
AC 18  INIT +0  SPD 25  PB +3
HP  31 / 43   TEMP 0      ▸
●●●● ●●● ●○   slots     ▸
TURN                      ▸
CHECKS & SAVES            ▸
SPELLS                    ▸
FEATURES & RESOURCES      ▸
CONDITIONS  · Bless (C)   ▸
GEAR & COIN               ▸
REST                      ▸
▁▁▁▁▁▁▁▁▁▁ DICE ▁▁▁▁▁▁▁▁▁▁▁
```
`★` = inspiration toggle (filled when held; 2014 term). HP row shows *bloodied* by rendering
the numbers in `Subheading` weight when current ≤ half. The slot row is a compact pip
strip (levels 1–3 shown; deeper levels on S5). Conditions row lists active conditions and
the concentration spell with "(C)". Everything is one tap away.

## S2 Turn (combat mode)

```
▔▔ BACK ▔▔▔ TURN ▔▔ ADV ▔▔▔
ACTION
Mace          +5   1d6+2  ▸
Sacred Flame  DC15 1d8    ▸
Spirit Guard. ●●○ 3d8     ▸
Guiding Bolt  ●●●● 4d6    ▸
BONUS
Spiritual Wpn ●●○ 1d8+4   ▸
Healing Word  ●●●● 1d4+4  ▸
REACTION
(none)                     
OTHER
Channel Div.  ●○          ▸
▁▁▁▁▁▁▁ HP ▁▁▁ DICE ▁▁▁▁▁▁▁
```
Tap a row = roll attack **and** damage together (or the save DC + damage for save spells);
pips are the *lowest usable slot level* and the cast spends one; long-press → "Upcast ↑ /
Advantage / Disadvantage / Read" chooser. `ADV` in the top bar toggles a sticky adv/dis mode
for the next roll (shown as `ADV`/`DIS`/`—`). Row order: attacks, then prepared spells by
level, then counters flagged `showOnTurn`. Result appears as a 2-second modal
(`S11 Roll result`); the row then shows the last result inline in `Detail` until the next
roll, so a missed modal costs nothing.

## S3 HP pad

```
▔▔ BACK ▔▔▔▔ HP ▔▔ DEATH ▔▔
                            
        31 / 43            
        temp 0             
                            
   −10    −5    −1         
   +10    +5    +1         
                            
 DAMAGE   HEAL    TEMP     
 [■]      [□]     [□]      
                            
▁▁▁▁▁▁▁▁▁▁ REST ▁▁▁▁▁▁▁▁▁▁▁
```
Verb chips select what the buttons and wheel apply to. Wheel = ±1 in the current verb.
Damage hits temp first; heal caps at max; temp keeps the higher value (rules.py). At 0 HP
the screen swaps its middle for death saves:

```
        0 / 43   DOWN       
 success  ○ ○ ○             
 failure  ● ○ ○             
      [ ROLL DEATH SAVE ]   
```
Natural 20 → "+1 HP" and saves clear; natural 1 → two failures; three failures renders
"DEAD" in `Heading` weight with a single "Undo last" (no drama, no animation).

## S4 Checks & saves

```
▔▔ BACK ▔▔ CHECKS ▔▔ ADV ▔▔
STR 14  +2   save +2       
  Athletics        +2     ▸
DEX 10  +0   save +0       
  Acrobatics  +0 · Stealth +0
  Sleight of Hand +0       
CON 16  +3   save +3       
INT  8  −1   save −1       
  Arcana −1 · History −1   
  Investigation −1 · Nature −1
WIS 18  +4   save ●+7      
  Insight ●+7 · Medicine ●+7
  Perception ◐+5 (passive 15)
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
Ability row: tap the score = ability check, tap "save" = saving throw. Skills grouped under
their ability (the 2024 paper-sheet grouping new players find readable). `●` proficient,
`◐` half, `◆` expertise, nothing = none. Long-press any = adv/dis chooser.

## S5 Spells

```
▔▔ BACK ▔▔ SPELLS ▔▔ PREP ▔▔
WIS · DC 15 · ATK +7       
1st ●●●●   2nd ●●●  3rd ●●○
CANTRIPS                    
  Sacred Flame            ▸
1ST                         
  Bless (C)  (D)          ▸
  Cure Wounds (D)         ▸
2ND                         
  Spiritual Weapon        ▸
3RD                         
  Spirit Guardians (C)    ▸
▁▁▁▁▁ CAST ▁▁▁ +SLOT ▁▁▁▁▁▁
```
Pips: tap a filled pip to spend, tap the level label to restore one (mis-tap recovery).
`(D)` = always prepared (domain spell). `PREP` toggles prepare mode (rows become checkboxes; the
prepared count vs allowed is shown). Tap a spell → S10 reader with a `CAST` bottom action.
Concentration: casting a (C) spell sets the concentration line on S1 and offers to drop the
previous one.

## S6 Features & resources

```
▔▔ BACK ▔▔ FEATURES ▔▔ + ▔▔▔
Channel Divinity   ●○  short▸
Lay on Hands   5/30    long ▸
Wild Shape     ●●      short▸
Rage           ●●●     long ▸
Second Wind    ●       short▸
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
One row per counter: name · pips (≤ 8) or `value/max` (> 8) · reset rule. Tap = spend one;
wheel while a row is focused = ±1; tap the name = SRD feature text if `featureKey` is set.
`+` adds a custom counter (name via editor, max and reset via wheel) — this is how non-SRD
features (manoeuvres, ki from a homebrew subclass) are tracked without their text.

## S7 Conditions

```
▔▔ BACK ▔▔ CONDITIONS ▔▔▔▔▔▔
Concentrating: Bless  [DROP]
Exhaustion  ○●○○○○  1       
  disadv. on ability checks  
□ Blinded     ■ Poisoned    
□ Charmed     □ Prone       
□ Deafened    □ Restrained  
□ Frightened  □ Stunned     
□ Grappled    □ Unconscious 
□ Incapacitated □ Paralyzed 
□ Invisible   □ Petrified   
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
Tap toggles; tap the name text opens the SRD condition text (S10). Exhaustion steps with
the wheel and prints the level's effect line under it. Active conditions surface on S1.

## S8 Rest

```
▔▔ BACK ▔▔▔▔ REST ▔▔▔▔▔▔▔▔▔
SHORT REST                  
Hit dice  d8  ●●●●○         
  spend one   [ROLL] [AVG]  
  +4 HP  (31 → 35 / 43)     
  resets: Channel Divinity  
                            
LONG REST                 ▸ 
  full HP · 2 hit dice · all 
  slots · exhaustion −1      
▁▁▁▁▁▁▁▁▁▁ DONE ▁▁▁▁▁▁▁▁▁▁▁
```
Short rest: spend hit dice one at a time (roll or take the average), then `DONE` applies the
short-rest resets and shows what changed. Long rest → confirm screen → summary. The tool
never clears conditions on a rest (the GM decides) and warns if HP is 0 ("must have at
least 1 HP to benefit").

## S9 Gear & coin

```
▔▔ BACK ▔▔ GEAR ▔▔▔▔ + ▔▔▔▔
gp 47   sp 12   cp 0       ▸
■ Chain Mail       AC 16   ▸
■ Shield           +2      ▸
■ Mace                     ▸
□ Holy Symbol              
◇ Amulet of Health  attune ▸
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
`■` equipped, `◇`/`◆` attuned toggle (max 3, the fourth tap is refused with a line of
text). Tap a compendium-backed row → text. Coin row → a pad where the wheel edits the
focused denomination. `+` → search equipment/magic items (editor screen) or "custom name".

## S10 Reader (compendium entry)

```
▔▔ BACK ▔▔ FIREBALL ▔▔ CAST ▔
3rd-level evocation         
1 action · 150 feet · V S M 
Instantaneous               
A bright streak flashes from
your pointing finger to a   
point you choose within     
range … 8d6 fire, DEX save  
for half.                   
At higher levels: +1d6 per  
slot level above 3rd.       
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
`Paragraph` text in a `LightScrollView`; the wheel scrolls. `CAST` appears only when opened
from a character's spell list. The header lines and the link footer are specified below (the
per-kind header table and the cross-link footer, D10).

**Body render — Markdown-lite → `sdk:ui` (D7/D8).** `h1`/`h2` → `Heading`, `h3` →
`Subheading`, `h4`/`h5` → bold `Paragraph`; a leading heading at any level whose text equals
the record's own name is dropped — the top bar already says it (the nine rules chapters open
`# <Name>`, 33 of the 40 rule sections open `## <Name>`). `- `/`* ` bullets become hanging
rows; `N.  ` numbered items keep their number. Inline `**bold**` → weight, `*italic*` → slant. Pipe tables
— both bundle dialects, rows contiguous or blank-line-separated — render as monospace
column-aligned rows: `Detail` when the packed width fits ≈ 38 chars, `Superfine` up to ≈ 48;
wider tables stack instead — a 2-column table becomes a bold run-in paragraph, a 3+-column
table becomes a bold title line plus `Header: cell` lines. Nothing scrolls horizontally.

**Per-kind header line**, below the top bar and above the body:

| Kind | Header |
|---|---|
| spells | "3rd-level evocation" / "Evocation cantrip" (+ " (ritual)"); "1 action · 150 feet · V S M"; a concentration duration reads "Concentration, up to 1 minute"; the M-component line and the classes line are lighten. Body = text, then an "At higher levels." run-in from `higherLevel`. |
| conditions, rule sections, weapon properties, damage types, magic schools | none — body = text |
| rules (chapter) | none — body = text minus its own leading `h1`; footer = its sections in reading order |
| classes | "Hit die d12"; "Saves Str, Con"; lighten humanized proficiencies; casters add "Spellcasting: Int (from level 1)". Body = spellcasting sections (a class with none, e.g. Barbarian, is a legal header-only page). The 20-level class table is deferred to M4. |
| subclasses | lighten flavor line ("Primal Path"); body = text |
| features | "Barbarian 1" / "Berserker 3" (+ a prerequisites line) |
| traits | none — the parent trait (when there is one) is a PART OF footer link |
| subraces | "+1 Cha" ability-bonus line; the race is a RACE footer link |
| races | "Medium · Speed 30 ft."; "+2 Con"; lighten languages |
| backgrounds | lighten skills line; body = feature text + suggested-characteristics tables |
| feats | "Prerequisite: Str 13" |
| equipment | lighten "75 gp · 55 lb."; armor adds its real line, each field as applicable — chain mail "Heavy armor · AC 16 · Str 13 · Stealth disadvantage", scale mail "Medium armor · AC 14 + Dex (max 2) · Stealth disadvantage"; weapons add "Martial melee weapon" / "1d8 slashing" / range, versatile, thrown as applicable. Footer (weapons only): PROPERTIES links. |
| magic items | the headline verbatim, lighten ("Armor (medium or heavy, but not hide), uncommon"); a base item's footer lists its VARIANTS |
| creatures | classic SRD stat-block order: "Small humanoid (goblinoid), neutral evil"; "AC 15 (armor)"; "HP 7 (2d6)"; "Speed 30 ft."; an ability grid as a monospace table (STR..CHA header, one row "8 (-1)" — ASCII hyphen); lighten saves/skills/vulnerabilities/resistances/immunities/senses/languages lines; "CR 1/4 · 50 XP · Prof +2". Body = text + trait/action/reaction/legendary-action run-ins under headings. |
| skills | "Ability: Dexterity" |
| languages | "Exotic · Script: Infernal" + speakers |
| alignments | lighten "CE" |
| proficiencies | a type line, plus lighten classes/races lines when the record names any; no body |

**Cross-link footer (D10).** Underlined `LightText` rows at the end, grouped under a
`SectionHeaderRow` label each — the structural sections first, then a `SEE` section whose rows
are the condition names:

| Source kind | Footer links |
|---|---|
| class | SUBCLASSES + FEATURES |
| subclass | FEATURES |
| race | TRAITS + SUBRACES |
| subrace | TRAITS + RACE |
| trait | its parent trait |
| feature | its parent feature + its class |
| magic item (base) | its VARIANTS |
| weapon | PROPERTIES |
| rules chapter | SECTIONS |
| rule section | its CHAPTER |

Condition links come from a whole-word case-insensitive scan of the rendered prose for the
15 condition names, on exactly these kinds: spells (text + higher-level), conditions
(self-excluded), rule sections, subclasses, features, traits, feats, equipment, magic items,
and creatures (which also union their `conditionImmunities`). The other kinds are not
scanned — the bundle's only condition-word hit among them is lightfoot-halfling's idiomatic
"prone to wanderlust", which must not become a link. First-occurrence order, deduped, capped
at 12. Spell names in prose are not links in M2. Every link row pushes a new S10 reader.

Reader-to-reader cross-links are the one static-depth exception (D5): each link push adds one
screen and BACK pops one, so the chain is bounded by how many links the user actually taps,
not by a fixed number.

## S11 Roll result (transient modal)

```
     ┌───────────────────┐
     │  MACE             │
     │  18   to hit      │
     │  (13 + 5)         │
     │   6   damage      │
     │  (4 + 2) bludg.   │
     └───────────────────┘
```
Shown for 2 s via `LightModalManager` (no hold affordance exists — the last result also
stays inline on the row that produced it); natural 20/1 render the d20 value in `Subtitle`
weight with "CRIT"/"MISS" beneath. Advantage shows both dice, the kept one
underlined.

## S12 New character — "From paper"

A short wizard, each step one screen, all wheel-driven except two names:

1. **Name** (editor, single line, caps).
2. **Class & level** — class list (12 SRD + "Other…" which asks a name and hit die), level
   via wheel; subclass list (SRD or "Other…").
3. **Race** — list (SRD 9 + subraces + "Other…").
4. **Ability scores** — six numbers, wheel per row, default 10. Enter *final* scores.
5. **Saves & skills** — two-column checkboxes; defaults pre-ticked from class/background.
6. **Vitals** — HP max (default = average formula), AC mode (manual value or armor from
   gear), speed (default from race).
7. **Attacks** — add from equipment (weapon list) or "custom" (name + ability + damage dice
   picker); ≤ 12.
8. **Spells** — if the class casts: ability preset; prepared spells from the SRD list
   filtered by class and level; "+ custom spell" (name + level).
9. **Done** → S1. Class-table counters (Channel Divinity, Rage, …) and hit dice are seeded
   automatically from the compendium.

Every step can be revisited from `EDIT` on S1. Nothing here is required to be complete —
a half-transcribed character is still useful at the table.

## S13 Compendium hub

```
▔▔ BACK ▔ COMPENDIUM ▔ FIND ▔
SPELLS (319)              ▸
CONDITIONS (15)           ▸
RULES                     ▸
CLASSES & FEATURES        ▸
RACES                     ▸
BACKGROUNDS & FEATS       ▸
EQUIPMENT (237)           ▸
MAGIC ITEMS (362)         ▸
CREATURES (334)           ▸
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
`FIND` (top-bar right action) opens the editor S13.3 and returns S13.4, a bounded result list
(≤ 50) across kinds, name matches first, then FTS4 body matches — see S13.3/S13.4. SPELLS pushes S13.2
(spells by level, its own wheel job); every other row pushes S13.1, the one generic group-list
screen, seeded with that row's kind(s) — see S13.1 for the per-group content table. Counts
come from `countsByKind()` at runtime, never hardcoded (D13); RULES, CLASSES & FEATURES,
RACES and BACKGROUNDS & FEATS stay uncounted because each mixes more than one kind. The six
LOOKUP kinds (skills, languages, damage types, magic schools, alignments, proficiencies) have
no hub row — reached only via FIND and reader cross-links.

## S13.1 Compendium group list (generic)

```
▔ BACK ▔ CLASSES & FEATURES
CLASSES                    
Barbarian                 ▸
Bard                      ▸
Cleric                    ▸
SUBCLASSES                 
Path of the Berserker     ▸
College of Lore           ▸
Circle of the Land        ▸
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
One screen definition, reused for every S13 row but SPELLS; the top bar centre is that row's
own label. `LightLazyScrollView`, every row — a section header included — exactly 2.5 grid
units tall (D1); a header row is `LightText(Detail)`, lighten, upper-cased by the row itself, and
is not `lightClickable` (the same shape as S5's level groups). Not letter-spaced: `LightText`
takes no `letterSpacing` and only the `fine`/`button` styles define one (`LightTheme.kt:124-136`),
both larger than `Detail` — upper case and `lighten` carry the header on their own. The wheel scrolls; a press is
consumed as a no-op (no primary action here — see the wheel-contract amendment above). No
`FIND` on this screen — search stays on the hub and on S13.4. Tap a row → S10. Static depth
S0 → S13 → S13.1 → S10 = 4 (deeper only via a reader cross-link, D5).

Per-group content — the load-bearing shape of this screen:

| Group | Rows | Right detail |
|---|---|---|
| CONDITIONS | flat, `listInOrder(CONDITIONS, 15)` — bounded at 15 | none |
| RULES | 9 chapter header rows, `listInOrder(RULES, 9)` — bounded at 9, non-tappable (six chapters' text is only their `# Title`; equipment, spellcasting and using-ability-scores carry real intro prose, reached via FIND or a section's CHAPTER link) — each followed by its sections, `children(RULE_SECTIONS, chapterKey)` — finite by the bundle; 49 rows total. A chapter's own reader page is reached from FIND or from one of its sections' CHAPTER links (D10), not from this list. | none |
| CLASSES & FEATURES | section CLASSES, `listInOrder(CLASSES, 12)` — bounded at 12 — then section SUBCLASSES, `listByName(SUBCLASSES, 12)` — bounded at 12; features have no list row here — reached from a class or subclass reader's footer and from FIND (63 duplicate "Ability Score Improvement" rows would be noise) | none |
| RACES | section RACES, `listInOrder(RACES, 9)` — bounded at 9 — then section SUBRACES, `listByName(SUBRACES, 4)` — bounded at 4; traits via race reader footers + FIND | none |
| BACKGROUNDS & FEATS | two one-row sections, `listInOrder(BACKGROUNDS, 1)` + `listInOrder(FEATS, 1)`; SRD 5.1 ships exactly one of each | none |
| EQUIPMENT | one section per `categoriesOf(EQUIPMENT)` category (humanized label), rows from `byCategory` — finite by the bundle — then a WEAPON PROPERTIES section, `listByName(WEAPON_PROPERTIES, 12)` — bounded at 12 (the bundle ships 11); ≈ 250 rows total (the hub's 237 counts equipment records only — the category headers and the 11 weapon-property rows are the difference, not a discrepancy). Sections run **weapon, armor, adventuring gear, tools, mounts and vehicles**, not `categoriesOf`'s alphabetical order: alphabetical opens the screen on 116 rows of adventuring gear and puts weapons at row 204, and weapons and armor are what get looked up mid-turn. A category the bundle grows later keeps its alphabetical place after these five. | none |
| MAGIC ITEMS | flat, `bySubcategory(MAGIC_ITEMS, "base")` — finite by the bundle, 239 rows; the 123 variants are reached from each base item's reader footer and FIND — 239 + 123 = 362, the hub's count (which counts items, not rows opened by this list, D13) | rarity |
| CREATURES | flat, `listByName(CREATURES, 334)` — bounded at 334, the whole bundle | CR as a fraction ("1/8", "1/2", "5") |

Right-detail text is `LightText(Detail)`, lighten, right-aligned in the same row as the name.

## S13.2 Spells by level

```
▔▔ BACK ▔▔▔▔ SPELLS ▔▔▔▔▔▔▔
          LEVEL 3          
◂       LEVEL 3 · 42      ▸
Counterspell           Abj▸
Fear                  Illu▸
Fireball               Evo▸
Fly                   Tran▸
Gaseous Form          Tran▸
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
Top-bar centre is `LightTopBarCenter.TwoLineDetail("SPELLS", "CANTRIPS")` at level 0 or
`TwoLineDetail("SPELLS", "LEVEL n")` at level 1–9 — drawn above as the bar plus a centred
second line, both lines `Detail` weight (the SDK's own rendering, not the usual `Fine` title —
verified in `LightTopBar.kt`). The first content row is a tappable stepper, `◂ LEVEL n · count ▸` — the
emulator emits no wheel codes, so the arrows are the tap fallback for the wheel job below.
Rows under it are `spellsByLevel(level)` (finite by the bundle; the largest level, 2nd, is 54
rows), right detail = the school abbreviated to at most four characters as the wireframe draws
it — `Abj` `Conj` `Div` `Ench` `Evo` `Illu` `Nec` `Tran` (the full names ellipsize 37 of the 319
spell names, the abbreviations 6); tap one → S10, with no `CAST` action (opened outside a
character's spell list — S10's existing rule). Touch drag scrolls the row list itself, since
the wheel's turns are claimed by the level stepper on this screen.

| Wheel event | Job |
|---|---|
| 317 (toward the top of the phone) | level + 1, clamped at 9 — no wrap |
| 318 (toward the bottom) | level − 1, clamped at 0 — no wrap |
| 319 (press) | consumed, no-op — no primary action on this screen |

Spell counts by level, 0–9: 24, 49, 54, 42, 31, 37, 31, 20, 16, 15 — sums to 319, the SPELLS
count on the S13 hub. Static depth S0 → S13 → S13.2 → S10 = 4.

## S13.3 Find (editor)

```
┌─────────────────────────┐
│                         │
│  Search…                │
│                         │
└─────────────────────────┘
```
The SDK's own full-screen `LightTextInputEditor(singleLine = true)` — the box stands in for
the SDK's own chrome; the tool draws no top or bottom bar here. Return submits the query;
BACK (or hardware back) cancels with no result.
Reached from the hub's `FIND` or from S13.4's `FIND` (re-seeded with the current query).

## S13.4 Search results

```
▔ BACK ▔ RESULTS (4) ▔ FIND
SPELLS                     
Fireball                  ▸
Fire Bolt                 ▸
CONDITIONS                 
Frightened                ▸
CLASSES & FEATURES         
Rage           Barbarian 1▸
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
`search(query)` (`CompendiumReader`) — name matches first, then FTS4 body matches, ≤ 50 rows
(`Search.LIMIT`). Non-tappable kind header rows (the same shape as S13.1) group the hits in
S13 order (SPELLS, CONDITIONS, RULES, CLASSES & FEATURES, RACES, BACKGROUNDS & FEATS,
EQUIPMENT, MAGIC ITEMS, CREATURES); a LOOKUP kind sorts after those nine, in `Kind` declaration
order (it has no hub row of its own). A row may carry a right-aligned lighten Detail
disambiguator (D6) — a feature row reads "Barbarian 1" (its class and level; `classKey` is on
the `CompendiumRef` projection — a projection column, not a `RecordRow` column, so it needs
no `SCHEMA_VERSION` bump). Empty result is one lighten `Copy` line, "No
matches." `FIND` re-opens the editor seeded with the current query; the editor pops back to
this same screen instance, which re-queries in place — it never pushes a second results
screen. From the hub the sequence is editor-pops-then-results-pushes (S0 → S13 → S13.3 pops to
S0 → S13, which then pushes S0 → S13 → S13.4); from this screen the sequence briefly reaches
S0 → S13 → S13.4 → S13.3 before popping back to S0 → S13 → S13.4 to re-query — depth 4 is the
deepest the compendium branch reaches outside a reader chain (D5). Tap a row → S10.

## S14 Journal

```
▔▔ BACK ▔▔ JOURNAL ▔▔ + ▔▔▔▔
SESSION 12 · 28 Aug        ▸
SESSION 11 · 21 Aug        ▸
SESSION 10 · 14 Aug        ▸
                            
PEOPLE (14)               ▸
PLACES (6)                ▸
QUESTS (3 open)           ▸
LOOT · party 412 gp       ▸
                            
▁▁▁▁▁▁▁▁▁ EXPORT ▁▁▁▁▁▁▁▁▁▁
```
Session screen = entries newest-last (wheel scrolls forward in time), one line each:
`@ Merrick · Salmon's Spyglass`, `→ Waterdeep`, `? Find the missing caravan`, `◆ 3 potions`.
`+` starts the capture flow: **kind** (7-row list) → **roster** for that kind, recency-first,
"+ new" at the top (the only keyboard trip, name only) → optional "at …"/"re …" link chips →
optional one-line text → saved with a timestamp. Rosters: tap a person/place/quest to see
its derived mention list (every entry that links it). Quests show open by default; done/
failed sink to the bottom struck-through. `EXPORT` renders the journal as text pages
(v1) and QR pages (v1.x) for the laptop clean-up pass.

## S15 Dice

```
▔▔ BACK ▔▔▔▔ DICE ▔▔▔ ADV ▔▔
   d4  d6  d8  d10 d12 [d20] d100
   count  1      mod  +5    
                            
         [  ROLL  ]         
                            
   18  (13+5)   d20+5       
   11  (6+5)    d20+5       
    9  (3+2+4)  2d6+4       
▁▁▁▁▁▁▁▁▁▁ CLEAR ▁▁▁▁▁▁▁▁▁▁
```
Wheel changes the focused number (count or modifier); wheel press rolls. History is the
last ten rolls of this session, cleared on relaunch — no statistics, no streaks.

## S16 About

```
▔▔ BACK ▔▔▔▔ ABOUT ▔▔▔▔▔▔▔▔
ATTRIBUTION                
Grimoire is 5E compatible. 
It bundles rules text from 
the System Reference       
Document 5.1 …             
                           
Grimoire 0.1.0 (1)         
dev.tyler.grimoire         
5E compatible              
github.com/tyleryancey/    
light-grimoire             
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
`LightScrollView`; the wheel scrolls. `ATTRIBUTION.md` (one `h1`, five paragraphs) renders
verbatim under S10's Markdown-lite rules, its `h1` kept this time (there is no record name to
match it against). Below it, four plain lines: version, tool id, "5E compatible", the
repository URL as plain text. Nothing else; empty bottom bar.

---

## Navigation map

```
S0 Home ─┬─ S1 Sheet ─┬─ S2 Turn ── S11 result / S10 reader
         │            ├─ S3 HP
         │            ├─ S4 Checks ── S11
         │            ├─ S5 Spells ── S10 (+CAST)
         │            ├─ S6 Features ── S10
         │            ├─ S7 Conditions ── S10
         │            ├─ S8 Rest ── confirm
         │            ├─ S9 Gear ── S10 / coin pad
         │            └─ EDIT → S12 steps
         ├─ S13 Compendium ─┬─ S13.1 group lists ── S10 reader
         │                  ├─ S13.2 spells by level ── S10 reader
         │                  └─ FIND ── S13.3 editor ── S13.4 results ── S10 reader
         ├─ S14 Journal ── session ── capture flow / rosters
         ├─ S15 Dice ── S11
         └─ S16 About
```
Static navigation depth never exceeds four screens; reader-to-reader cross-links are the
sole exception — the chain is bounded by the user's taps and BACK unwinds it. Every screen
rebuilds from Room/DataStore on relaunch (`onScreenShow` reloads; nothing lives only in a
view model).
