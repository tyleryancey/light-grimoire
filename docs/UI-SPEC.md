# Grimoire — UI specification (screen by screen)

Canvas: LP3 3.92" 1080×1240, `LightGrid` 27 × 31 units (1 unit ≈ 40 px ≈ 15.2 dp). Top bar
3 units, bottom bar 4 units → 24 content units ≈ 9–10 rows of `Copy` text or ~16 rows of
`Detail`. Type scale is multiplied by `screenHeightDp/600` (≈ 0.79 on the LP3), so `Copy`
renders ≈ 24 sp, `Paragraph` ≈ 19 sp, `Detail` ≈ 16 sp, `Subtitle` ≈ 41 sp, `Title` ≈ 90 sp.

Wireframes below are 27 characters wide = one grid unit per character. `▔` top bar, `▁`
bottom bar, `●`/`○` filled/hollow pip, `▸` row that navigates, `■`/`□` toggle on/off.

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
`true` only when the screen actually used the event, so unhandled keys still reach LightOS.
**Verify on hardware in M0** — the emulator does not emit 317–319.

Global rules: no colour literals; state by weight/glyph; every list bounded; BACK is always
the top bar's left button (the examples' convention — the SDK draws no back bar); the top
bar centre is the screen name in `Fine`; the bottom bar holds screen actions only.

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
`Paragraph` text in a `LightScrollView`; the wheel scrolls. Header line = the typed fields
(level/school, time/range/components, duration). Cross-references (a condition named in
the text) are rendered as underlined `LightText` rows at the end: "See: Prone".
`CAST` appears only when opened from a character's spell list.

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

## S13 Compendium

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
`FIND` opens the editor (single line) and returns to a bounded result list (≤ 50) across
kinds, name matches first (Room FTS4). Spells list filters by level with the wheel; rules
are the SRD's six chapters with their sections.

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

Attribution text from `assets/legal/ATTRIBUTION.md` (rendered verbatim), version, tool id,
"5E compatible", repository URL as plain text. Nothing else.

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
         ├─ S13 Compendium ── lists ── S10
         ├─ S14 Journal ── session ── capture flow / rosters
         ├─ S15 Dice ── S11
         └─ S16 About
```
Depth never exceeds four screens. Every screen rebuilds from Room/DataStore on relaunch
(`onScreenShow` reloads; nothing lives only in a view model).
