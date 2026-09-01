package dev.tyler.grimoire.ui.keys

/**
 * One physical wheel gesture on the LP3 side wheel: a detent turn toward the top of the
 * phone ([UP]), a detent turn toward the bottom ([DOWN]), or a press of the wheel ([PRESS]).
 */
enum class WheelEvent {
    /** Key code 317 — one detent toward the top of the phone. */
    UP,

    /** Key code 318 — one detent toward the bottom of the phone. */
    DOWN,

    /** Key code 319 — the wheel pressed in. */
    PRESS,
}

/**
 * Maps raw LP3 key codes to [WheelEvent]s — the single place the wheel's numeric codes live.
 *
 * Repo-wide key convention (docs/UI-SPEC.md, wheel contract): view models implement
 * `fun handleKey(keyCode: Int): Boolean` and their screen's `onKeyDown` delegates to it,
 * never touching the `KeyEvent` parameter — `android.view.KeyEvent` is unconstructible in
 * JVM tests, so keeping the logic on the bare key code keeps every handler testable.
 *
 * Compendium screens consume all three wheel events: turns perform the screen's stated job
 * (scroll, or the spells list's level step) and the press is consumed as a no-op wherever
 * the screen defines no primary action — an unconsumed wheel event is forwarded to LightOS,
 * which foregrounds itself and relaunches the tool, a destructive context switch
 * mid-reading. Volume (24/25) and camera (80/27) keys are never consumed and still reach
 * LightOS; [of] returns null for them so a `handleKey` built on it falls through naturally.
 */
object WheelHandler {
    fun of(keyCode: Int): WheelEvent? = when (keyCode) {
        317 -> WheelEvent.UP // LightDeviceKeys.RotaryTurnUp — toward the top of the phone
        318 -> WheelEvent.DOWN // LightDeviceKeys.RotaryTurnDown — toward the bottom
        319 -> WheelEvent.PRESS // LightDeviceKeys.RotaryButtonPress
        else -> null // volume 24/25, camera 80/27 and everything else stay unconsumed
    }

    /**
     * Whether a screen that acts on this key must also swallow its other halves. Acting on a wheel
     * event in `onKeyDown` alone is not enough: `LightKeyHandler` defaults `onKeyUp` and
     * `onKeyMultiple` to false, and `LightActivity` forwards every unconsumed key it recognises to
     * LightOS with `componentToRelaunch` set — so the release of each detent would foreground
     * LightOS and relaunch the tool even though the turn itself was handled. Every wheel-consuming
     * view model therefore forwards `onKeyUp`/`onKeyMultiple` here, which consumes without acting
     * (routing the release through `handleKey` would double every detent).
     */
    fun consumes(keyCode: Int): Boolean = of(keyCode) != null
}
