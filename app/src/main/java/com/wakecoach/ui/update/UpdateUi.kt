package com.wakecoach.ui.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.update.UpdateState

/** Shown on the home screen while a newer build is available or downloading. */
@Composable
fun UpdateBanner(state: UpdateState, onOpen: () -> Unit) {
    val text = when (state) {
        is UpdateState.Available -> stringResource(R.string.update_available, state.release.versionName)
        is UpdateState.Downloading -> stringResource(R.string.update_downloading, (state.progress * 100).toInt())
        is UpdateState.Installing -> stringResource(R.string.update_installing)
        else -> return
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⬆", fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
            Text(text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun UpdateDialog(
    state: UpdateState,
    needsInstallPermission: Boolean,
    onUpdate: () -> Unit,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (state) {
                    is UpdateState.Available -> stringResource(R.string.update_available, state.release.versionName)
                    is UpdateState.Failed -> stringResource(R.string.update_failed)
                    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                    else -> stringResource(R.string.update_title)
                }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    is UpdateState.Available -> {
                        if (state.release.notes.isNotBlank()) {
                            Text(stringResource(R.string.update_whats_new), fontSize = 16.sp)
                            Text(state.release.notes, fontSize = 15.sp)
                        }
                        if (needsInstallPermission) {
                            Text(stringResource(R.string.update_permission_needed), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is UpdateState.Downloading -> {
                        Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()))
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().heightIn(min = 8.dp))
                    }
                    is UpdateState.Installing -> Text(stringResource(R.string.update_installing))
                    UpdateState.Checking -> Text(stringResource(R.string.update_checking))
                    is UpdateState.Failed -> Text(state.message)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when {
                state is UpdateState.Available && needsInstallPermission ->
                    Button(onClick = onGrantPermission) { Text(stringResource(R.string.update_allow)) }
                state is UpdateState.Available ->
                    Button(onClick = onUpdate) { Text(stringResource(R.string.update_now)) }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
        },
        dismissButton = {
            if (state is UpdateState.Available) TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
        },
    )
}
