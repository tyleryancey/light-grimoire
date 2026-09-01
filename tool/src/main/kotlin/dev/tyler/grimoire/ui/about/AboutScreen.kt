package dev.tyler.grimoire.ui.about

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import dev.tyler.grimoire.BuildConfig
import dev.tyler.grimoire.ui.common.MARKDOWN_SIDE_MARGIN_UNITS
import dev.tyler.grimoire.ui.common.MarkdownBlocks
import dev.tyler.grimoire.ui.common.WheelScrollEffect

/** One wheel detent scrolls this many vertical grid units — the reader's step, so both long texts feel alike. */
private const val WHEEL_STEP_UNITS = 5f

/** Breathing room between the attribution and the identity block, and past the last line. */
private const val SECTION_GAP_UNITS = 1.25f

/**
 * S16 About: the bundled CC-BY-4.0 attribution, then four plain identity lines (docs/UI-SPEC.md S16).
 *
 * What is on this screen is settled by docs/LICENSING.md, not by taste. The attribution renders verbatim
 * from `assets/legal/ATTRIBUTION.md`; the only other text is the version, the tool id, "5E compatible" and
 * the repository. Do not add a trademark line or any further Wizards attribution — the SRD's own legal page
 * forbids it. The repository is plain text because a zero-permission tool has no browser to open (ADR-0004).
 *
 * `BuildConfig`'s three fields come from `lighttool.toml` by way of the Light plugin (it sets
 * `applicationId`, `versionCode` and `versionName` on `defaultConfig`), so the identity block cannot drift
 * from the manifest Light's builder ships.
 */
class AboutScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, AboutViewModel>(sealedActivity) {
    override val viewModelClass: Class<AboutViewModel>
        get() = AboutViewModel::class.java

    override fun createViewModel(): AboutViewModel = AboutViewModel(
        readAsset = { String(lightContext.readAsset(it), Charsets.UTF_8) },
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        toolId = BuildConfig.APPLICATION_ID,
    )

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        // Not `rememberScrollState()`: the SDK composes every screen at one un-keyed call site
        // (LightActivity.onCreate), so a pushed screen can land on the slots of the one it covers and
        // inherit its scroll offset. Keyed on the view model — one per back-stack entry — About opens
        // at the top of the attribution however the reader beneath it was left.
        val scrollState = remember(viewModel) { ScrollState(0) }
        val stepPx = with(LocalDensity.current) { WHEEL_STEP_UNITS.verticalGridUnitsAsDp().toPx() }
        WheelScrollEffect(viewModel.ticks, scrollState) { stepPx }
        LightTheme(colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("ABOUT"),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LightScrollView(modifier = Modifier.fillMaxSize(), scrollState = scrollState) {
                        // The attribution, through the reader's own Markdown-lite renderer: one `#` heading
                        // and five paragraphs, drawn exactly as the generated file writes them.
                        MarkdownBlocks(state.blocks)
                        val notice = state.notice
                        if (notice != null) {
                            Quiet(notice)
                        }
                        Spacer(Modifier.height(SECTION_GAP_UNITS.gridUnitsAsDp()))
                        for (line in state.lines) {
                            LightText(
                                text = line,
                                variant = LightTextVariant.Copy,
                                modifier = Modifier.padding(horizontal = MARKDOWN_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
                            )
                        }
                        Spacer(Modifier.height(SECTION_GAP_UNITS.gridUnitsAsDp()))
                    }
                }
                // S16's wireframe draws a bare bottom-bar row: nothing in it, but its 4-unit height and
                // 1-unit top margin are part of the reading column the wireframe measures.
                LightBottomBar(items = emptyList())
            }
        }
    }

    @Composable
    private fun Quiet(text: String) {
        LightText(
            text = text,
            variant = LightTextVariant.Copy,
            lighten = true,
            modifier = Modifier.padding(
                top = SECTION_GAP_UNITS.gridUnitsAsDp(),
                start = MARKDOWN_SIDE_MARGIN_UNITS.gridUnitsAsDp(),
                end = MARKDOWN_SIDE_MARGIN_UNITS.gridUnitsAsDp(),
            ),
        )
    }
}
