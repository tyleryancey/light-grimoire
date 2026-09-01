# Grimoire — UI specification (screen by screen)

Canvas: LP3 3.92" 1080×1240, `LightGrid` 27 × 31 units (1 unit ≈ 40 px ≈ 15.2 dp). Top bar
3 units, bottom bar 4 units plus its own 1-unit top margin (`LightBottomBar.kt:18-20`) →
23 content units ≈ 9 full rows of `Copy` text or 15 rows of `Detail`. Type scale is multiplied by
`screenHeightDp/600` (≈ 0.79 on the LP3), so `Copy` renders ≈ 24 sp, `Paragraph` ≈ 19 sp,
`Detail` ≈ 16 sp, `Subtitle` ≈ 41 sp, `Title` ≈ 90 sp.

**One line box, in grid units, is `fontSize × lineHeight × 31/600`** — the screen-height scale
and the pixel density cancel, so the figure is exact for any LP3 (`LightTheme.kt:85-136`):

| Variant | sp × lineHeight | Units |
|---|---|---|
| `Heading` | 38 × 1.35 | 2.65 |
| `Copy` | 30 × 1.50 | 2.33 |
| `Subheading` | 30 × 1.25 | 1.94 |
| `Paragraph` | 24.5 × 1.25 | 1.58 |
| `Detail` | 20 × 1.45 | 1.50 |
| `Fine` | 25 × 1.15 | 1.49 |

Every vertical budget below is derived from this table; the tool's own navigating row is a
fixed 2.5 units (`ROW_HEIGHT_GRID_UNITS`, `ui/common/Rows.kt:19`), taller than any single line
box because it carries the tap target.

Wireframes below are 27 characters wide = one grid unit per character. `▔` top bar, `▁`
bottom bar, `●`/`○` filled/hollow pip, `▸` row that navigates, `◂` its mirror (S13.2's level
stepper), `■`/`□` toggle on/off, `· · ·` a pinned-header boundary (S1) — not drawn on device,
unlike every other inline `·` in these frames, which is a plain mid-line separator
("Cleric 5 · Hill Dwarf").

One character ≈ one grid unit only holds at `Copy` weight, the size these frames are drawn at.
Several screens below draw compact stat, pip and toggle lines at `Detail`, which is 20 sp
against `Copy`'s 30, so it packs exactly **1.5×** as many characters per unit — the S1 stat line
`AC 18  INIT +0  SPD 25  PB +3` is 29 characters, wider than the 27-column frame, but ≈ 19 of
S1's 25 usable units (27 minus the 1-unit side margin `Rows.kt` gives every row); ≈ 37
characters of `Detail` fit on one such line. `Fine` (25 sp) holds 1.2 characters per unit the
same way. Read a wireframe as a *layout*, not a character-for-character render, wherever it
runs past 27 columns.

Component mapping (all `sdk:ui`, verified 29 Aug 2026 — see `.claude/skills/lp3-ui-patterns`):

| Need | Build from |
|---|---|
| Row list | `LightScrollView` (mixed heights) or `LightLazyScrollView` (uniform rows) + `LightText` + `lightClickable` |
| Toggle / pip | `LightIcon(TOGGLE_STATE_ON/OFF)` or `CIRCLE`/`STAR_OUTLINE` glyph in a clickable row; never a Material switch |
| Number pad | `LightText(Subtitle)` value + two glyph buttons (`UP`/`DOWN` or `ADD`/`DOWN`) + wheel via `LightKeyHandler` |
| Text entry | `LightTextField` (display) → `navigateTo(EditorScreen)` → `LightTextInputEditor(singleLine=true, initialCaps=true)` — `initialCaps=false` for a search query, which is not a name |
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

**Every screen the tool draws owns the wheel**, whether or not it has a use for it: turns perform
the screen's stated job — scroll, a level step, a verb's ±1, a focused counter's ±1, an
exhaustion step, a coin denomination's ±1, depending on the screen (each S0–S9 section below
states its own) — and the press is consumed
as a no-op wherever the screen defines no primary action — an unconsumed wheel event is forwarded
to LightOS, which foregrounds itself and relaunches the tool, a destructive context switch
mid-reading. That includes S0 Home — whose wheel scrolls the character list once the
compendium is Ready, but which before Ready has neither a list to scroll nor a primary action,
and so must consume **both** halves as no-ops for the ≈ 2.5 s the import runs: a turn that
reached LightOS there would restart Home on the one screen a first-time player watches all the
way through — and S13.3's editor, which has no view model and so
consumes on the screen itself. Consuming `onKeyDown` alone is not enough:
`LightKeyHandler` defaults `onKeyUp` and `onKeyMultiple` to false, so the *release* half of every
detent would relaunch the tool on its own — every wheel consumer forwards all three to
`WheelHandler.consumes`. Volume (24/25) and camera (80/27) keys are never consumed and still
reach LightOS.

