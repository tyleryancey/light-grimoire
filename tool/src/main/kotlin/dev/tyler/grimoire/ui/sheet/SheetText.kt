package dev.tyler.grimoire.ui.sheet

import dev.tyler.grimoire.data.Summaries
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Derive
import dev.tyler.grimoire.rules.Derived
import dev.tyler.grimoire.ui.common.Layout
import dev.tyler.grimoire.ui.common.ROW_SIDE_MARGIN_UNITS
import dev.tyler.grimoire.ui.common.Signs
import dev.tyler.grimoire.ui.common.slotStrip

/**
 * Everything S1 puts on screen, as pure functions of the character and its `Derived` — which is where all
 * of S1's testable behaviour lives, because a composable cannot be exercised from the JVM gate.
 *
 * Nothing here formats anything twice. The identity line is `Summaries.summaryOf`, the same function that
 * writes S0's second line into the database, so the hub and the home list can never disagree about how a
 * character is named; every signed number goes through `Signs.mod`, so `+0` and `−1` are the tool's one
 * spelling everywhere.
 */
object SheetText {
    /**
     * How many pips the slot row can draw, from the row's own width.
     *
     * The row lives in a `LightLazyScrollView` that always scrolls — nine 2.5-unit rows in a 19-unit
     * viewport — which spends the 2-unit `Outside` gutter twice, so it is **21** usable columns and not
     * the 25 an unscrolled row would get ([ROW_SIDE_MARGIN_UNITS] states all three cases). Less the
     * trailing `ARROW_RIGHT` (2 units, `LightIcon`'s default square), the word `slots` at `Detail`
     * (5 characters ÷ [Layout.DETAIL_CHARS_PER_UNIT] = 3.33 units), a unit of gap before it and half a
     * unit after, that leaves 14.17 units, at the [dev.tyler.grimoire.ui.common.PIP_PITCH_UNITS] 0.67-unit
     * pitch of one `Detail` character: 21 pips.
     *
     * **The budget cannot fire on any character the rules admit** — levels 1–3 hold at most 4 + 3 + 3
     * slots and Pact Magic at most 4, so 14 pips is the ceiling (`SheetTextTest` pins that) — which is
     * exactly why it is a private figure here and not a shared constant in `Layout.kt`: `slotStrip` needs
     * *an* argument, and a number that can never bind does not need a home other screens can reach.
     * `SlotStripModel.more` is therefore driven by depth alone: a 4th-level slot or deeper, which is on S5.
     */
    private const val SLOT_ROW_MAX_PIPS = 21

    /**
     * The top bar's centre: the whole name, uppercased and **untruncated by this tool**.
     *
     * Names run to 40 characters (`CharacterLimits.MAX_NAME`) and `LightTopBarCenter`'s centre is capped at
     * 18 grid units, which the SDK ellipsizes itself. Every shortening heuristic is wrong on some real name
     * — "Brother Aldric" wants its last word kept, "Vessa Quickfinger" its first — so the tool passes the
     * whole name and lets the SDK's ellipsis have the last word (docs/UI-SPEC.md S1).
     */
    fun title(character: Character): String = title(character.name)

    /**
     * The same rule applied to a name alone — what the pushing screen already holds, before the character
     * has been read back out of the store (`SheetViewModel`'s initial title, and `ReaderScreen`'s pattern).
     * One function, so the bar cannot change its spelling when the load lands.
     */
    fun title(name: String): String = name.uppercase()

    /**
     * The header's first line: `Cleric 5 · Hill Dwarf`.
     *
     * This is `Summaries.summaryOf` and nothing else. The one-line delegation is the point — S0 stores that
     * string in the `characters` table to draw its list from, and S1 recomputes it here; a second formatter
     * would let a character read one way on Home and another on its own sheet.
     */
    fun identity(character: Character): String = Summaries.summaryOf(character)

