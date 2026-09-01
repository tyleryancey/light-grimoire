package dev.tyler.grimoire.ui.home

import dev.tyler.grimoire.compendium.ImportState
import dev.tyler.grimoire.data.CharacterLimits
import dev.tyler.grimoire.data.CharacterSummaryRow

/** Which of S0's three bodies is drawn between the bars (docs/UI-SPEC.md S0). */
enum class HomeBody {
    /** `Preparing the rules…` over a determinate bar — the import, and the whole of a first launch. */
    PREPARING,

    /** The import's own reason, in the line the progress line was in; the next show retries. */
    FAILED,

    /** The character list, then `COMPENDIUM ▸`. The only body that offers to open anything. */
    LIST,
}

/**
 * Everything S0 draws, as a value (docs/UI-SPEC.md S0). Built by [of] from the two things Home watches —
 * the compendium's [ImportState] and the character list — plus the one line the screen itself can say.
 *
 * **The list is empty in every body but [HomeBody.LIST], and that is a safety property rather than a
 * tidiness one.** Both of a character row's destinations need the compendium: S1 builds its view model from
 * `CompendiumStore.reader()`, which throws unless the import is Ready, and `createViewModel` runs inside
 * `navigateTo` — so a tappable row before Ready throws out of the navigation call. The spec says the same
 * thing in words ("The list above appears only once the store is Ready"), and making it structural here
 * means no branch of the screen can draw a row the tool cannot open.
 */
data class HomeUiState(
    val body: HomeBody = HomeBody.PREPARING,
    /** 0f‥1f for the determinate bar; 0 in every body but [HomeBody.PREPARING]. */
    val progress: Float = 0f,
    /** The failed import's reason, and empty in every other body. */
    val reason: String = "",
    /** At most [CharacterLimits.MAX_CHARACTERS] rows, most recently touched first. */
    val characters: List<CharacterSummaryRow> = emptyList(),
    /**
     * Whether the store has actually answered yet — which is not the same question as whether it answered
     * with nothing.
     *
     * The import and the list query race on a warm launch: the compendium's stamp check and one indexed
     * `SELECT` are both a few milliseconds, so Ready can arrive first. Without this, a player with six
     * characters would be told "No characters yet." for the frame in between.
     */
    val listLoaded: Boolean = false,
    /**
     * A refusal the player's last tap earned. Two reach this line, both from the `NEW` flow: a seventh
     * character (`HomeViewModel.requestNew`, before the editor opens) and a name past 40 characters
     * (`HomeViewModel.requestClass`, the moment the editor closes). Both are the words the repository would
     * have used, said earlier — see `CharacterLimits`.
     */
    val message: String? = null,
    /**
     * Bumped every time [message] is set, including to the same sentence twice.
     *
     * The screen scrolls the line into view when this changes, and re-tapping `NEW` at the cap is exactly
     * the case that needs it: the sentence does not change, so a screen keyed on the text alone would sit
     * still on the second tap and read as a control that does nothing.
     */
    val messageNonce: Int = 0,
) {
    /** True when `NEW` can still be offered a name — the cap is on the store, so the row count is the check. */
    val canCreate: Boolean
        get() = body == HomeBody.LIST && listLoaded && characters.size < CharacterLimits.MAX_CHARACTERS

    /** True when the one quiet line is the whole body: Ready, asked, and holding nothing. */
    val empty: Boolean
        get() = body == HomeBody.LIST && listLoaded && characters.isEmpty()

    companion object {
        /** The one mapping from what Home watches to what Home draws. */
        fun of(
            imports: ImportState,
            characters: List<CharacterSummaryRow>,
            listLoaded: Boolean = false,
            message: String? = null,
            messageNonce: Int = 0,
        ): HomeUiState = when (imports) {
            ImportState.Ready -> HomeUiState(
                body = HomeBody.LIST,
                characters = characters,
                listLoaded = listLoaded,
                message = message,
                messageNonce = messageNonce,
            )
            is ImportState.Failed -> HomeUiState(
                body = HomeBody.FAILED,
                reason = imports.reason,
                listLoaded = listLoaded,
                message = message,
                messageNonce = messageNonce,
            )
            // A total of 0 would be a store that reported progress before it knew the bundle's size; the bar
            // draws empty rather than NaN, which Compose would paint as a full one.
            is ImportState.Importing -> HomeUiState(
                body = HomeBody.PREPARING,
                progress = if (imports.total > 0) imports.done.toFloat() / imports.total else 0f,
                listLoaded = listLoaded,
                message = message,
                messageNonce = messageNonce,
            )
            ImportState.Idle, ImportState.Checking -> HomeUiState(
                body = HomeBody.PREPARING,
                listLoaded = listLoaded,
                message = message,
                messageNonce = messageNonce,
            )
        }
    }
}
