package com.unplug.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// ================================================================
// Palette: black & white, in two directions
// ================================================================

@Immutable
data class Palette(val paper: Color, val ink: Color, val dim: Color, val faint: Color, val isDark: Boolean)

val DarkPalette = Palette(Color.Black, Color.White, Color(0xFF7A7A7A), Color(0xFF262626), isDark = true)
val LightPalette = Palette(Color.White, Color.Black, Color(0xFF8C8C8C), Color(0xFFE0E0E0), isDark = false)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** Background. */
val Paper: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.paper

/** Foreground. */
val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.ink

/** Secondary text. */
val Dim: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.dim

/** Hairlines and inactive marks. */
val Faint: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.faint

/** Instrument labels always stay monospace, whatever font is chosen. */
val Mono = FontFamily.Monospace

/** Renders app icons in grayscale so the screen stays monochrome. */
val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

const val MIN = 60_000L

@Composable
fun UnplugTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    CompositionLocalProvider(
        LocalSettings provides settings,
        LocalPalette provides if (dark) DarkPalette else LightPalette,
        LocalTextStyle provides TextStyle(fontFamily = settings.font.family(context)),
        content = content,
    )
}

/** Big numbers: chosen font + chosen clock weight, with even-width digits. */
@Composable
fun digitsStyle(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(fontFeatureSettings = "tnum", fontWeight = LocalSettings.current.clockWeight.weight)
)

/** Haptic feedback that respects the "Haptic feedback" setting. */
@Composable
fun rememberHaptics(): (HapticFeedbackType) -> Unit {
    val haptics = LocalHapticFeedback.current
    val enabled = LocalSettings.current.haptics
    return remember(haptics, enabled) { { type -> if (enabled) haptics.performHapticFeedback(type) } }
}

// ================================================================
// Press feedback: a soft spring "squish" instead of a ripple
// ================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tap(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

// ================================================================
// Text
// ================================================================

@Composable
fun Body(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        color = Dim,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

/** Small monospace caps — the "instrument panel" voice of the UI. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Dim) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontFamily = Mono,
        maxLines = 1,
    )
}

// ================================================================
// Controls
// ================================================================

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Box(
        modifier
            .tap(onClick = onClick)
            .height(56.dp)
            .background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        ButtonText(text, Paper)
    }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .tap(onClick = onClick)
            .height(56.dp)
            .border(1.dp, Ink),
        contentAlignment = Alignment.Center,
    ) {
        ButtonText(text, Ink)
    }
}

@Composable
private fun ButtonText(text: String, color: Color) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 13.sp,
        letterSpacing = 3.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = Mono,
    )
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = Mono,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .tap(onClick = onClick)
            .height(40.dp)
            .then(if (selected) Modifier.background(Ink) else Modifier.border(1.dp, Faint)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Paper else Dim,
            fontSize = 13.sp,
            fontFamily = fontFamily,
            maxLines = 1,
        )
    }
}

/** Monochrome on/off switch with a springy knob. */
@Composable
fun MonoSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "knob",
    )
    Box(
        Modifier
            .tap(onClick = { onChange(!checked) })
            .size(width = 46.dp, height = 26.dp)
            .border(1.dp, if (checked) Ink else Faint, RoundedCornerShape(13.dp))
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(20.dp)
                .background(if (checked) Ink else Dim, CircleShape)
        )
    }
}

@Composable
fun RoundIconButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .tap(onClick = onClick)
            .size(44.dp)
            .border(1.dp, Faint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Dim,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

/** A minimal list of actions in a hairline box (used for long-press menus). */
@Composable
fun ActionSheet(title: String, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Paper)
                .border(1.dp, Faint)
                .padding(vertical = 12.dp)
        ) {
            Label(title, Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            actions.forEach { (text, action) ->
                Text(
                    text,
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            action()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
        }
    }
}

// ================================================================
// Formatting
// ================================================================

fun durationNumber(minutes: Int): String =
    if (minutes < 60) "$minutes" else "%d:%02d".format(minutes / 60, minutes % 60)

fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

fun formatCountdown(ms: Long): String {
    val total = (ms + 999) / 1000 // round up so it never shows 00:00 while still locked
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
