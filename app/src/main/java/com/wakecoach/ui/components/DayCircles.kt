package com.wakecoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Seven round day toggles in locale order, with "weekdays" / "every day" shortcuts. */
@Composable
fun DayCircles(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    val first = WeekFields.of(locale).firstDayOfWeek
    val days = (0L..6L).map { first.plus(it) }
    // Work week: Sun-Thu where the week starts on Sunday (e.g. Israel), else Mon-Fri.
    val workWeek = if (first == DayOfWeek.SUNDAY) days.take(5).toSet() else DayOfWeek.entries.take(5).toSet()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                val on = d in selected
                Surface(
                    onClick = { onChange(if (on) selected - d else selected + d) },
                    shape = CircleShape,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    border = if (on) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(46.dp).semantics {
                        contentDescription = d.getDisplayName(TextStyle.FULL, locale)
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            d.getDisplayName(TextStyle.NARROW, locale),
                            fontSize = 18.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onChange(workWeek) }, label = { Text(stringResource(R.string.days_workweek)) })
            AssistChip(onClick = { onChange(DayOfWeek.entries.toSet()) }, label = { Text(stringResource(R.string.days_every)) })
            AssistChip(onClick = { onChange(emptySet()) }, label = { Text(stringResource(R.string.repeat_once)) })
        }
    }
}
