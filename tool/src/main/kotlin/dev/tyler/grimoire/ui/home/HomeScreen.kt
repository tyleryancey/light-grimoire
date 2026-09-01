package dev.tyler.grimoire.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightColors
import com.thelightphone.sdk.ui.LightProgressBar
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import dev.tyler.grimoire.compendium.CompendiumStore
import dev.tyler.grimoire.data.GrimoireStore
import dev.tyler.grimoire.ui.about.AboutScreen
import dev.tyler.grimoire.ui.common.NavRow
import dev.tyler.grimoire.ui.common.QuietLine
import dev.tyler.grimoire.ui.common.SECTION_GAP_UNITS
import dev.tyler.grimoire.ui.common.TWO_LINE_ROW_HEIGHT_GRID_UNITS
import dev.tyler.grimoire.ui.common.TextEditorScreen
import dev.tyler.grimoire.ui.common.TwoLineRow
import dev.tyler.grimoire.ui.common.WheelScrollEffect
import dev.tyler.grimoire.ui.compendium.CompendiumHubScreen
import dev.tyler.grimoire.ui.sheet.SheetScreen

/** The editor's title, and the whole of S12 step 1: a name, in initial caps. */
private const val NAME = "NAME"

/**
 * S0 Home: the characters, and the way into the rules (docs/UI-SPEC.md S0).
 *
 * Three bodies, and the import decides which ([HomeUiState]): `Preparing the rules…` over a determinate
 * bar while the compendium imports (plan D8 — `sdk:ui` has no spinner), the import's own reason in that
 * same line if it fails, and otherwise the character list over a gap over `COMPENDIUM ▸`.
 *
 * **Nothing that needs the compendium is drawn before Ready, and that is load-bearing rather than tidy.**
 * `CompendiumStore.reader()` refuses every state but Ready and `createViewModel` runs inside `navigateTo`,
 * so a row tapped during the ≈ 2.5 s import would throw out of the navigation call. That rules out three
 * things at once: the `COMPENDIUM` row (S13), the character rows (S1 derives AC from the compendium's
 * armor table) and `NEW` (its class picker lists the bundle's classes). Ready is sticky, so a control that
 * is drawn at all stays safe to tap. `ABOUT` is drawn in every state on purpose — it reads only
 * `assets/legal/ATTRIBUTION.md`, so the CC-BY attribution stays reachable during the wait and, more to the
 * point, on a failed import, when it would otherwise be the one thing on screen that cannot be read.
 *
 * **M3 task 1 interim, and it deviates from the S0 wireframe.** The wireframe draws `JOURNAL` and `DICE`
 * under the characters; S14 and S15 do not exist, and the tool's no-dead-controls rule (S1's inert rows,
 * S3's missing `REST`) says not to draw a row before it goes anywhere — so they are absent, and return
 * with M5. `docs/UI-SPEC.md`'s S0 M3 interim note records exactly this shape — the character list,
 * `COMPENDIUM`, `NEW` and `ABOUT` live, `JOURNAL` and `DICE` absent — so the screen and the spec agree.
 *
 * S0 is a [LightScrollView] rather than a `LightLazyScrollView` because it mixes row heights — six 4-unit
 * character rows, a 1-unit gap and a 2.5-unit `COMPENDIUM` row come to 27.5 units against the 23 available
 * — which the uniform-row contract cannot draw. (The spec's own figure is 32.5: it counts three utility
 * rows, `JOURNAL` and `DICE` included, which this interim does not draw. Either number overflows 23, and
 * mixed heights would rule out a lazy list even if neither did.) The wheel scrolls it one character row
 * per detent.
 */
