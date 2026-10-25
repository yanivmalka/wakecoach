package com.wakecoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val VISIBLE = 3
private const val LOOPS = 1000

/**
 * Circular scroll wheel ("drum") for picking a number in [0, count).
 * Snaps to the nearest row, ticks the haptic motor on every change, and the
 * centre row is the selection.
 */
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 64.dp,
    format: (Int) -> String = { "%02d".format(it) },
) {
    // A long virtual list makes the wheel feel endless; start in the middle so both directions work.
    val middle = (LOOPS / 2) * count
    val state = rememberLazyListState(initialFirstVisibleItemIndex = middle + value - VISIBLE / 2)
    val haptics = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val scope = rememberCoroutineScope()

    // Index of the row sitting in the centre slot.
    val centred by remember {
        derivedStateOf {
            val offsetRows = (state.firstVisibleItemScrollOffset / state.layoutInfo.let {
                it.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: 1f
            }).roundToInt()
            state.firstVisibleItemIndex + offsetRows + VISIBLE / 2
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { centred % count }.distinctUntilChanged().collect {
            if (it != currentValue) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnChange(it)
            }
        }
    }

    // Follow external changes (e.g. a shortcut button) without fighting the user's scroll.
    LaunchedEffect(value) {
        if (!state.isScrollInProgress && centred % count != value) {
            val delta = ((value - centred % count) + count) % count
            val shortest = if (delta > count / 2) delta - count else delta
            state.animateScrollToItem(centred + shortest - VISIBLE / 2)
        }
    }

    Box(
        modifier
            .height(itemHeight * VISIBLE)
            .semantics {
                contentDescription = "$label ${format(value)}"
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..(count - 1).toFloat(), count - 2)
                setProgress { target ->
                    val v = target.roundToInt().coerceIn(0, count - 1)
                    scope.launch { state.scrollToItem(middle + v - VISIBLE / 2) }
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Selection band behind the centre row.
        Box(
            Modifier.fillMaxWidth().height(itemHeight)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count * LOOPS) { index ->
                val distance = abs(index - centred)
                val selected = distance == 0
                Box(Modifier.height(itemHeight).width(itemHeight * 2), contentAlignment = Alignment.Center) {
                    Text(
                        format(index % count),
                        fontSize = if (selected) 40.sp else 30.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Light,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (distance == 0) 1f else 0.45f
                            val scale = if (distance == 0) 1f else 0.85f
                            scaleX = scale; scaleY = scale
                        },
                    )
                }
            }
        }
    }
}