Global rules: no colour literals; state by weight/glyph; every list bounded; BACK is always
the top bar's left button (the examples' convention — the SDK draws no back bar); the top
bar centre is the screen name in `Fine`, except S13.2's two-line level stepper (`Detail`,
the SDK's own `LightTopBarCenter.TwoLineDetail` weight); the bottom bar holds screen actions
only.

**Choosers, confirms and search never sit under what they lead to.** A long-press chooser, a
confirm screen and the search editor are each a typed-result screen (`SimpleLightScreen<R>`)
that pops back to its caller before anything happens with the result — the caller reads the
result and pushes whatever comes next. This is the shape S13.3's editor already uses to reach
S13.4 (the editor pops to the hub, which then pushes results), applied wherever else a screen
would otherwise sit permanently under two more: S2's long-press chooser pops before S2 pushes
S10 for "Read"; S8's long-rest confirm pops before S8 pushes the rest summary; S9's `+` search
editor pops before S9 pushes the equipment/magic-item picker. Static navigation depth (the
closing Navigation map's rule; D5's reader-cross-link chain is the sole exception) stays ≤ 4 at
every one of these joins — none of them needed a new exception, only the pattern M2 already
established.

---

## S0 Home

```
▔▔▔▔▔▔▔▔ GRIMOIRE ▔▔▔▔▔▔▔▔▔▔
Brother Aldric            ▸
  Cleric 5 · Hill Dwarf
Vessa Quickfinger         ▸
  Rogue 3 · Lightfoot Halfling
                            
COMPENDIUM                ▸
JOURNAL                   ▸
DICE                      ▸
                            
▁▁▁▁▁▁ NEW ▁▁▁ ABOUT ▁▁▁▁▁▁
```
Characters first (≤ 6, two lines each — name, then `summary` verbatim from the character's own
`race.name`/class/level, e.g. "Lightfoot Halfling" rather than a shortened "Halfling" — the
player transcribed it, so it is shown as transcribed), then the three utilities, one line each.
Six two-line character rows (4 units each) plus a gap plus three one-line utility rows is 32.5
units against the 23 available, and mixes row heights `LightLazyScrollView`'s uniform-row
contract cannot draw — so S0 is a `LightScrollView`, not a lazy list; mixed heights are exactly
what it is for. Tap a character → S1. `NEW` → S12. If exactly one character exists the tool
still opens here (predictable, one extra tap).

