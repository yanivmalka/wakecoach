package com.wakecoach.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.core.feedback.Progress
import com.wakecoach.data.WakeRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** Single-hue bar fill, validated per surface (light #FBF8F3, dark #0E1626) with the dataviz checks. */
private val BarLight = Color(0xFF8A5A00)
private val BarDark = Color(0xFFC08020)

/** Weekly progress (requirement 13): streaks, snoozes and time to get up, with a word of praise. */
@Composable
fun ProgressScreen(repository: WakeRepository, onBack: () -> Unit) {
    var progress by remember { mutableStateOf<Progress?>(null) }
    LaunchedEffect(Unit) {
        progress = Progress.from(
            repository.recentOutcomes(), ZoneId.systemDefault(), LocalDate.now(),
            WeekFields.of(Locale.getDefault()).firstDayOfWeek, count = 6,
        )
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.progress_title), fontSize = 28.sp)
        val p = progress
        if (p == null) {
            // Still loading; the query is local and fast.
        } else if (p.weeks.all { it.mornings == 0 }) {
            Text(stringResource(R.string.progress_empty), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val thisWeek = p.weeks.last()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(stringResource(R.string.progress_streak), p.currentStreak.toString(), Modifier.weight(1f))
                Tile(stringResource(R.string.progress_best), p.bestStreak.toString(), Modifier.weight(1f))
                Tile(
                    stringResource(R.string.progress_snoozes_week),
                    if (thisWeek.mornings == 0) "–" else "%.1f".format(thisWeek.avgSnoozes),
                    Modifier.weight(1f),
                )
            }
            praise(p)?.let { Text(it, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary) }

            val fmt = DateTimeFormatter.ofPattern("d/M")
            val labels = p.weeks.map { it.weekStart.format(fmt) }
            // Two measures, two charts: never a second y-axis.
            BarChart(
                title = stringResource(R.string.progress_chart_snoozes),
                values = p.weeks.map { if (it.mornings == 0) null else it.avgSnoozes },
                labels = labels,
                format = { "%.1f".format(it) },
            )
            BarChart(
                title = stringResource(R.string.progress_chart_minutes),
                values = p.weeks.map { if (it.mornings == 0) null else it.avgMinutesToUp },
                labels = labels,
                format = { "%.0f".format(it) },
            )
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.done), fontSize = 18.sp)
        }
    }
}

@Composable
private fun praise(p: Progress): String? {
    val better = p.snoozeImprovement ?: return null
    return when {
        better >= 0.1 -> stringResource(R.string.progress_better, "%.1f".format(better))
        better <= -0.1 -> stringResource(R.string.progress_worse)
        else -> null
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(label, fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One series, one axis: thin bars rounded only at the data end and anchored to a hairline
 * baseline, the latest value labelled directly, and a tap on any bar to read its value.
 * Weeks without data leave a gap rather than a zero bar.
 */
@Composable
private fun BarChart(title: String, values: List<Double?>, labels: List<String>, format: (Double) -> String) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bar = if (dark) BarDark else BarLight
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val max = (values.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val lastIndex = values.indexOfLast { it != null }
    var selected by remember(values) { mutableStateOf(lastIndex) }
    val description = labels.zip(values).joinToString("; ") { (l, v) -> "$l: ${v?.let(format) ?: "–"}" }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 17.sp, color = ink)
        // Time runs left to right in every language, like the clock digits.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.semantics { contentDescription = "$title. $description" }) {
                Box(Modifier.fillMaxWidth().height(24.dp)) {
                    val v = values.getOrNull(selected)
                    if (v != null) {
                        // Readout for the tapped (or latest) week.
                        Text(
                            "${labels[selected]}: ${format(v)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                        )
                    }
                }
                Canvas(
                    Modifier.fillMaxWidth().height(140.dp).pointerInput(values) {
                        detectTapGestures { pos ->
                            val slot = size.width / values.size
                            val i = (pos.x / slot).toInt().coerceIn(0, values.lastIndex)
                            if (values[i] != null) selected = i
                        }
                    },
                ) {
                    val slot = size.width / values.size
                    val barWidth = (slot * 0.5f).coerceAtMost(28.dp.toPx())
                    val radius = 4.dp.toPx()
                    values.forEachIndexed { i, v ->
                        if (v == null) return@forEachIndexed
                        val h = (v / max * (size.height - radius)).toFloat().coerceAtLeast(2.dp.toPx())
                        val left = slot * i + (slot - barWidth) / 2
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = left, top = size.height - h, right = left + barWidth, bottom = size.height,
                                    topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                                    bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                                )
                            )
                        }
                        // The selected week stays full strength; the others recede slightly.
                        drawPath(path, if (i == selected) bar else bar.copy(alpha = 0.55f))
                    }
                    drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                }
                Row(Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { i, l ->
                        Text(
                            l, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                            color = if (i == selected) ink else muted,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
