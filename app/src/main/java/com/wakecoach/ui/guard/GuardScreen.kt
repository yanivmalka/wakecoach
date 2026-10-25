package com.wakecoach.ui.guard

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.core.guard.GuardDecision
import com.wakecoach.core.guard.Slot
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.data.GuardRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** "These regular alarms are missing for [date]": add or skip each, or all at once. */
@Composable
fun GuardScreen(date: LocalDate, repo: GuardRepository, onChanged: suspend () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var missing by remember { mutableStateOf<List<Slot>?>(null) }
    var decisions by remember { mutableStateOf<List<GuardDecision>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        val notBefore = if (date == LocalDate.now()) LocalDateTime.now() else null
        missing = repo.missing(date, notBefore)
        decisions = repo.decisions()
    }
    fun act(block: suspend () -> Unit) = scope.launch { block(); onChanged(); refresh++ }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.guard_title), fontSize = 28.sp)
        val day = stringResource(if (date == LocalDate.now()) R.string.guard_today else R.string.guard_tomorrow)
        val list = missing
        when {
            list == null -> Unit
            list.isEmpty() -> Text(stringResource(R.string.guard_all_covered, day), fontSize = 19.sp, color = MaterialTheme.colorScheme.primary)
            else -> {
                Text(stringResource(R.string.guard_intro, day), fontSize = 18.sp)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.key }) { slot ->
                        SlotRow(
                            slot,
                            offerStop = repo.shouldOfferStop(slot, decisions),
                            onAdd = { act { repo.addAlarms(listOf(slot), date) } },
                            onSkip = { act { repo.notThisTime(listOf(slot), date) } },
                            onStop = { act { repo.stopTracking(slot) } },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { act { repo.notThisTime(list, date) } }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.guard_not_this_time))
                    }
                    Button(onClick = { act { repo.addAlarms(list, date) } }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.guard_add_all))
                    }
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.done), fontSize = 18.sp)
        }
    }
}

@Composable
private fun SlotRow(slot: Slot, offerStop: Boolean, onAdd: () -> Unit, onSkip: () -> Unit, onStop: () -> Unit) {
    val title = if (slot.kind == AlarmKind.WAKE) "⏰ " + stringResource(R.string.kind_wake) else "🔔 ${slot.label}"
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(slot.time.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 22.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.guard_add)) }
                TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.guard_not_this_time)) }
            }
            // After three skips in a row, offer to stop asking about this one.
            if (offerStop) {
                TextButton(onClick = onStop) { Text(stringResource(R.string.guard_stop_tracking)) }
            }
        }
    }
}