@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {
    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    /**
     * The two process-wide stores, bound as seams — the view model itself never sees a context, which is
     * what lets the JVM gate drive Home at all (see [HomeViewModel]).
     */
    override fun createViewModel(): HomeViewModel = HomeViewModel(
        ensureImported = { CompendiumStore.ensureImported(lightContext) },
        importState = CompendiumStore.state,
        characters = GrimoireStore.characters(lightContext),
    )

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        // Not `rememberScrollState()`: the SDK composes every screen at one un-keyed call site, so a pushed
        // screen can land on Home's slots and inherit its offset. Keyed on the view model — one per
        // back-stack entry — Home keeps its own place across a visit to a sheet.
        val scrollState = remember(viewModel) { ScrollState(0) }
        val stepPx = with(LocalDensity.current) { TWO_LINE_ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp().toPx() }
        WheelScrollEffect(viewModel.ticks, scrollState) { stepPx }
        // A refusal is drawn at the top of the list, so it is brought into view rather than left below six
        // characters' worth of scroll. Keyed on the nonce, not the sentence: re-tapping `NEW` at the cap
        // says the same words twice and must still move.
        LaunchedEffect(state.messageNonce) {
            if (state.message != null) scrollState.animateScrollTo(0)
        }
        LightTheme(colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(center = LightTopBarCenter.Text("GRIMOIRE"))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (state.body) {
                        HomeBody.PREPARING -> PreparingBody(progress = state.progress, colors = colors)
                        // The same lightened line the progress text used, in the same place — the reason is
                        // what the player has instead of a bar, and the next show retries the import.
                        HomeBody.FAILED -> QuietLine(state.reason)
                        HomeBody.LIST -> ListBody(
                            state = state,
                            scrollState = scrollState,
                            onCharacter = ::openCharacter,
                            onCompendium = { navigateTo({ CompendiumHubScreen(it) }) },
                        )
                    }
                }
                LightBottomBar(items = barItems(state))
            }
        }
    }

    /**
     * `NEW` only once the compendium is Ready — the class picker reads it — and `ABOUT` always.
     *
     * Two text items, which `LightBottomBar` splits left and right, drawing the wireframe's
     * `▁▁ NEW ▁▁ ABOUT ▁▁`; one item is centred, which is the M2 bar this build still shows while the
     * import runs.
     */
    private fun barItems(state: HomeUiState): List<LightBarButton> {
        val about = LightBarButton.Text(text = "ABOUT", onClick = { navigateTo({ AboutScreen(it) }) })
        if (state.body != HomeBody.LIST) return listOf(about)
        return listOf(LightBarButton.Text(text = "NEW", onClick = ::newCharacter), about)
    }

    /**
     * Open a character's sheet, handing S1 the name the row already holds.
     *
     * The name is passed for the reason `ReaderScreen`'s is: S1's title otherwise stays empty until the
     * character, the compendium's armor table and the whole derivation have all landed, and stays empty for
     * good on the "No such character." branch. Home knows the name at the moment of the tap, so the bar
     * reads as itself from the first frame and never fills in under the player's eye.
     */
    private fun openCharacter(id: String, name: String) {
        navigateTo({ SheetScreen(it, id, name) })
    }

    /**
     * `NEW`: a name, then a class, then the character's own sheet (docs/UI-SPEC.md S12 steps 1–2).
     *
     * Each step pops before the next is pushed, so the branch is S0 → S1 and never four screens deep, and
     * **a cancel at either step delivers no result at all** — `LightActivity.deliverResult` returns early
     * on a null — so the callback never runs and nothing is created. An empty or blank name is treated the
     * same way as a cancel: it is what the editor returns when a player submits an untouched field.
     *
     * **Both of the flow's rules are checked at the earliest moment they are knowable**, which is the whole
     * shape of this function. The store's cap is knowable before anything is typed, so
     * [HomeViewModel.requestNew] refuses there and the editor never opens; a name's length is knowable the
     * instant the editor comes back, so [HomeViewModel.requestClass] refuses there and the class picker
     * never opens. `LightTextInputEditor` has no length parameter, so an over-long name *can* be submitted
     * — and the editor's result cannot be re-read once it is gone, so refusing it after the class step
     * would throw away a transcription the player would have to make again.
     */
    private fun newCharacter() {
        if (!viewModel.requestNew()) return
        navigateTo({ TextEditorScreen(it, NAME, initialCaps = true) }) { typed ->
            val name = typed?.trim().orEmpty()
            if (name.isEmpty()) return@navigateTo
            if (!viewModel.requestClass(name)) return@navigateTo
            navigateTo({ ClassPickerScreen(it) }) { classKey ->
                viewModel.create(name, classKey) { id -> openCharacter(id, name) }
            }
        }
    }
}

/**
 * The import's line and its bar — the measured M2 body, unchanged: one lightened `Copy` line over a
 * determinate [LightProgressBar] that advances per kind (22 steps, ≈ 2.5 s on the LP3).
 */
@Composable
private fun PreparingBody(progress: Float, colors: LightColors) {
    Column(modifier = Modifier.padding(1f.gridUnitsAsDp())) {
        LightText(
            text = "Preparing the rules…",
            variant = LightTextVariant.Copy,
            lighten = true,
        )
        Spacer(Modifier.height(1f.gridUnitsAsDp()))
        LightProgressBar(colors = colors, progress = progress)
    }
}

/**
 * The Ready body: any refusal, then the characters, then a gap, then `COMPENDIUM ▸`.
 *
 * The gap is the wireframe's blank line, and the reason the list is a `LightScrollView`: 4-unit character
 * rows and a 2.5-unit utility row are two heights, which no uniform list can draw.
 */
@Composable
private fun ListBody(
    state: HomeUiState,
    scrollState: ScrollState,
    onCharacter: (String, String) -> Unit,
    onCompendium: () -> Unit,
) {
    LightScrollView(scrollState = scrollState) {
        val message = state.message
        if (message != null) QuietLine(message)
        // The one line a new player sees. `NEW` is right below it in the bar, which is why the line says
        // what is true rather than what to do.
        if (state.empty) QuietLine("No characters yet.")
        for (character in state.characters) {
            TwoLineRow(
                title = character.name,
                subtitle = character.summary,
                onClick = { onCharacter(character.id, character.name) },
            )
        }
        Spacer(Modifier.height(SECTION_GAP_UNITS.gridUnitsAsDp()))
        NavRow(name = "COMPENDIUM", onClick = onCompendium)
    }
}