Wheel scrolls the list, one character row per detent, once the store is Ready; wheel press has
no primary action anywhere on this screen and is consumed as a no-op. **Before** Ready, while
`Preparing the rules…` is up, there is no list and no action, so the turn is consumed as a
no-op too — both halves, for the whole import.

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
▔▔ BACK ▔ BROTHER… ▔ EDIT ▔
Cleric 5 · Hill Dwarf     ★
AC 18  INIT +0  SPD 25  PB +3
· · · · · · · · · · · · ·
HP  31 / 43   TEMP 0      ▸
●●●● ●●● ●○   slots     ▸
TURN                      ▸
CHECKS & SAVES            ▸
SPELLS                    ▸
CONDITIONS  · Bless (C)   ▸
REST                      ▸
FEATURES & RESOURCES      ▸
GEAR & COIN               ▸
▁▁▁▁▁▁▁▁▁▁ DICE ▁▁▁▁▁▁▁▁▁▁▁
```
`★` = inspiration toggle (filled when held; 2014 term), pinned beside the identity line. The
top **4 units** are a pinned header, drawn once and never scrolled: a 0.5-unit pad, the
identity line (`Detail`: class/level · race, star trailing), the stat line (`Detail`:
AC/INIT/SPD/PB), and a 0.5-unit pad separating the header from the list beneath it. Two
`Detail` line boxes are 1.50 units each by the preamble's table, so 0.5 + 1.5 + 1.5 + 0.5 = 4.0
exactly; the `· · ·` row in the frame *is* that lower pad — it marks where the header ends and
is not itself drawn on device. Below it is a `LightLazyScrollView` of the nine navigating rows,
each the uniform 2.5-unit `NavRow` height. The header has to be pinned and `Detail`-compact
because 9 × 2.5 = 22.5 of the 23 content units leaves 0.5 for everything else: a header of two
`Copy` lines is 4.65 units, and nine rows plus that header is 27.15 units against 23. Nothing
shrinks the rows — 2.5 units is the tool's one row height, and a `Copy` line box alone is 2.33.

The top bar centre shows the whole character name, **uppercased and untruncated by this
tool** — names run to 40 characters (`character.schema.json`) and `LightTopBarCenter`'s centre
is capped at 18 grid units in `Fine` (`CENTER_MAX_WIDTH_UNITS`, `LightTopBar.kt:19`), which the
SDK then ellipsizes itself (`widthIn(max = …)` at `LightTopBar.kt:105`, `maxLines = 1` +
`TextOverflow.Ellipsis` at `:114-115`). Every shortening heuristic is wrong on some real name
("Brother Aldric" wants its last word kept, "Vessa Quickfinger" its first), so the tool passes
the full uppercased name and lets the SDK's ellipsis have the last word.

The `BROTHER…` above is the **wireframe's** limit, not the device's: once `BACK` and `EDIT` are
drawn, this 27-column frame leaves the centre about eight columns. The real bar is wider — at
`Fine`'s 1.2 characters per unit (25 sp against `Copy`'s 30, the preamble's conversion), 18
units hold 18 × 30/25 ≈ **21 characters**. So the fixture this frame draws, "Brother Aldric"
(14), renders whole on device and never ellipsizes; the longest name in `fixtures/characters/`,
"Ser Maelis of the Pact" (22), is the one that actually meets the SDK's ellipsis, and a
schema-maximum 40-character name loses roughly half itself. Twenty-one characters is far more
than this frame can draw and far less than the schema allows — which is exactly why the tool
does not try to choose the truncation point.

Nine rows, one turn of the wheel: 19 of the 23 content units go to the list (23 − 4 for the
pinned header), and 19 / 2.5 = 7.6 rows are visible without scrolling — rows 1–7 (`HP` through
`REST`) are visible on open, row 8 shows as a part-row that tells the player the list scrolls;
`FEATURES & RESOURCES` and `GEAR & COIN` arrive after one wheel turn or a touch drag. The seven
visible rows are exactly the ones a one-tap contract names: `HP` (HP change, death save),
`slots` (the fastest look at what is castable), `TURN` (attack+damage, in-combat cast),
`SPELLS` (cast), `CONDITIONS` (condition toggle) and `REST` (rest start) — plus
`CHECKS & SAVES`, which no contract names but which `docs/PRD.md:43-46` ranks above
`FEATURES & RESOURCES` in per-session frequency (5–15 skill checks and 3–10 saves a session,
ranked separately there, against 3–10 feature-counter spends; the same line puts inventory
out-of-session), so it takes the seventh visible slot instead of them. "Everything is one tap
away" now means: seven of nine rows, and every one-tap-contract action, in one tap; the
remaining two rows are one wheel turn (or touch drag) plus one tap.

**Owed to Tyler — the row order is a substantive change, not just a layout one.** Fitting nine
rows into 7.6 visible slots forced `REST` up out of row 9 (below the fold, which would have
broken "rest start" in the one-tap contract) and `FEATURES & RESOURCES` / `GEAR & COIN` down to
rows 8–9. The frequency argument above is the reason, but re-ranking the hub is an information-
architecture decision this repair pass is proposing, not one it was handed; it wants explicit
ratification before S1 is built.

HP row shows *bloodied* by rendering the numbers **bold**, via a new small helper,
`EmphasisText` (`ui/common`) — not `Subheading`: `Subheading` and `Copy` are both 30 sp
`FontWeight.Normal` (`LightTheme.kt:91-102`), differing only by 0.9 sp of tracking and a
tighter line box (1.25 against 1.50, i.e. 1.94 units against 2.33) — neither of which reads as
"heavier type" (PRD principle 4) — and `LightText` exposes no weight parameter at all
(`LightText.kt:75-87`). `EmphasisText` lifts the `BasicText` + `buildAnnotatedString` +
`SpanStyle(fontWeight = FontWeight.Bold)` path `ui/common/MarkdownBlocks.kt`'s `InlineSpans`
already uses and M2 device-verified, for a single bold run outside prose. (S3's `DEAD` label
has no such problem: `Heading` is 38 sp, genuinely larger than `Copy`/`Subheading`'s shared
30 sp — see S3.)

The slot row is a compact pip strip (levels 1–3 shown; deeper levels on S5). Conditions row
lists active conditions and the concentration spell with "(C)". Wheel scrolls the list, three
rows per detent (`WheelScrollEffect`); wheel press has no primary action here and is consumed
as a no-op — every row navigates on tap, none carries a wheel-selected focus the way S6's
counters do.

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

Wheel scrolls the row list (attacks, spells and flagged counters together can exceed the
visible area); wheel press has no primary action and is consumed as a no-op — rolling needs a
specific row's tap, not a generic press.

## S3 HP pad

Four states — **UP**, **DYING**, **STABLE**, **DEAD** — the same top and bottom bar throughout;
only the middle changes. The top bar's right action is `UNDO`, not `DEATH`: the swap into the
death states at 0 HP is automatic, so a button to reach them would do nothing a hit point does
not already do — see `UNDO` at the end of this section.

**UP** — `current > 0`:
```
▔▔ BACK ▔▔▔▔ HP ▔▔ UNDO ▔▔▔
                            
        31 / 43            
        temp 0             
                            
   −10    −5    −1         
   +10    +5    +1         
                            
 DAMAGE   HEAL    TEMP     
 [■]      [□]     [□]      
                            
