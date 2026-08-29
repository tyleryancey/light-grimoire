package dev.tyler.grimoire.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp

@InitialScreen
class ImportSpikeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ImportSpikeViewModel>(sealedActivity) {

    override val viewModelClass: Class<ImportSpikeViewModel>
        get() = ImportSpikeViewModel::class.java

    override fun createViewModel(): ImportSpikeViewModel = ImportSpikeViewModel(lightContext)

    @Composable
    override fun Content() {
        val status by viewModel.status.collectAsState()
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background).padding(1f.gridUnitsAsDp())) {
                LightText(text = "Import spike", variant = LightTextVariant.Heading)
                LightText(text = status, variant = LightTextVariant.Detail)
            }
        }
    }
}
