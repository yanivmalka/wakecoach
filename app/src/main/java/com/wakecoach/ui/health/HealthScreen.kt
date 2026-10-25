package com.wakecoach.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.core.health.AlarmHealth
import com.wakecoach.core.health.HealthCheck
import com.wakecoach.core.health.HealthItem
import com.wakecoach.ui.theme.BigTouch

/** "Will my alarms ring?" – one row per phone setting that can silence an alarm, with a fix button. */
@Composable
fun HealthScreen(
    checks: List<HealthCheck>,
    testScheduled: Boolean,
    onFix: (HealthItem) -> Unit,
    onConfirmSamsung: () -> Unit,
    onTestAlarm: () -> Unit,
    onBack: () -> Unit,
) {
    val summary = AlarmHealth.summarize(checks)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.health_title), fontSize = 28.sp)
        Text(
            if (summary.allGood) stringResource(R.string.health_all_good)
            else LocalContext.current.resources.getQuantityString(R.plurals.health_problems, summary.problems, summary.problems),
            fontSize = 20.sp,
            color = if (summary.critical > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(AlarmHealth.ordered(checks), key = { it.item }) { check -> HealthRow(check, onFix, onConfirmSamsung) }
        }

        Button(onClick = onTestAlarm, enabled = !testScheduled, modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch)) {
            Text(
                stringResource(if (testScheduled) R.string.health_test_scheduled else R.string.health_test),
                fontSize = 19.sp,
            )
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.done), fontSize = 18.sp)
        }
    }
}

@Composable
private fun HealthRow(check: HealthCheck, onFix: (HealthItem) -> Unit, onConfirmSamsung: () -> Unit) {
    val (title, body) = texts(check.item)
    Surface(
        color = if (check.ok) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (check.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (check.ok) "✅" else if (check.item.critical) "⚠️" else "🔈", fontSize = 22.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(title), fontSize = 17.sp)
                if (!check.ok) Text(stringResource(body), fontSize = 14.sp)
            }
            if (!check.ok) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = { onFix(check.item) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(if (check.item == HealthItem.SAMSUNG_SLEEPING) R.string.health_open else R.string.health_fix))
                    }
                    // Samsung's list cannot be read back, so the user tells us once it is done.
                    if (check.item == HealthItem.SAMSUNG_SLEEPING) {
                        OutlinedButton(onClick = onConfirmSamsung, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.health_done_it))
                        }
                    }
                }
            }
        }
    }
}

private fun texts(item: HealthItem): Pair<Int, Int> = when (item) {
    HealthItem.NOTIFICATIONS -> R.string.health_notifications to R.string.health_notifications_body
    HealthItem.FULL_SCREEN -> R.string.health_fullscreen to R.string.health_fullscreen_body
    HealthItem.EXACT_ALARMS -> R.string.health_exact to R.string.health_exact_body
    HealthItem.NEXT_ALARM_REGISTERED -> R.string.health_armed to R.string.health_armed_body
    HealthItem.BATTERY_OPTIMIZATION -> R.string.health_battery to R.string.health_battery_body
    HealthItem.BACKGROUND_RESTRICTED -> R.string.health_background to R.string.health_background_body
    HealthItem.SAMSUNG_SLEEPING -> R.string.health_samsung to R.string.health_samsung_body
    HealthItem.ALARM_VOLUME -> R.string.health_volume to R.string.health_volume_body
    HealthItem.DND_SILENCE -> R.string.health_dnd to R.string.health_dnd_body
    HealthItem.STEP_ACCESS -> R.string.health_steps to R.string.health_steps_body
}