▁▁▁▁▁▁▁▁▁▁ REST ▁▁▁▁▁▁▁▁▁▁▁
```
Wheel press: no primary action, consumed as a no-op.

At 0 HP the screen inserts a death-save panel **above** the pad — it does not replace it, so
`HEAL +1`, the only way a downed character gets back up, stays reachable at 0 HP. A death panel
that swapped the pad out would take that control away at exactly the moment it matters most.

**DYING** — `current == 0`, not stable, not dead:
```
▔▔ BACK ▔▔▔▔ HP ▔▔ UNDO ▔▔▔
        0 / 43   DOWN       
 success ○ ○ ○  failure ●○○ 
      [ ROLL DEATH SAVE ]   
   −10    −5    −1         
   +10    +5    +1         
                            
 DAMAGE   HEAL    TEMP     
 [■]      [□]     [□]      
▁▁▁▁▁▁▁▁▁▁ REST ▁▁▁▁▁▁▁▁▁▁▁
```
Two ways to record a save, both reaching `Ledger.deathSave`: `[ ROLL DEATH SAVE ]` rolls the
app's own d20 and handles natural 1 and natural 20 automatically — a 1 is two failures; a 20
regains 1 HP and clears the saves outright (`Ledger.kt:123-126` sets `hp.damage = max − 1` and
`NO_SAVES`), which returns the character to **UP** at 1 HP, not to STABLE. Or tap a hollow
success or failure pip directly — the manual path for a player who already rolled a physical
d20 and just wants to log the outcome.

A pip tap dispatches `Event.DeathSave` with a plain, non-critical value: **success = 10,
failure = 9**. Those two are the whole decision, and they need no engine work — `Ledger.deathSave`
already branches `d20 >= 10` → success, `else` → failure (`Ledger.kt:128-129`), and 10 and 9 are
the two values nearest the threshold that are neither a natural 1 nor a natural 20. Natural 1
and natural 20 therefore stay reachable only through `ROLL`, never through a pip tap, by
construction. Wheel press triggers `ROLL DEATH SAVE` — the Dice screen's "wheel press rolls"
convention, reused here.

**STABLE** — `deathSaves.stable`:
```
▔▔ BACK ▔▔▔▔ HP ▔▔ UNDO ▔▔▔
        0 / 43   STABLE     
 success ○ ○ ○  failure ○○○ 
   Stable. No further saves.
   −10    −5    −1         
   +10    +5    +1         
                            
 DAMAGE   HEAL    TEMP     
 [■]      [□]     [□]      
▁▁▁▁▁▁▁▁▁▁ REST ▁▁▁▁▁▁▁▁▁▁▁
```
`Ledger.deathSave` zeroes both counts on stabilizing, so both rows draw hollow — there is
nothing left to show filled. No `[ ROLL ]` button: `Ledger.deathSave` returns the character
unchanged once `stable` (`Ledger.kt:120`), so a live-looking roll button here would be a dead
control. **The pips are display-only in this state**, for exactly the same reason — they are
tappable in DYING, but here the same early return would swallow the tap, and six hollow
tappable-looking pips are the dead control the missing roll button was removed to avoid. Wheel
press: no primary action, consumed as a no-op.

**DEAD** — `deathSaves.dead`:
```
▔▔ BACK ▔▔▔▔ HP ▔▔ UNDO ▔▔▔
                            
                            
           DEAD             
        0 / 43              
                            
       [ REVIVE ]           
                            
                            
