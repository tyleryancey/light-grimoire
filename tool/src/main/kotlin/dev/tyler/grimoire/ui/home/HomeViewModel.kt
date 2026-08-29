package dev.tyler.grimoire.ui.home

import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.compendium.CompendiumStore
import dev.tyler.grimoire.compendium.ImportState
import kotlinx.coroutines.flow.StateFlow

/**
 * Home's view model (plan D7): every `onScreenShow` — navigation, the first frame and each `onResume` — asks
 * [CompendiumStore] to make sure the compendium is imported. The store's state is the guard, so the call is
 * free once Ready and a `Failed` launch retries the next time Home is shown.
 */
class HomeViewModel(private val ctx: SealedLightContext) : LightViewModel<Unit>() {
    val state: StateFlow<ImportState> = CompendiumStore.state

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        CompendiumStore.ensureImported(ctx)
    }
}
