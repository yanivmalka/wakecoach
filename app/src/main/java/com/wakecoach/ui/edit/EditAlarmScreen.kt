package com.wakecoach.ui.edit

import com.wakecoach.data.repeatDays
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.core.model.Profiles
import com.wakecoach.data.AlarmEntity
import com.wakecoach.ui.home.ThumbRow
import com.wakecoach.ui.profileDescription
import com.wakecoach.ui.profileName
import com.wakecoach.ui.theme.BigDigits
import com.wakecoach.ui.theme.BigTouch
import com.wakecoach.ui.theme.NightCard
import com.wakecoach.ui.theme.Sun
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditAlarmScreen(
    initial: AlarmEntity?,
    leftHanded: Boolean,
    onSave: (AlarmEntity) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    onCancel: () -> Unit,
) {
    val base = initial ?: AlarmEntity(hour = 7, minute = 0)
    var hour by remember { mutableIntStateOf(base.hour) }
    var minute by remember { mutableIntStateOf(base.minute) }
    var days by remember { mutableStateOf(base.repeatDays) }
    var profileId by remember { mutableStateOf(base.profileId) }
    var importance by remember { mutableIntStateOf(base.importance) }
    var label by remember { mutableStateOf(base.label) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()
            .padding(16.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BigDigits("%02d:%02d".format(hour, minute), Modifier.fillMaxWidth().height(120.dp), color = Sun)
            // Big +/- steppers instead of a fiddly dial: hours left, minutes right (always LTR like the digits).
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stepper(onMinus = { hour = (hour + 23) % 24 }, onPlus = { hour = (hour + 1) % 24 })
                    Stepper(onMinus = { minute = (minute + 55) % 60 }, onPlus = { minute = (minute + 5) % 60 })
                }
            }

            val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            val ordered = (0L..6L).map { first.plus(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ordered.forEach { d ->
                    FilterChip(
                        selected = d in days,
                        onClick = { days = if (d in days) days - d else days + d },
                        label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 16.sp) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            Text(stringResource(R.string.profile), fontSize = 18.sp)
            Profiles.all.forEach { p ->
                val selected = p.id == profileId
                Column(
                    Modifier.fillMaxWidth()
                        .background(NightCard, RoundedCornerShape(16.dp))
                        .border(if (selected) 2.dp else 0.dp, if (selected) Sun else NightCard, RoundedCornerShape(16.dp))
                        .clickable { profileId = p.id }
                        .padding(16.dp),
                ) {
                    Text(stringResource(profileName(p.id)), fontSize = 20.sp, color = if (selected) Sun else MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(profileDescription(p.id)), fontSize = 14.sp)
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
                onClick = {
                    onSave(
                        base.copy(
                            hour = hour, minute = minute, daysMask = AlarmEntity.maskOf(days),
                            profileId = profileId, importance = importance, label = label.trim(), enabled = true,
                        )
                    )
                },
                modifier = Modifier.heightIn(min = BigTouch).padding(start = 12.dp),
            ) { Text(stringResource(R.string.save), fontSize = 22.sp, modifier = Modifier.padding(horizontal = 24.dp)) }
        }
    }
}

@Composable
private fun Stepper(onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = onMinus, modifier = Modifier.size(BigTouch)) { Text(stringResource(R.string.time_minus), fontSize = 28.sp) }
        FilledTonalButton(onClick = onPlus, modifier = Modifier.size(BigTouch)) { Text(stringResource(R.string.time_plus), fontSize = 28.sp) }
    }
}