▁▁▁▁▁▁▁▁▁▁ REST ▁▁▁▁▁▁▁▁▁▁▁
```
No pad, no verb chips, no `±n` buttons: `Ledger.heal` (`Ledger.kt:105`), `Ledger.longRest`
(`:164`) and `Ledger.deathSave` (`:120`) all return the character unchanged once `dead`, so
drawing controls that do nothing would be the same dead-button problem `STABLE` avoids by
dropping its roll button. `DEAD` draws in `Heading` weight — 38 sp and a 2.65-unit line box,
genuinely larger than `Copy`/`Subheading`'s shared 30 sp, so unlike S1's bloodied case this one
already reads as heavier without help.

**One control still reaches a dead character, and it is not on this screen.** The bottom bar
keeps `REST`, and `Ledger.spendHitDie` has no dead guard: `Ledger.kt:148` returns
`out.copy(deathSaves = NO_SAVES)` whenever the die brings HP above 0, which clears `dead` along
with everything else. S8 → `[ROLL]`/`[AVG]` would therefore resurrect a dead character with
none of the deliberation `[ REVIVE ]` is justified by. Two things close it: **S8's hit-dice
controls are disabled whenever `deathSaves.dead`** (stated again in S8), and `spendHitDie` is
**owed a dead guard** pipeline-first (`pipeline/reference/`, a fixture, then `rules/Ledger.kt`)
in the same step as TEMP `−n` below. Until that guard lands the disable is the only thing
holding the door, which is why it is written into both screens rather than one. `REST` stays in
this frame's bottom bar so all four states keep the same bars — the state that changes is the
middle of the screen, never the chrome.

The other two unguarded functions are harmless here and are named so nobody has to re-derive
it: damage dealt to a dead character always takes the at-0-HP branch (`Ledger.kt:87-92`), which
carries `dead` forward rather than clearing it, and `Ledger.temp` only ever raises temporary
HP, which cannot revive anyone. `spendHitDie` is the whole hole.

`[ REVIVE ]` clears `deathSaves` back to its default and leaves HP at 0, so the character
returns to `DYING`, ready for saves again — resurrection is the DM's call at the table, and the
tool only records it. It is **not** a view-model `copy()`: every `deathSaves` mutation in the
engine goes through `Ledger` (damage, heal, deathSave, spendHitDie, longRest), and
`Ledger.kt:12-13`'s view-model carve-out names four rules-free fields — toggling a condition,
inspiration, currency, what is equipped — and no death-save field. `REVIVE` is owed to
`pipeline/reference/` as `Ledger.revive` (`Event.Revive`), a fixture, then Kotlin, the same debt
as TEMP `−n` and the `spendHitDie` guard.

**M3 gap, stated rather than hidden:** until `Ledger.revive` lands, DEAD has no working control.
The only in-app recovery from a mis-tapped third failure is `UNDO`, and `UNDO` survives only the
current visit to S3 (an in-memory snapshot — see the end of this section). Off that path, a
character wrongly marked dead is recovered by editing HP through M4's `EDIT`, which does not
exist yet. That is the strongest argument for landing `Ledger.revive` in the same step as the
other two engine debts, not after the screens.

Wheel: no verb to nudge in this state and nothing to scroll, so **both** halves are consumed as
no-ops — turn and press alike. `REVIVE` is deliberately tap-only.

**Verb chips.** Select what the `±n` buttons and the wheel apply to; each is *signed*, so the
same three buttons cover both directions of a correction:

| Chip | `+n` | `−n` |
|---|---|---|
| DAMAGE | give it back (`Event.Heal(n)`) | take `n` (`Event.Damage(n)`) |
| HEAL | heal (`Event.Heal(n)`) | damage (`Event.Damage(n)`) |
| TEMP | grant `n` temp HP, the 2014 "does not stack" rule (`Event.Temp(n)`, keeps the higher value) | lower temp by `n`, floored at 0 |

TEMP `−n` has **no engine support yet**: `Ledger.temp` only ever raises (`max(current,
amount)`); a lowering path needs a pipeline-first change (`pipeline/reference/`, a fixture, then
`rules/Ledger.kt`) before this direction of the chip ships. This section is written as the
target state.

DAMAGE `+n` and HEAL `−n` are not exact inverses of their own verb, because each reaches the
real `rules/Ledger.kt` function its sign maps to, not a UI-layer approximation: DAMAGE `+n`
calls `heal()`, which clears death saves outright once HP is back above 0 (`NO_SAVES`); HEAL
`−n` calls `damage()`, which at 0 HP *adds a failure* rather than simply subtracting HP. A
mis-tap's own correction can therefore have a death-save side effect at 0 HP.

Wheel = ±1 in the current verb in **UP, DYING and STABLE** — the three states that draw a pad.
DEAD draws no pad and no chips, so there is no verb to nudge: its turn is consumed as a no-op
alongside its press, per that state's paragraph. Touch drag scrolls the screen wherever the
wheel's turns are claimed by the verb nudge, the same shape S13.2's level stepper uses for its
own row list.

`UNDO` reverts the single most recent pad action taken **during this visit to S3** — a snapshot
the view model holds in memory, not a stored character state. It does not survive a screen pop,
a relaunch, or a LightOS modal re-entry (`onScreenShow` reloads from the repository, per the
global rule): tapping `UNDO` with nothing to undo is a consumed no-op. It is deliberately not
scoped to the death states — a mis-tapped `−10` in UP is the same mistake as a mis-tapped third
failure, and one uniform action covers both. Until `Ledger.revive` lands it is also the only
in-app recovery from a mis-tapped third failure, which is why the visit-scoped lifetime above
is a real limit and not a detail.

## S4 Checks & saves

```
▔▔ BACK ▔▔ CHECKS ▔▔ ADV ▔▔
STR 14  +2   save +2
  Athletics +2
