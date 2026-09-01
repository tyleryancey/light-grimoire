package dev.tyler.grimoire.ui.common

import dev.tyler.grimoire.rules.DerivedSpellcasting
import dev.tyler.grimoire.rules.Spellcasting
import kotlin.math.max

/**
 * One band of the slot strip: a label, how many slots are **available**, and how many there are in
 * all. [available] is what a [PipStrip] fills — see [slotStrip] for why that polarity is the whole
 * point of this file.
 */
data class SlotGroup(val label: String, val available: Int, val total: Int)

/**
 * What S1's `●●●● ●●● ●○   slots ▸` row and S5's `1st ●●●●   2nd ●●●  3rd ●●○` line draw.
 *
 * [more] says the strip is not the whole story — either the character has slots deeper than
 * [SLOT_STRIP_MAX_LEVEL], or [slotStrip]'s pip budget dropped a band. Both mean the same thing to
 * the player and to the screen: the full picture is on S5.
 */
data class SlotStripModel(val groups: List<SlotGroup>, val more: Boolean) {
    /** No bands at all — a non-caster (S1 draws no slot row) or a caster who has not levelled into one. */
    val isEmpty: Boolean get() = groups.isEmpty()

    /** Total pips drawn, which is what [slotStrip]'s `maxPips` budgets. */
    val pips: Int get() = groups.sumOf { it.total }
}

/** The regular spell levels the inline strip draws; anything deeper sets [SlotStripModel.more]. */
const val SLOT_STRIP_MAX_LEVEL = 3

private val ORDINALS = listOf("1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th", "9th")

/** A spell level as the wireframes label it: `1st`, `2nd`, `3rd`. Shared with S5's level bands. */
fun slotLevelLabel(level: Int): String = ORDINALS.getOrElse(level - 1) { level.toString() }

/**
 * The slot strip, as a pure function of the derived maxima and the stored spend.
 *
 * **Filled = available = max − used.** That is the one thing this file exists to get right and the
 * easy bug to write: a pip strip is a read-out of what the player can still cast, so filling `used`
 * pips would draw a rested character as empty and a spent one as full — exactly backwards, and
 * plausible enough on a 4/3/2 cleric that a careless test would not catch it (a first-level band of
 * 4 max and 2 used reads `2` either way). The test pins every band's `(available, total)` pair, and
 * the all-spent paladin/warlock fixture pins the polarity outright.
 *
 * Slot maxima are derived and the spend is stored, so they can disagree after a level change; a
 * spend outside `0..total` is clamped rather than allowed to draw a negative or over-long run.
 *
 * **Layout.** Regular levels 1..[SLOT_STRIP_MAX_LEVEL] in order, then the Pact Magic band. The pact
 * band is budgeted *first* even though it is drawn last: a pure warlock has an all-zero `slotsMax`
 * and a non-null `pact`, so it is the only band they have, and a strip that dropped it for want of
 * pips would draw a caster with no slots at all. What [maxPips] then squeezes out is the deepest
 * regular level — the least-cast one — and the strip stops at the first band that does not fit
 * rather than skipping it and drawing a later one out of order. Anything dropped, and any slot
 * deeper than the strip draws, sets [more].
 *
 * [used] is nullable because a non-caster's `Character.spellcasting` is: no stored spend means
 * nothing spent, which for a non-caster's all-zero maxima is an empty strip either way.
 */
fun slotStrip(derived: DerivedSpellcasting, used: Spellcasting?, maxPips: Int): SlotStripModel {
    val spent = used?.slotsUsed ?: emptyList()
    val regular = (1..SLOT_STRIP_MAX_LEVEL).mapNotNull { level ->
        val total = derived.slotsMax.getOrElse(level - 1) { 0 }
        if (total <= 0) {
            null
        } else {
            val gone = spent.getOrElse(level - 1) { 0 }.coerceIn(0, total)
            SlotGroup(slotLevelLabel(level), available = total - gone, total = total)
        }
    }
    val pact = derived.pact?.takeIf { it.count > 0 }?.let { slots ->
        val gone = (used?.pactUsed ?: 0).coerceIn(0, slots.count)
        SlotGroup("Pact ${slotLevelLabel(slots.level)}", available = slots.count - gone, total = slots.count)
    }

    val budget = max(0, maxPips)
    // The pact band is budgeted before the regular levels are laid in, and drawn after them.
    val fittingPact = pact?.takeIf { it.total <= budget }
    var remaining = budget - (fittingPact?.total ?: 0)
    var dropped = pact != null && fittingPact == null

    val shown = mutableListOf<SlotGroup>()
    for (group in regular) {
        if (group.total <= remaining) {
            shown += group
            remaining -= group.total
        } else {
            // Stop rather than skip: a strip that jumped 3rd and drew a later band would read as
            // levels the character does not have.
            dropped = true
            break
        }
    }
    if (fittingPact != null) shown += fittingPact

    val deeper = (SLOT_STRIP_MAX_LEVEL + 1..9).any { derived.slotsMax.getOrElse(it - 1) { 0 } > 0 }
    return SlotStripModel(groups = shown, more = dropped || deeper)
}
