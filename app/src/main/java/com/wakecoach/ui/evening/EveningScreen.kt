package com.wakecoach.ui.evening

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.ui.components.WheelPicker
import com.wakecoach.ui.theme.BigTouch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val PRESETS = listOf(LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(13, 0), LocalTime.of(17, 0))

/**
 * "What matters to you tomorrow?" (requirement 6). Shown and spoken on the morning screen, and
 * optionally repeated during the day by one-time reminders the user picks here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EveningScreen(
    load: suspend () -> String,
    loadReminders: suspend () -> List<LocalTime>,
    onSave: (String, List<LocalTime>) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var reminders by remember { mutableStateOf(emptyList<LocalTime>()) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        text = load()
        reminders = loadReminders()
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()
            .padding(20.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.evening_title), fontSize = 30.sp)
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.evening_hint)) },
                textStyle = TextStyle(fontSize = 22.sp),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("🔔 " + stringResource(R.string.goal_reminders_title), fontSize = 20.sp)
            Text(
                stringResource(if (text.isBlank()) R.string.goal_reminders_need_text else R.string.goal_reminders_explain),
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (PRESETS + reminders).distinct().sorted().forEach { t ->
                    val on = t in reminders
                    FilterChip(
                        selected = on, enabled = text.isNotBlank(),
                        onClick = { reminders = if (on) reminders - t else (reminders + t).sorted() },
                        label = { Text(t.format(HHMM), fontSize = 18.sp) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                AssistChip(
                    enabled = text.isNotBlank(), onClick = { picking = true },
                    label = { Text("＋ " + stringResource(R.string.goal_reminder_other_time), fontSize = 16.sp) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = BigTouch)) {
                Text(stringResource(R.string.cancel), fontSize = 18.sp)
            }
            Button(onClick = { onSave(text, reminders) }, modifier = Modifier.weight(2f).heightIn(min = BigTouch)) {
                Text(stringResource(R.string.save), fontSize = 20.sp)
            }
        }
    }

    if (picking) TimeDialog(
        onPick = { t -> picking = false; reminders = (reminders + t).distinct().sorted() },
        onDismiss = { picking = false },
    )
}

@Composable
private fun TimeDialog(onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    var hour by remember { mutableIntStateOf(12) }
    var minute by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_reminder_other_time)) },
        text = {
            // Clock wheels are always LTR, as on the alarm editor.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WheelPicker(
                        count = 24, value = hour, onValueChange = { hour = it },
                        label = stringResource(R.string.hours), modifier = Modifier.width(96.dp),
                    )
                    Text(":", fontSize = 32.sp, modifier = Modifier.padding(horizontal = 6.dp))
                    WheelPicker(
                        count = 60, value = minute, onValueChange = { minute = it },
                        label = stringResource(R.string.minutes), modifier = Modifier.width(96.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(hour, minute)) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