DEX 10  +0   save +0
  Acrobatics +0 · Stealth +0
  Sleight of Hand +0
CON 16  +3   save +3
INT  8  −1   save −1
  Arcana −1 · History −1
  Investigation −1 · Nature −1
  Religion ●+2
WIS 18  +4   save ●+7
  Animal Handling +4 · Insight ●+7
  Medicine ●+7 · Survival +4
  Perception ◐+5 (passive 15)
CHA 12  +1   save ●+4
  Deception +1 · Intimidation +1
  Performance +1 · Persuasion +1
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
Ability row: tap the score = ability check, tap "save" = saving throw. Skills grouped under
their ability (the 2024 paper-sheet grouping new players find readable). `●` proficient,
`◐` half, `◆` expertise, nothing = none. Long-press any = adv/dis chooser.

**All six abilities and all eighteen skills are drawn**, in the bundle's own grouping by
`ability` (`compendium/skills.json`): STR 1, DEX 3, CON 0, INT 5, WIS 5, CHA 4. Nothing is
dropped for space — INT's fifth is Religion and WIS's are Animal Handling and Survival, and CHA
gets the four the screen used to omit entirely. Within an ability the skills read
alphabetically, with one deliberate break: Perception takes the last line of the WIS group
rather than its alphabetical place, because it is the one skill that carries a second value
(the passive score) and pairing it with another skill would run the line past what `Detail`
holds.

