package dev.tyler.grimoire.ui.about

import dev.tyler.grimoire.compendium.Block

/**
 * Everything S16 draws (docs/UI-SPEC.md S16): the bundled attribution, then the four plain identity
 * lines. Nothing else is on the screen — docs/LICENSING.md forbids any further Wizards attribution.
 *
 * [blocks] is `assets/legal/ATTRIBUTION.md` parsed by the same Markdown-lite parser the reader uses,
 * rendered verbatim (its `#` heading kept — there is no record name to match it against). [notice] is
 * the fallback line when the asset cannot be read, and is null on every normal launch. [lines] is the
 * identity block, composed in the view model's constructor so it is on screen from the first frame and
 * survives a failed read.
 */
data class AboutUiState(
    val blocks: List<Block> = emptyList(),
    val notice: String? = null,
    val lines: List<String> = emptyList(),
)
