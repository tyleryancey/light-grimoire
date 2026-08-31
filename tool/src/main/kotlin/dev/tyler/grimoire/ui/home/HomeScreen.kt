package dev.tyler.grimoire.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
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

/**
 * S0 Home. Until the compendium is Ready it shows one line and a determinate bar (plan D8 — sdk:ui has no
 * spinner); a failed import shows its reason and the next show retries. The Ready branch is still the M0
 * placeholder — the real Home arrives with M3.
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
                when (val s = state) {
                    ImportState.Ready -> LightText(
                        text = "M0 scaffold. Screens arrive in M2 and M3.",
                        variant = LightTextVariant.Copy,
                        lighten = true,
                        modifier = Modifier.padding(1f.gridUnitsAsDp()),
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
        }
    }
}