Every number above is `fixtures/derived.json`'s own output for `cleric-5-life.json` — including
`Religion ●+2` (the fixture is proficient in Religion, so it is *not* the bare INT −1) and
`CHA … save ●+4` (its `saveProficiencies` are `["wis","cha"]`, the 2014 cleric's two).

Six ability rows and eleven skill lines are 17 `Detail` line boxes ≈ 25.5 units against the 23
available, before any row padding — so this screen scrolls from the moment it opens, which it
already does. Nothing is dropped, but not everything is on screen at once: the CHA group is the
part below the fold on open, reached by the same wheel turn (or touch drag) this screen already
documents. F5 asks that every ability, save and skill be *reachable* and rollable, which it is;
it does not ask that all eighteen fit one screenful, which at `Detail` they cannot.

Wheel scrolls (`LightScrollView`, mixed row heights per ability's skill count); wheel press:
no primary action, consumed as a no-op — rolling needs a specific ability, save or skill row,
and this plain scroll list has no wheel-selected focus.

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

Wheel scrolls the spell list (grouped by level; a prepared list can run past the visible area);
wheel press: no primary action, consumed as a no-op — casting is the bottom bar's `CAST`, from
a tapped spell, not a generic press.

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
One row per counter: name · pips (≤ 8) or `value/max` (> 8) · reset rule. The name column is
fixed-width and ellipsizes (`maxLines = 1`, `TextOverflow.Ellipsis` — the same pattern `NavRow`
already uses), rather than reserving room for a full 30-character counter name: a 30-character
name, pips or `value/max`, the reset word and the trailing arrow do not all fit on 27 units.

Tap a row = spend one; tap the name = SRD feature text if `featureKey` is set. The first row
has wheel focus as soon as the screen opens — the wheel is claimed unconditionally, the same
shape S13.2's level stepper uses, never a plain scroll by default — and tapping a different
row's pips moves focus to it; the wheel then nudges *that* counter by ±1. Wheel press also
spends one from the focused counter, the same effect as tapping it. Touch drag scrolls the
list, since the wheel's turns are claimed by the focused row on this screen. `+` adds a custom
counter (name via editor, max and reset via wheel) — this is how non-SRD features (manoeuvres,
ki from a homebrew subclass) are tracked without their text.

## S7 Conditions

```
▔▔ BACK ▔▔ CONDITIONS ▔▔▔▔▔▔
Concentrating: Bless  [DROP]
Exhaustion  ○●○○○○  1       
  Disadvantage on ability checks
□ Blinded       □ Paralyzed 
□ Charmed       □ Petrified 
□ Deafened      ■ Poisoned  
□ Frightened    □ Prone     
□ Grappled      □ Restrained
□ Incapacitated □ Stunned   
□ Invisible     □ Unconscious
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
Tap toggles; tap the name text opens the SRD condition text (S10). The 14 toggles — drawn in
seven two-column rows, which is why ten lines cover this whole screen — are the
bundle's 15 conditions minus Exhaustion, which `character.schema.json`'s own description calls
out as tracked separately and which this screen draws as a stepper instead of a checkbox — PRD
F8's "checklist of 15" is these 14 toggles plus that one stepper, not 15 toggles. Left column
reads the first seven names alphabetically (Blinded … Invisible), right column the remaining
seven (Paralyzed … Unconscious).

**PRD F8's per-condition "one-line rule" is dropped, and that is an amendment owed to
`docs/PRD.md:68`, not something this screen satisfies by another route.** Two facts kill it.
There is no room: a rule line needs the full width, so giving each condition one collapses the
two-column grid into 14 toggle rows plus 14 rule lines — 28 `Detail` lines, 42 units, on a
screen with 23, before the concentration and exhaustion rows are drawn at all. And there is no
source: every `conditions.json` record carries exactly
`key, name, edition, source, license, xref, text`, with no summary field — the shortest text is
Incapacitated's 60 characters and the longest a 620-character Petrified, and Blinded is two
bullet sentences. A one-liner would have to be written by hand, which PRD principle 2 forbids.
What replaces it is the full SRD text one tap away on S10 — more than a line, but the real
rule rather than a paraphrase of it. Restoring a per-row line later means new derived data in
the pipeline (a generated `summary` field), not a UI change.

Ten lines drawn here looks tight against the preamble's "~9 full rows of `Copy`", but every
line on this screen is `Detail`, not `Copy`: at 1.50 units a line box those ten lines are
≈ 15.0 of 23 units. The remaining ≈ 8 units are row padding — the toggles are tap targets, not
bare text lines — not room for more content.

Exhaustion's level 1–6 effect line is the one per-row rule that survives, because it is
derivable verbatim rather than typed: it is the substring of the bundle's own `exhaustion`
record after the " - " in whichever paragraph starts with the current level
("1 - Disadvantage on ability checks", "2 - Speed halved", … "6 - Death"). Nothing is
abbreviated — an abbreviation like "disadv. on ability checks" would be re-typed rules text,
which PRD principle 2 forbids. Level 0 draws no effect line (there is none to show).

Exhaustion steps with the wheel, which this screen claims unconditionally from the moment it
opens (S13.2's shape, not a default scroll that later gets claimed); touch drag scrolls the
toggle grid instead — though at seven toggle rows fixed by the bundle this screen does not
actually overflow today, and the fallback exists for the same reason every wheel-claimed screen
states one. Wheel press: no primary action, consumed as a no-op — toggling a specific condition needs
a specific tap. Active conditions surface on S1.

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
short-rest resets and shows what changed. Long rest → confirm screen → summary; the confirm
screen pops back to this screen before this screen pushes the summary — the pop-then-push
shape the preamble's depth rule describes — so the chain stays S0 → S1 → S8 → summary, depth 4,
never a confirm screen left sitting under it. The tool never clears conditions on a rest (the
GM decides) and warns if HP is 0 ("must have at least 1 HP to benefit").

**`[ROLL]` and `[AVG]` are disabled whenever `deathSaves.dead`.** `Ledger.spendHitDie` is the
one HP-affecting function with no dead guard — `Ledger.kt:148` returns
`out.copy(deathSaves = NO_SAVES)` whenever the die brings HP above 0, clearing `dead` with it —
so a hit die spent here would silently resurrect a dead character, which is S3's `[ REVIVE ]`
without any of its deliberation. The disable is the UI half; the guard itself is owed to
`pipeline/reference/` and a fixture (see S3 DEAD). `LONG REST` needs no such disable —
`Ledger.longRest` already returns the character unchanged once `dead` (`Ledger.kt:164`) — but
it is drawn inert in that state for the same dead-control reason S3 STABLE drops its roll
button.

Wheel scrolls (hit-dice pools are bounded per die size — see `docs/DATA-MODEL.md`'s open bound
question); wheel press: no primary action, consumed as a no-op — spending a die is `[ROLL]` or
`[AVG]`, not a generic press.

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

Turn scrolls the item list (bounded at 60) until the coin row is tapped, which turns the top of
the screen into a coin pad in place and claims the wheel for the focused denomination until
BACK or another tap closes it — touch drag scrolls the item list underneath meanwhile, the same
fallback S13.2 uses for its level stepper. Wheel press: no primary action, consumed as a no-op
in both states — spending is the pad's own ±1, not a press. `+` opens the search editor, which
pops back to this screen before this screen pushes the equipment/magic-item picker — the same
pop-then-push shape the preamble's depth rule describes, keeping S0 → S1 → S9 → picker at
depth 4.

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
│  FIND                   │
│  ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁  │
│  [ keyboard ]           │
└─────────────────────────┘
```
The SDK's own full-screen `LightTextInputEditor(singleLine = true, initialCaps = false)` — the
box stands in for the SDK's own chrome; the tool draws no top or bottom bar here. `FIND` is the
editor's `title`, the only string it takes: there is no placeholder or hint parameter
(`LightTextInputEditor.kt:47-61`), so the empty line carries no prompt text. Return submits the
query (that is what `singleLine` buys — otherwise Return inserts a newline); BACK, hardware back
and an empty submit all cancel with no result delivered, so a caller treats "the result callback
never fired" as cancel. A query under two characters is refused at the caller, because the name
query has a two-character floor while the FTS query has none — one letter would return fifty rows
of whatever sorts first. The editor consumes the wheel like every other compendium screen; it is
the one screen with no view model, so the overrides sit on the screen itself.
Reached from the hub's `FIND` or from S13.4's `FIND` (re-seeded with the current query).

## S13.4 Search results

```
▔ BACK ▔ RESULTS (6) ▔ FIND
SPELLS                     
Fireball                  ▸
Fire Bolt                 ▸
CLASSES & FEATURES         
Rage           Barbarian 1▸
CREATURES                  
Fire Giant                ▸
ALSO MENTIONED             
Burning Hands        Spell▸
Traps         Rule section▸
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```
`search(query)` (`CompendiumReader`) returns **two tiers** (`Search.Results`), drawn one after
the other: the records whose *name* matched, then the records only a body mentions. Blending
them into one kind-grouped list buried the name matches — a name hit sat behind every body
mention of an earlier kind, which put the equipment **Shield** at row 23 of the "shield"
results and the creature **Fire Giant** at row 53 of the "fire" results. Two tiers put every
name match above every mention, whatever kind it belongs to; a record whose name matched is
never repeated as a mention.

**Named tier.** Non-tappable kind header rows (the same shape as S13.1) group the name matches
in S13 order (SPELLS, CONDITIONS, RULES, CLASSES & FEATURES, RACES, BACKGROUNDS & FEATS,
EQUIPMENT, MAGIC ITEMS, CREATURES); a LOOKUP kind sorts after those nine, in `Kind` declaration
order (it has no hub row of its own). A row may carry a right-aligned lighten Detail
disambiguator (D6) — a feature row reads "Barbarian 1" (its class and level; `classKey` is on
the `CompendiumRef` projection — a projection column, not a `RecordRow` column, so it needs
no `SCHEMA_VERSION` bump); every other named row's own name is its answer, and repeating a kind
the header above already names would be noise.

**Mention tier.** One header, `ALSO MENTIONED`, and under it a flat list — no per-kind headers,
because two levels of header over a tail of loose body matches read badly on 27 units. Each
mention row instead carries its kind as the right detail ("Spell", "Rule section", "Creature"),
so a spell is still told from a rule with no header to say so — except a feature, which keeps
the class-and-level disambiguator a named feature row gets ("Barbarian 6"). Features are the
one kind whose names collide (31 colliding names in the bundle, "Ability Score Improvement" 63
times): the mentions of "rage" hold three different features all named "Path feature", and
"Class feature" on each of the three is one unopenable choice where "Barbarian 6 / 10 / 14" is
three. The mentions are ordered by the same S13 kind order; the header is drawn only when there
is at least one mention.

**No kind takes more than five mentions** (`Search.MENTIONS_PER_KIND`). The FTS query has no
`ORDER BY`, so its hits arrive in import order — spells first — and one loud kind used to take
every slot: "hit points" drew fifty spell rows and the rule section that answers it was never
even fetched. The tier is allowed to come back short of the cap rather than top itself up with
more of the same kind; five rows a kind is what "where else does this appear" is worth.

**The cap is on hits, not rows**: at most 50 hits across the two tiers (`Search.LIMIT`), name
matches taken first so they always survive the cut, and the headers standing over them are
extra rows on top — "fire" draws 50 hits under 7 headers, 57 rows. The top bar counts hits, not
rows: `RESULTS (n)` once the query is in, bare `RESULTS` while it runs. Both queries fetch
`Search.FETCH` (250) candidates rather than 50, because neither one's SQL order is a ranking:
the name query sorts by `sortName`, so cutting at 50 cut "giant" alphabetically and lost **Stone
Giant** and **Storm Giant** from the screen entirely. The bound belongs to the ranking, not to
the SQL.

Two transient lines, each the same lightened `Copy` line the empty state uses (`sdk:ui` has no
spinner): the hub draws `Opening…` while its counts load, and this screen draws `Searching…`
while a query runs. Empty result — neither tier found anything — is one lighten `Copy` line,
"No matches."

**Known limits, left for a later milestone** (both predate the two-tier split; measured over
this bundle): a multi-word query never reaches the name tier, because `Search.likePrefix` hands
the whole typed string to a `LIKE` substring test while the FTS side AND-joins its tokens — so
"long rest" and "opportunity attack" are all mentions, and the rule section that answers them is
found only by the FTS query. And inside the named tier kind grouping outranks match quality, so
an exact name match in a LOOKUP kind sorts below every hub kind: the damage type **Fire**, the
one record whose name *is* the query, is the last named row of "fire". Neither is worth new
query shapes in M2; both want a name index that tokenises, which is an M6-or-later change.

`FIND` re-opens the editor seeded with the current query; the editor pops back to
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
