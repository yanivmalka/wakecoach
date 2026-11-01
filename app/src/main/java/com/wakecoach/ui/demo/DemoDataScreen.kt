package com.wakecoach.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.data.DemoData
import com.wakecoach.data.DemoPersona
import com.wakecoach.ui.theme.BigTouch
import kotlinx.coroutines.launch

/** Fill the app with a ready-made persona's alarms and history, or wipe everything. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoDataScreen(demo: DemoData, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var persona by remember { mutableStateOf(DemoPersona.STUDENT) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val addedText = stringResource(R.string.demo_added)
    val resetText = stringResource(R.string.demo_reset_done)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("🧪 " + stringResource(R.string.demo_title), fontSize = 28.sp)
        Text(stringResource(R.string.demo_explain), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(stringResource(R.string.demo_pick_persona), fontSize = 18.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoPersona.entries.forEach { p ->
                FilterChip(
                    selected = p == persona, onClick = { persona = p },
                    label = { Text(p.emoji + " " + stringResource(p.title), fontSize = 16.sp) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Button(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    val r = demo.add(persona)
                    status = addedText.format(r.alarms, r.mornings)
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.demo_add, stringResource(persona.title)), fontSize = 18.sp) }

        status?.let { Text("✅ $it", fontSize = 16.sp) }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.demo_reset_title), fontSize = 18.sp)
        Text(stringResource(R.string.demo_reset_explain), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            enabled = !busy,
            onClick = { confirmReset = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("🗑 " + stringResource(R.string.demo_reset), fontSize = 18.sp) }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch)) {
            Text(stringResource(R.string.done), fontSize = 20.sp)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.demo_reset_confirm_title)) },
            text = { Text(stringResource(R.string.demo_reset_explain)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    busy = true
                    scope.launch {
                        demo.resetAll()
                        status = resetText
                        busy = false
                    }
                }) { Text(stringResource(R.string.demo_reset), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
