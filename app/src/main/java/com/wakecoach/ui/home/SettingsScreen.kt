package com.wakecoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.data.UserSettings
import com.wakecoach.ui.theme.BigTouch
import com.wakecoach.ui.theme.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    onBack: () -> Unit,
    version: String,
    onCheckUpdates: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.settings), fontSize = 28.sp)

        Text(stringResource(R.string.theme), fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                ThemeMode.SYSTEM to R.string.theme_system,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.DARK to R.string.theme_dark,
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = settings.themeMode == mode, onClick = { onChange { it.copy(themeMode = mode) } },
                    label = { Text(stringResource(label), fontSize = 18.sp) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Text(stringResource(R.string.hand), fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = !settings.leftHanded, onClick = { onChange { it.copy(leftHanded = false) } },
                label = { Text(stringResource(R.string.hand_right), fontSize = 18.sp) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            FilterChip(
                selected = settings.leftHanded, onClick = { onChange { it.copy(leftHanded = true) } },
                label = { Text(stringResource(R.string.hand_left), fontSize = 18.sp) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }

        // Local while dragging; persisting reschedules alarms, so only do it on release.
        var goal by remember(settings.sleepGoalMinutes) { mutableIntStateOf(settings.sleepGoalMinutes) }
        Text(stringResource(R.string.sleep_goal) + ": " + stringResource(R.string.hours_value, goal / 60f), fontSize = 18.sp)
        Slider(
            value = goal.toFloat(),
            onValueChange = { goal = (it / 15).roundToInt() * 15 },
            onValueChangeFinished = { onChange { it.copy(sleepGoalMinutes = goal) } },
            valueRange = 300f..600f,
            steps = 19,
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.bedtime_reminders), fontSize = 18.sp, modifier = Modifier.weight(1f))
            Switch(checked = settings.bedtimeReminders, onCheckedChange = { b -> onChange { it.copy(bedtimeReminders = b) } })
        }

        OutlinedButton(onClick = onOpenHealth, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("🔔 " + stringResource(R.string.health_menu), fontSize = 18.sp)
        }

        Text(stringResource(R.string.updates), fontSize = 18.sp)
        Text(stringResource(R.string.version_n, version), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.auto_check_updates), fontSize = 18.sp, modifier = Modifier.weight(1f))
            Switch(checked = settings.autoCheckUpdates, onCheckedChange = { b -> onChange { it.copy(autoCheckUpdates = b) } })
        }
        OutlinedButton(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.check_updates), fontSize = 18.sp)
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch)) {
            Text(stringResource(R.string.done), fontSize = 20.sp)
        }
    }
}
