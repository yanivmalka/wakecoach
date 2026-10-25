package com.wakecoach.ui.home

import com.wakecoach.data.repeatDays
import com.wakecoach.data.time
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.core.schedule.AlarmTimeCalculator
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.data.AlarmEntity
import com.wakecoach.data.alarmKind
import com.wakecoach.ui.profileName
import com.wakecoach.ui.theme.BigDigits
import com.wakecoach.ui.theme.BigTouch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(
    alarms: List<AlarmEntity>,
    leftHanded: Boolean,
    healthProblem: Boolean,
    onOpenHealth: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (AlarmEntity) -> Unit,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onEvening: () -> Unit,
    onSettings: () -> Unit,
    onProgress: () -> Unit,
    updateBanner: @Composable () -> Unit = {},
) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { now = ZonedDateTime.now(); delay(1000) }
    }
    val next = alarms.filter { it.enabled }
        .map { AlarmTimeCalculator.nextTrigger(it.time, it.repeatDays, now) }
        .minOrNull()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp),
    ) {
        BigDigits(now.format(HHMM), Modifier.fillMaxWidth().height(140.dp))
        Text(
            text = next?.let {
                val d = Duration.between(now, it)
                stringResource(R.string.next_alarm_in, stringResource(R.string.duration_h_m, d.toHours(), d.toMinutes() % 60))
            } ?: stringResource(R.string.no_alarms),
            color = MaterialTheme.colorScheme.primary, fontSize = 20.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (healthProblem) {
            FilledTonalButton(onClick = onOpenHealth, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(stringResource(R.string.health_banner))
            }
        }
        updateBanner()
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(alarm, onClick = { onEdit(alarm) }, onToggle = { onToggle(alarm, it) })
            }
        }

        // Thumb zone: primary actions at the bottom, biggest button on the thumb's side.
        ThumbRow(leftHanded) {
            FilledTonalButton(onClick = onSettings, modifier = Modifier.size(BigTouch)) { Text("⚙", fontSize = 22.sp) }
            FilledTonalButton(onClick = onProgress, modifier = Modifier.size(BigTouch)) { Text("📈", fontSize = 22.sp) }
            FilledTonalButton(onClick = onEvening, modifier = Modifier.heightIn(min = BigTouch)) {
                Text(stringResource(R.string.evening_button), fontSize = 18.sp)
            }
            Button(onClick = onAdd, modifier = Modifier.size(BigTouch + 16.dp), shape = RoundedCornerShape(50)) {
                Text("+", fontSize = 34.sp)
            }
        }
    }
}

/**
 * Lays children out so the last (primary) child sits under the user's thumb: physical right
 * for right-handed, left for left-handed, regardless of the app's text direction.
 */
@Composable
fun ThumbRow(leftHanded: Boolean, content: @Composable RowScope.() -> Unit) {
    val direction = if (leftHanded) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun AlarmRow(alarm: AlarmEntity, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val icon = if (alarm.alarmKind == AlarmKind.REMINDER) "🔔 " else "⏰ "
            Text(
                icon + LocalTime.of(alarm.hour, alarm.minute).format(HHMM), fontSize = 44.sp, fontWeight = FontWeight.Light,
                color = if (alarm.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            val days = if (alarm.repeatDays.isEmpty()) stringResource(R.string.repeat_once)
            else alarm.repeatDays.sorted().joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            val profile = if (alarm.alarmKind == AlarmKind.REMINDER) "" else stringResource(profileName(alarm.profileId))
            Text(
                listOf(alarm.label, days, profile).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = alarm.enabled, onCheckedChange = onToggle)
    }
}
