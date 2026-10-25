package com.wakecoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Sun = Color(0xFFFFC857)
private val SunDeep = Color(0xFF8A5A00)

private val DarkColors = darkColorScheme(
    primary = Sun,
    onPrimary = Color(0xFF2A1B00),
    primaryContainer = Color(0xFF5C4100),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFF9CCBD1),
    onSecondary = Color(0xFF00363B),
    secondaryContainer = Color(0xFF26404A),
    onSecondaryContainer = Color(0xFFD3EEF2),
    background = Color(0xFF0E1626),
    onBackground = Color(0xFFE3E8F2),
    surface = Color(0xFF0E1626),
    onSurface = Color(0xFFE3E8F2),
    surfaceVariant = Color(0xFF1C2A44),
    onSurfaceVariant = Color(0xFFC3CBDA),
    surfaceContainer = Color(0xFF1C2A44),
    surfaceContainerHigh = Color(0xFF243452),
    outline = Color(0xFF8A94A8),
    outlineVariant = Color(0xFF3A4760),
)

private val LightColors = lightColorScheme(
    primary = SunDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA6),
    onPrimaryContainer = Color(0xFF2A1B00),
    secondary = Color(0xFF3D6870),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EEF2),
    onSecondaryContainer = Color(0xFF00363B),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1B1C1E),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1B1C1E),
    surfaceVariant = Color(0xFFEDE6DA),
    onSurfaceVariant = Color(0xFF4C463C),
    surfaceContainer = Color(0xFFF2ECE2),
    surfaceContainerHigh = Color(0xFFEDE6DA),
    outline = Color(0xFF7D766B),
    outlineVariant = Color(0xFFD0C6B8),
)

fun ThemeMode.isDark(systemDark: Boolean) = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Wraps content in a Surface so every Text inherits a readable content color;
 * without it Compose falls back to black text regardless of the background.
 */
@Composable
fun WakeCoachTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (mode.isDark(isSystemInDarkTheme())) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme) {
        Surface(color = scheme.background, contentColor = scheme.onBackground, content = content)
    }
}

/** Minimum touch target for half-asleep thumbs. */
val BigTouch = 64.dp

/** Clock digits that fill the available width; always LTR, even in Hebrew. */
@Composable
fun BigDigits(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    widthFraction: Float = 1f,
) {
    BoxWithConstraints(modifier) {
        // A digit is roughly 0.6em wide; size the string to fill the box.
        val size = (maxWidth.value * widthFraction / (text.length * 0.62f)).sp
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text, color = color, fontSize = size, fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center, maxLines = 1, lineHeight = size,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
