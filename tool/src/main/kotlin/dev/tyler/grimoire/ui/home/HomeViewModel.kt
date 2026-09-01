package dev.tyler.grimoire.ui.home

import android.view.KeyEvent
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.compendium.CompendiumStore
import dev.tyler.grimoire.compendium.ImportState
import dev.tyler.grimoire.ui.keys.WheelHandler
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

    /**
     * Home has nothing to scroll and no primary action, but it still swallows the wheel: an unconsumed wheel
     * event is forwarded to LightOS, which foregrounds itself and relaunches the tool. Home is the screen the
     * tool opens on and the one a player watches for the 2.3 s import, so a thumb resting on the wheel there
     * is the likeliest place in the tool to be bounced out. See [WheelHandler.consumes] for why all three
     * halves matter.
     */
    fun consumesKey(keyCode: Int): Boolean = WheelHandler.consumes(keyCode)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = consumesKey(keyCode)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = consumesKey(keyCode)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean = consumesKey(keyCode)
}
