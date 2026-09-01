package dev.tyler.grimoire.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightProgressBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import dev.tyler.grimoire.compendium.ImportState
import dev.tyler.grimoire.ui.about.AboutScreen
import dev.tyler.grimoire.ui.common.NavRow
import dev.tyler.grimoire.ui.compendium.CompendiumHubScreen

/**
 * S0 Home. Until the compendium is Ready it shows one line and a determinate bar (plan D8 — sdk:ui has no
 * spinner); a failed import shows its reason and the next show retries.
 *
 * The Ready branch is the M2 interim of docs/UI-SPEC.md S0: one `COMPENDIUM ▸` row where the character list
 * will go. `NEW`, `JOURNAL`, `DICE` and the characters themselves arrive with M3/M4 — no dead rows before then.
 *
 * The `COMPENDIUM` row lives inside the Ready branch and nowhere else, and that is load-bearing rather than
 * tidy: [dev.tyler.grimoire.compendium.CompendiumStore.reader] refuses every state but Ready, and
 * `createViewModel` runs inside `navigateTo`, so a tap during the ≈ 2.5 s first-launch import would throw out
 * of the navigation call. A drawn-but-disabled row would be the same bug wearing a lighter colour. Ready is
 * sticky (`ImportGate.ensure` returns false once Ready, and nothing writes a lesser state after it), so a row
 * that is drawn at all stays safe to tap.
 *
 * `ABOUT` sits in the bottom bar in every state: S0 replaces the *list* while the import runs, not the bar,
 * and About reads only `assets/legal/ATTRIBUTION.md` — so the CC-BY attribution stays reachable during the
 * wait and, more to the point, on a failed import, when it would otherwise be the one thing on screen that
 * cannot be read.
 */
@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {
    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel(): HomeViewModel = HomeViewModel(lightContext)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
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
                    when (val s = state) {
                        ImportState.Ready -> NavRow(
                            name = "COMPENDIUM",
                            onClick = { navigateTo({ CompendiumHubScreen(it) }) },
                        )
                        is ImportState.Failed -> LightText(
                            text = s.reason,
                            variant = LightTextVariant.Copy,
                            lighten = true,
                            modifier = Modifier.padding(1f.gridUnitsAsDp()),
                        )
                        ImportState.Idle, ImportState.Checking, is ImportState.Importing -> Column(
                            modifier = Modifier.padding(1f.gridUnitsAsDp()),
                        ) {
                            LightText(
                                text = "Preparing the rules…",
                                variant = LightTextVariant.Copy,
                                lighten = true,
                            )
                            Spacer(Modifier.height(1f.gridUnitsAsDp()))
                            val progress = if (s is ImportState.Importing) s.done.toFloat() / s.total else 0f
                            LightProgressBar(colors = colors, progress = progress)
                        }
                    }
                }
                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(text = "ABOUT", onClick = { navigateTo({ AboutScreen(it) }) }),
                    ),
                )
            }
        }
    }
}
