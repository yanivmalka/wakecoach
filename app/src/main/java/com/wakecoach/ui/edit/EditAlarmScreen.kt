package com.wakecoach.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.core.model.Profiles
import com.wakecoach.data.AlarmEntity
import com.wakecoach.data.alarmKind
import com.wakecoach.data.repeatDays
import com.wakecoach.ui.components.DayCircles
import com.wakecoach.ui.components.SoundPickerRow
import com.wakecoach.ui.components.WheelPicker
import com.wakecoach.ui.home.ThumbRow
import com.wakecoach.ui.profileDescription
import com.wakecoach.ui.profileName
import com.wakecoach.ui.theme.BigTouch

@Composable
fun EditAlarmScreen(
    initial: AlarmEntity?,
    leftHanded: Boolean,
    onSave: (AlarmEntity) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    onCancel: () -> Unit,
) {
    val base = initial ?: AlarmEntity(hour = 7, minute = 0)
    var kind by remember { mutableStateOf(base.alarmKind) }
    var hour by remember { mutableIntStateOf(base.hour) }
    var minute by remember { mutableIntStateOf(base.minute) }
    var days by remember { mutableStateOf(base.repeatDays) }
    var profileId by remember { mutableStateOf(base.profileId) }
    var importance by remember { mutableIntStateOf(base.importance) }
    var label by remember { mutableStateOf(base.label) }
    var soundUri by remember { mutableStateOf(base.soundUri) }
    val isReminder = kind == AlarmKind.REMINDER
    // A reminder without a label would ring with no idea what it is for.
    val canSave = !isReminder || label.isNotBlank()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()
            .padding(16.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isReminder, onClick = { kind = AlarmKind.WAKE },
                    label = { Text("⏰ " + stringResource(R.string.kind_wake), fontSize = 18.sp) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = isReminder, onClick = { kind = AlarmKind.REMINDER },
                    label = { Text("🔔 " + stringResource(R.string.kind_reminder), fontSize = 18.sp) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }

            if (isReminder) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.reminder_label)) },
                    placeholder = { Text(stringResource(R.string.reminder_hint)) },
                    singleLine = true, isError = label.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Hours and minutes wheels, always LTR like a clock face.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WheelPicker(
                        count = 24, value = hour, onValueChange = { hour = it },
                        label = stringResource(R.string.hours), modifier = Modifier.width(120.dp),
                    )
                    Text(":", fontSize = 40.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    WheelPicker(
                        count = 60, value = minute, onValueChange = { minute = it },
                        label = stringResource(R.string.minutes), modifier = Modifier.width(120.dp),
                    )
                }
            }

            DayCircles(selected = days, onChange = { days = it }, modifier = Modifier.fillMaxWidth())

            SoundPickerRow(stored = soundUri, onChange = { soundUri = it })

            if (!isReminder) {
                Text(stringResource(R.string.profile), fontSize = 18.sp)
                Profiles.all.forEach { p ->
                    val selected = p.id == profileId
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.fillMaxWidth()
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape,
                            )
                            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape)
                            .clickable { profileId = p.id }
                            .padding(16.dp),
                    ) {
                        val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        Text(stringResource(profileName(p.id)), fontSize = 20.sp, color = color)
                        Text(stringResource(profileDescription(p.id)), fontSize = 14.sp, color = color)
                    }
                }

                Text(stringResource(R.string.importance), fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.importance_1, R.string.importance_2, R.string.importance_3).forEachIndexed { i, res ->
                        FilterChip(
                            selected = importance == i + 1, onClick = { importance = i + 1 },
                            label = { Text(stringResource(res), fontSize = 16.sp) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    placeholder = { Text(stringResource(R.string.label_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        ThumbRow(leftHanded) {
            if (initial != null) {
                OutlinedButton(onClick = { onDelete(initial) }, modifier = Modifier.heightIn(min = BigTouch)) {
                    Text(stringResource(R.string.delete))
                }
            } else {
                OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = BigTouch)) {
                    Text(stringResource(R.string.cancel))
                }
            }
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        base.copy(
                            hour = hour, minute = minute, daysMask = AlarmEntity.maskOf(days),
                            profileId = profileId, importance = importance, label = label.trim(),
                            kind = kind.name, enabled = true, soundUri = soundUri,
                            // Saving an edit starts fresh: any "skip once" is dropped.
                            skipDate = null,
                        )
                    )
                },
                modifier = Modifier.heightIn(min = BigTouch).padding(start = 12.dp),
            ) { Text(stringResource(R.string.save), fontSize = 22.sp, modifier = Modifier.padding(horizontal = 24.dp)) }
        }
    }
}
