package com.wakecoach.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.wakecoach.container
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Look packs: a full light and dark color set each. Names are stored; never rename one.
 * [PHONE] takes the phone's own wallpaper colors (Android 12+), else falls back to [SUNRISE].
 */
enum class ThemePack { SUNRISE, OCEAN, FOREST, LAVENDER, EMBER, CONTRAST, PHONE }

/** The few colors a pack picks; the rest of the Material scheme is derived from them. */
private class Palette(
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val secondaryContainer: Color, val onSecondaryContainer: Color,
    val background: Color, val onBackground: Color,
    val surfaceVariant: Color, val onSurfaceVariant: Color, val surfaceHigh: Color, val outline: Color,
)

private fun Palette.scheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = if (dark) background else Color.White,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        background = background, onBackground = onBackground, surface = background, onSurface = onBackground,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surfaceVariant, surfaceContainerHigh = surfaceHigh, outline = outline,
        outlineVariant = surfaceHigh,
    )
}

private val packs: Map<ThemePack, Pair<Palette, Palette>> = mapOf(
    // light to dark pairs
    ThemePack.OCEAN to (Palette(
        Color(0xFF00658E), Color.White, Color(0xFFC7E7FF), Color(0xFF001E2E),
        Color(0xFF4E616D), Color(0xFFD1E5F4), Color(0xFF0A1E28),
        Color(0xFFF6FAFE), Color(0xFF171C1F), Color(0xFFDDE8F0), Color(0xFF3F484E), Color(0xFFCFDCE6), Color(0xFF6F7880),
    ) to Palette(
        Color(0xFF85CFFF), Color(0xFF00344C), Color(0xFF004C6C), Color(0xFFC7E7FF),
        Color(0xFFB5C9D7), Color(0xFF213A48), Color(0xFFD1E5F4),
        Color(0xFF0B1720), Color(0xFFDEE3E8), Color(0xFF172837), Color(0xFFBFC8CF), Color(0xFF203445), Color(0xFF89929A),
    )),
    ThemePack.FOREST to (Palette(
        Color(0xFF2E6B30), Color.White, Color(0xFFB1F2A8), Color(0xFF002204),
        Color(0xFF52634F), Color(0xFFD5E8CF), Color(0xFF101F0F),
        Color(0xFFF7FBF1), Color(0xFF191D17), Color(0xFFDEE5D8), Color(0xFF424940), Color(0xFFD0D9CA), Color(0xFF72796F),
    ) to Palette(
        Color(0xFF96D68E), Color(0xFF00390A), Color(0xFF145218), Color(0xFFB1F2A8),
        Color(0xFFB9CCB4), Color(0xFF233523), Color(0xFFD5E8CF),
        Color(0xFF0F1A10), Color(0xFFE0E4DB), Color(0xFF1C2B1C), Color(0xFFC2C9BD), Color(0xFF253826), Color(0xFF8C9388),
    )),
    ThemePack.LAVENDER to (Palette(
        Color(0xFF6750A4), Color.White, Color(0xFFE9DDFF), Color(0xFF22005D),
        Color(0xFF625B71), Color(0xFFE8DEF8), Color(0xFF1E192B),
        Color(0xFFFDF8FD), Color(0xFF1C1B1E), Color(0xFFE7E0EB), Color(0xFF49454E), Color(0xFFDAD2DF), Color(0xFF7A757F),
    ) to Palette(
        Color(0xFFCFBCFF), Color(0xFF381E72), Color(0xFF4F378A), Color(0xFFE9DDFF),
        Color(0xFFCBC2DB), Color(0xFF332D41), Color(0xFFE8DEF8),
        Color(0xFF141221), Color(0xFFE6E1E6), Color(0xFF231F33), Color(0xFFCAC4CF), Color(0xFF2E2942), Color(0xFF948F99),
    )),
    ThemePack.EMBER to (Palette(
        Color(0xFFA23F16), Color.White, Color(0xFFFFDBCF), Color(0xFF390C00),
        Color(0xFF77574C), Color(0xFFFFDBCF), Color(0xFF2C160D),
        Color(0xFFFFF8F6), Color(0xFF221A17), Color(0xFFF5DED6), Color(0xFF53433E), Color(0xFFE9D3CB), Color(0xFF85736D),
    ) to Palette(
        Color(0xFFFFB59A), Color(0xFF5C1900), Color(0xFF822800), Color(0xFFFFDBCF),
        Color(0xFFE7BDAF), Color(0xFF442A21), Color(0xFFFFDBCF),
        Color(0xFF1A110E), Color(0xFFF1DFDA), Color(0xFF2E201B), Color(0xFFD8C2BA), Color(0xFF3A2A24), Color(0xFFA08D86),
    )),
    // Maximum contrast for low vision or a bleary first look: black, white and one bright accent.
    ThemePack.CONTRAST to (Palette(
        Color(0xFF000000), Color.White, Color(0xFFFFE500), Color(0xFF000000),
        Color(0xFF000000), Color(0xFFFFE500), Color(0xFF000000),
        Color.White, Color.Black, Color(0xFFEDEDED), Color.Black, Color(0xFFD6D6D6), Color.Black,
    ) to Palette(
        Color(0xFFFFE500), Color.Black, Color(0xFFFFE500), Color.Black,
        Color.White, Color(0xFFFFE500), Color.Black,
        Color.Black, Color.White, Color(0xFF1A1A1A), Color.White, Color(0xFF2E2E2E), Color.White,
    )),
)

/** Light and dark scheme of [pack], for the app and for the picker's previews. */
@Composable
fun packScheme(pack: ThemePack, dark: Boolean): ColorScheme {
    if (pack == ThemePack.PHONE && android.os.Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        return if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    }
    return packs[pack]?.let { (light, darkP) -> if (dark) darkP.scheme(true) else light.scheme(false) }
        ?: if (dark) DarkColors else LightColors
}

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
fun WakeCoachTheme(mode: ThemeMode = ThemeMode.SYSTEM, pack: ThemePack? = null, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    // Screens that do not pass a pack (ring, call, feedback) follow the stored one.
    val ctx = LocalContext.current
    val packFlow = remember(ctx) { ctx.container.settings.settings.map { it.themePack } }
    val stored by packFlow.collectAsState(initial = pack ?: ThemePack.SUNRISE)
    val scheme: ColorScheme = packScheme(pack ?: stored, dark)
    // Status/nav bar icons follow the app theme, not the system one, so they stay readable
    // when the user forces light or dark.
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark, activity) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
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
