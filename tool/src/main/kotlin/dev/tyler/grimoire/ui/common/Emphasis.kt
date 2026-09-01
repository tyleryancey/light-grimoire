package dev.tyler.grimoire.ui.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp

/**
 * A line of text that can be **bold**, which `sdk:ui` cannot otherwise draw.
 *
 * `LightText` exposes no weight parameter at all (`LightText.kt:75-87`), and the variants that look
 * like an answer are not one: `Subheading` and `Copy` are both 30 sp `FontWeight.Normal`
 * (`LightTheme.kt:91-102`), differing only by 0.9 sp of tracking and a tighter line box, neither of
 * which reads as heavier type. The only working bold in the tool is the `BasicText` +
 * `buildAnnotatedString` + `SpanStyle(fontWeight = Bold)` path that `MarkdownBlocks`'s `InlineSpans`
 * already uses and M2 verified on hardware; this lifts it for a single run outside prose — S1's
 * bloodied HP numbers, and whatever else earns weight later.
 *
 * **Not bold is not a special case:** it delegates straight to `LightText`, so ordinary text drawn
 * through this helper is styled by exactly the same code as every other line in the tool and cannot
 * drift from it. Only the bold branch takes the hand-rolled path, and it takes the real variant's
 * style from [emphasisTextStyle] so the two branches share a size, a line box and a colour.
 */
@Composable
fun EmphasisText(
    text: String,
    variant: LightTextVariant,
    bold: Boolean = false,
    lighten: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    if (!bold) {
        LightText(
            text = text,
            variant = variant,
            modifier = modifier,
            lighten = lighten,
            maxLines = maxLines,
        )
        return
    }
    BasicText(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
        },
        modifier = modifier,
        style = emphasisTextStyle(variant, lighten),
        // `LightText`'s own default, so the bold branch clips where the plain branch clips.
        overflow = TextOverflow.Clip,
        maxLines = maxLines,
    )
}

/**
 * A `LightTextVariant`'s real style, ready for a `BasicText` — the one place the tool redoes the
 * SDK's type scaling.
 *
 * `LightText` reaches this by calling `TextStyle.scaledForScreenHeight()`, which is `internal` to
 * `:sdk:ui` (`LightText.kt:58`) and unreachable from a tool, so the same arithmetic is redone with
 * the public `designVerticalPxToSp` — including its guard: several variants set no letter spacing,
 * and an `Unspecified` unit must stay unspecified rather than become a NaN size.
 *
 * The colour is set on the style because `BasicText`, unlike `LightText`, inherits none. (`LightText`
 * itself does take a `color` parameter, `LightText.kt:86` — `BasicText` is needed for the weight, not
 * for the colour.)
 */
@Composable
fun emphasisTextStyle(variant: LightTextVariant, lighten: Boolean = false): TextStyle {
    val typography = LightThemeTokens.typography
    // Exhaustive on purpose: a variant the SDK adds must be given a style here, not silently mapped.
    val base = when (variant) {
        LightTextVariant.Title -> typography.title
        LightTextVariant.Subtitle -> typography.subtitle
        LightTextVariant.Heading -> typography.heading
        LightTextVariant.Subheading -> typography.subheading
        LightTextVariant.Copy -> typography.copy
        LightTextVariant.Button -> typography.button
        LightTextVariant.Paragraph -> typography.paragraph
        LightTextVariant.ParagraphWide -> typography.paragraphWide
        LightTextVariant.Detail -> typography.detail
        LightTextVariant.Fine -> typography.fine
        LightTextVariant.Superfine -> typography.superfine
        LightTextVariant.Micro -> typography.micro
    }
    val colors = LightThemeTokens.colors
    return base.copy(
        color = if (lighten) colors.contentSecondary else colors.content,
        fontSize = base.fontSize.scaledForScreen(),
        lineHeight = base.lineHeight.scaledForScreen(),
        letterSpacing = base.letterSpacing.scaledForScreen(),
    )
}

/** The SDK's per-unit scaling, with the `Unspecified` guard that keeps an unset tracking from becoming NaN. */
@Composable
internal fun TextUnit.scaledForScreen(): TextUnit =
    if (this == TextUnit.Unspecified) this else value.designVerticalPxToSp()
