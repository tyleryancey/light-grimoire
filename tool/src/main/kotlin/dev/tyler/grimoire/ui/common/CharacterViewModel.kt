package dev.tyler.grimoire.ui.common

import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.grimoire.data.CharacterRepository

/**
 * The base class for every screen that edits a character (S1, S3, and the sheet screens after them): it
 * owns the one thing a per-screen contract cannot be trusted with — calling
 * [CharacterRepository.flush] on every path out of the screen.
 *
 * **Why this is a base class and not a note in each view model.** `flush` is the only exit signal the SDK
 * gives, and no single hook is a superset of the others:
 *
 * - `onScreenHide` fires when the screen is popped **and** when it pushes another screen on top of itself,
 *   but not when LightOS takes the foreground (a wheel event the tool did not consume, an incoming call).
 * - `onAppPause` fires on `Activity.onPause` — the process going away — but not on a back press.
 * - `onCleared` fires when the view model store is cleared, which `LightActivity.goBack()` does
 *   synchronously in the same call as `notifyWillHide()`.
 *
 * There is no `onStop`/`onDestroy` hook in the SDK, so those three are the whole surface. A contract that
 * says "remember to flush in both places", repeated once per screen, is a contract that ships broken the
 * first time a screen is added by someone reading a different example. Extending this class is the only
 * way to get a character view model, and the flushes come with it.
 *
 * Flushing more than once is free: [CharacterRepository.flush] brings pending deadlines forward and
 * returns, and a buffer with nothing in it is a no-op. A back press therefore flushes twice (hide, then
 * cleared) on purpose — deduplicating would trade a harmless repeat for a state machine that can be wrong.
 *
 * [flushNow] exists because two of the three overrides cannot be driven from the JVM gate:
 * `onScreenHide` takes a `SimpleLightScreen`, which needs a real activity, and `ViewModel.onCleared` is
 * `protected` in the framework. The override below widens it to public so a test can call it; every
 * override's body is a single delegating call, so a test that proves [flushNow] flushes and that
 * `onAppPause`/`onCleared` reach it has covered the third by inspection.
 */
abstract class CharacterViewModel<T>(
    protected val repo: CharacterRepository,
    protected val characterId: String,
) : LightViewModel<T>() {
    /** Write every pending save now. The body of all three exit hooks, and callable from a JVM test. */
    fun flushNow() {
        repo.flush()
    }

    /** Popped, or a deeper screen pushed on top. */
    override fun onScreenHide(screen: SimpleLightScreen<T>) {
        super.onScreenHide(screen)
        flushNow()
    }

    /** The process going to the background — the only hook a LightOS foreground steal fires. */
    override fun onAppPause() {
        super.onAppPause()
        flushNow()
    }

    /** `goBack()` clears the view model store synchronously; widened to public so a test can call it. */
    public override fun onCleared() {
        super.onCleared()
        flushNow()
    }
}
