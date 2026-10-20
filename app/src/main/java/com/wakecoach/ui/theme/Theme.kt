package com.wakecoach.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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

val Night = Color(0xFF0E1626)
val NightCard = Color(0xFF1B2A49)
val Sun = Color(0xFFFFC857)
val Mist = Color(0xFFDCE3F0)

private val scheme = darkColorScheme(
    primary = Sun,
    onPrimary = Night,
    background = Night,
    onBackground = Mist,
    surface = NightCard,
    onSurface = Mist,
    secondary = Color(0xFF7FB7BE),
)

@Composable
fun WakeCoachTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

/** Minimum touch target for half-asleep thumbs. */
val BigTouch = 64.dp

/** Clock digits that fill the available width; always LTR, even in Hebrew. */
@Composable
fun BigDigits(text: String, modifier: Modifier = Modifier, color: Color = Mist, widthFraction: Float = 1f) {
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