    /**
     * The header's second line: `AC 18  INIT +0  SPD 25  PB +3`.
     *
     * [speed] comes from the character, not from [derived]: `Derived` has no speed field at all — speed is
     * transcribed off the paper sheet and never computed — so a caller that reached for `derived.speed`
     * would not compile, which is the intended shape rather than an oversight to fix later.
     *
     * Two spaces between stats, one inside each, per the wireframe: 29 characters, which is wider than the
     * 27-column frame but ≈ 19 of the 25 usable units at `Detail`'s 1.5 characters per unit — and this line
     * really does get all 25, unlike the rows below it: the header is drawn *outside* the scroll view
     * (`SheetScreen.SheetBody`), so no scrollbar gutter is taken off it ([ROW_SIDE_MARGIN_UNITS]).
     */
    fun statLine(derived: Derived, speed: Int): String =
        "AC ${derived.ac}  INIT ${Signs.mod(derived.initiative)}  SPD $speed  PB ${Signs.mod(derived.profBonus)}"

    /**
     * The HP row: `31 / 43` and `TEMP 0`, with the bloodied flag that turns the numbers bold.
     *
     * `Derive.hpState` is the pure half of the engine — current HP is `max − damage` floored at 0, bloodied
     * is at or below half **and above zero** — so a character at 0 draws `0 / 68` in plain weight. That is
     * deliberate: down is a state S3 says in words (`DOWN`, `STABLE`, `DEAD`), and bolding it here would
     * spend the tool's one weight cue on the state the player is least likely to have missed.
     */
    fun hpRow(character: Character): SheetRow.Hp {
        val hp = Derive.hpState(character)
        return SheetRow.Hp(
            numbers = "${hp.current} / ${hp.max}",
            suffix = "TEMP ${hp.temp}",
            bloodied = hp.bloodied,
        )
    }

    /**
     * The `CONDITIONS` row's right-hand column: the active conditions, then the concentration spell marked
     * `(C)` — `Bless (C)`, `Prone`, `Poisoned · Bless (C)` — or null when there is nothing to say.
     *
     * **Exhaustion is not here, and that is the spec's own division, not an omission.** S7 draws exhaustion
     * as a stepper rather than one of its 14 toggles, because `character.schema.json` tracks it separately;
     * S1's sentence is "active conditions and the concentration spell". A character at exhaustion 3 shows
     * only their conditions on this row — flagged for ratification, not decided here.
     *
     * Conditions are sorted by the name they are drawn under, not left in the character's stored order:
     * the row is a read-out a player glances at, and one that reshuffled itself according to which
     * condition was toggled last would be unreadable at exactly the moment two of them are up. S7's own
     * grid is alphabetical for the same reason.
     */
    fun conditionsDetail(character: Character): String? {
        val conditions = character.conditions.map(Summaries::title).sorted()
        val concentrating = character.concentration?.name?.trim()?.takeIf { it.isNotEmpty() }
        val parts = conditions + listOfNotNull(concentrating?.let { "$it (C)" })
        return parts.takeIf { it.isNotEmpty() }?.joinToString(Summaries.SEPARATOR)
    }

    /**
     * The nine hub rows, in [SheetDestination]'s order — the spec's reordered one, which the enum owns so
     * this function never restates it.
     *
     * All nine are built from day one, including the seven whose screens M3 task 1 has not written yet: the
     * whole S1 section is an argument about whether nine 2.5-unit rows fit under a 4-unit pinned header, and
     * a hub that returned only the rows that navigate would leave that argument untested until the last
     * screen landed. What the screen does with a row it cannot open is the screen's business (see
     * `SheetScreen`); what fits is decided here.
     *
     * The slot row is the one row that can be absent — a non-caster has no bands (docs/UI-SPEC.md S1, and
     * `slotStrip`'s own contract) — so a rogue draws eight. `SPELLS` stays for them: the spec drops the
     * strip and nothing else, and a rogue who multiclasses next level wants the row where it was.
     */
    fun sheetRows(character: Character, derived: Derived): List<SheetRow> {
        val strip = slotStrip(derived.spellcasting, character.spellcasting, SLOT_ROW_MAX_PIPS)
        return SheetDestination.entries.mapNotNull { destination ->
            when (destination) {
                SheetDestination.HP -> hpRow(character)
                SheetDestination.SLOTS -> if (strip.isEmpty) null else SheetRow.Slots(strip)
                SheetDestination.CONDITIONS -> SheetRow.Nav(destination, conditionsDetail(character))
                // Listed rather than an `else`, so a destination added later must be given a row here.
                SheetDestination.TURN,
                SheetDestination.CHECKS,
                SheetDestination.SPELLS,
                SheetDestination.REST,
                SheetDestination.FEATURES,
                SheetDestination.GEAR,
                -> SheetRow.Nav(destination)
            }
        }
    }
}
