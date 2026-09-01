package dev.tyler.grimoire.ui.sheet

import dev.tyler.grimoire.ui.common.SlotStripModel

/**
 * The nine rows of S1's hub, in the order `docs/UI-SPEC.md` S1 puts them — which is **not** the order a
 * paper sheet reads in, and the reordering is the substantive half of that section.
 *
 * Nineteen of the 23 content units go to the list (the pinned header takes four), so 19 / 2.5 = 7.6 rows
 * are visible without scrolling. Fitting nine rows into 7.6 slots forced `REST` up out of row 9 — below
 * the fold would have broken "rest start" in the one-tap contract — and pushed `FEATURES & RESOURCES` and
 * `GEAR & COIN` down to rows 8–9, on the frequency ranking in `docs/PRD.md:43-46`. The declaration order
 * below *is* that decision; `SheetText.sheetRows` walks `entries` and never restates it.
 *
 * [label] is the row's own word. It is drawn on the left for every row but [SLOTS], whose pips are the
 * content and whose label sits in the right-hand column instead (`●●●● ●●● ●○   slots  ▸`) — the one
 * inversion in the list, and the reason the label lives on the destination rather than in the screen.
 */
enum class SheetDestination(val label: String) {
    /** S3, the HP pad — the only destination that exists in M3 task 1. */
    HP("HP"),

    /** S5, the same screen [SPELLS] opens: the strip is the fastest look at what is still castable. */
    SLOTS("slots"),

    TURN("TURN"),

    CHECKS("CHECKS & SAVES"),

    SPELLS("SPELLS"),

    CONDITIONS("CONDITIONS"),

    REST("REST"),

    FEATURES("FEATURES & RESOURCES"),

    GEAR("GEAR & COIN"),
}

/**
 * One drawn row of the hub, carrying exactly what that row draws.
 *
 * **The models are inside the list, not beside it.** An earlier shape had `hp` and `slots` as their own
 * fields on [SheetUiState] with the row list holding two placeholders — which meant two of the nine rows
 * had no content of their own to test, and a sibling field could fall out of step with the row that drew
 * it. Here `sheetRows` returns nine rows and every one of them is assertable on its own terms.
 *
 * [destination] is what the screen switches on to navigate, so a tap is never routed by matching a label
 * string.
 */
sealed interface SheetRow {
    val destination: SheetDestination

    /** The row's own word — [SheetDestination.label], restated here so a screen never reaches past the row. */
    val label: String get() = destination.label

    /** Every text row: a label, and the lightened right-hand [detail] the row has something to say in. */
    data class Nav(override val destination: SheetDestination, val detail: String? = null) : SheetRow

    /**
     * `HP  31 / 43   TEMP 0  ▸`. [numbers] goes **bold** when [bloodied] — the tool's one weight cue, drawn
     * through `EmphasisText` because `sdk:ui` has no bold `LightText` — and [suffix] is the lightened
     * temporary-hit-point column on the right.
     */
    data class Hp(val numbers: String, val suffix: String, val bloodied: Boolean) : SheetRow {
        override val destination: SheetDestination get() = SheetDestination.HP
    }

    /**
     * `●●●● ●●● ●○   slots  ▸` — levels 1–3 and the pact band as pips, filled = still castable.
     *
     * The row is omitted outright for a character with no bands at all (a non-caster, or a caster who has
     * not levelled into one): an empty strip beside the word "slots" is a read-out of nothing.
     */
    data class Slots(val strip: SlotStripModel) : SheetRow {
        override val destination: SheetDestination get() = SheetDestination.SLOTS
    }
}

/**
 * Everything S1 draws (docs/UI-SPEC.md S1).
 *
 * [title] is the top bar's centre, [identity] and [stats] are the two `Detail` lines of the pinned header
 * and [inspiration] is the star beside them; [rows] is the list beneath. Every one of them is derived —
 * the view model holds the character and the `Derived`, and this holds only strings and models.
 *
 * [loading] holds until the first load *and* its derivation finish, because the stat line cannot be drawn
 * without the armor table and a header that filled in a line at a time would move under the player's eye.
 * [missing] is the "no such character" branch.
 *
 * **[title] is the one field that means something in all three states**, which is why the screen draws the
 * top bar outside its `when`: it starts as the name the tapped row already held (`SheetScreen`'s `name`,
 * `ReaderViewModel`'s pattern), becomes the stored name once the load lands, and survives onto the
 * [missing] branch rather than leaving an empty bar over "No such character." Every other field is
 * meaningless while [loading] or [missing] is true, which is why the body branches on them first.
 */
data class SheetUiState(
    val title: String = "",
    val identity: String = "",
    val stats: String = "",
    val inspiration: Boolean = false,
    val rows: List<SheetRow> = emptyList(),
    val loading: Boolean = true,
    val missing: Boolean = false,
) {
    /** The HP row, which every loaded sheet has — null only while [loading] or [missing]. */
    val hp: SheetRow.Hp? get() = rows.filterIsInstance<SheetRow.Hp>().firstOrNull()

    /** The slot row, absent for a non-caster. */
    val slots: SheetRow.Slots? get() = rows.filterIsInstance<SheetRow.Slots>().firstOrNull()
}
