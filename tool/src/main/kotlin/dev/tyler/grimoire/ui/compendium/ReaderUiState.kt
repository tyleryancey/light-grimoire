package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.compendium.Block
import dev.tyler.grimoire.compendium.CompendiumRef

/**
 * Everything the S10 reader draws, already composed and already resolved (docs/UI-SPEC.md S10).
 *
 * [blocks] is `ReaderContent.of`'s output verbatim — header [Block.Field] lines, then the
 * Markdown-lite body with every table already lowered. [links] is the cross-link footer with each
 * section's [RefQuery][dev.tyler.grimoire.compendium.RefQuery] already run through the reader, so
 * the composable never suspends. [title] starts as the navigating row's own name so the top bar is
 * never blank, and becomes the record's name once it is fetched. [loading] holds only until the first
 * load finishes; [missing] is the "not in the compendium" branch — a key that resolves to no record.
 */
data class ReaderUiState(
    val title: String = "",
    val blocks: List<Block> = emptyList(),
    val links: List<ReaderLink> = emptyList(),
    val loading: Boolean = true,
    val missing: Boolean = false,
)

/** One resolved footer group: the label `ReaderContent` gave it and the rows to draw under it. */
data class ReaderLink(val label: String, val refs: List<CompendiumRef>)
