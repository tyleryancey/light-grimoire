package dev.tyler.grimoire.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/** The UI-SPEC's default for a list screen: one detent moves the list three rows (S1, S13.1, S13.4). */
const val DEFAULT_ROWS_PER_DETENT = 3

/**
 * Turns a view model's wheel ticks into scrolling of a `LightLazyScrollView`'s
 * [LazyListState], [rowsPerDetent] rows per detent.
 *
 * The VM exposes `MutableSharedFlow<Int>(extraBufferCapacity = 16)` and, from `handleKey`,
 * `tryEmit(-1)` for key 317 (toward the top of the phone) / `tryEmit(+1)` for key 318
 * (toward the bottom) — so a positive tick scrolls down/forward. [rowHeightPx] is a lambda
 * because the composable that owns the list computes it from `gridUnitsAsDp` and
 * `LocalDensity` (never `LocalContext`/`LocalView` — lint bans them) and it can change
 * with configuration.
 *
 * [rowsPerDetent] defaults to [DEFAULT_ROWS_PER_DETENT], which is what every M2 caller and S1 want;
 * it exists so a screen whose rows are tall enough to want a finer step can say so in units of its
 * own rows instead of the caller multiplying [rowHeightPx] behind this function's back.
 */
@Composable
fun WheelScrollEffect(
    ticks: Flow<Int>,
    listState: LazyListState,
    rowsPerDetent: Int = DEFAULT_ROWS_PER_DETENT,
    rowHeightPx: () -> Float,
) {
    LaunchedEffect(ticks, listState, rowsPerDetent) {
        ticks.collect { tick ->
            listState.animateScrollBy(tick * rowsPerDetent * rowHeightPx())
        }
    }
}

/**
 * Turns a view model's wheel ticks into scrolling of a `LightScrollView`'s [ScrollState] by
 * [stepPx] per detent (readers step by a few text lines rather than list rows).
 *
 * Same contract as the [LazyListState] overload: the VM's `MutableSharedFlow<Int>`
 * (`extraBufferCapacity = 16`) carries `-1` for key 317 / `+1` for key 318, and the caller
 * computes [stepPx] from `gridUnitsAsDp` and `LocalDensity`.
 */
@Composable
fun WheelScrollEffect(ticks: Flow<Int>, scrollState: ScrollState, stepPx: () -> Float) {
    LaunchedEffect(ticks, scrollState) {
        ticks.collect { tick ->
            scrollState.animateScrollBy(tick * stepPx())
        }
    }
}
