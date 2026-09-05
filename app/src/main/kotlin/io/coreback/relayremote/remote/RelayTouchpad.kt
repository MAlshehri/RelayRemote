package io.coreback.relayremote.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.coreback.relayremote.protocol.AppleTvCommand
import io.coreback.relayremote.protocol.TouchGesture
import io.coreback.relayremote.ui.RelayCarbon
import io.coreback.relayremote.ui.RelayCyan
import io.coreback.relayremote.ui.RelayViolet
import io.coreback.relayremote.ui.RelayFog
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun RelayTouchpad(
    enabled: Boolean,
    onCommand: (AppleTvCommand) -> Unit,
    onTouch: (TouchGesture) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val repeatScope = rememberCoroutineScope()
    var pendingCenterTap by remember { mutableStateOf<Job?>(null) }
    var isPressed by remember { mutableStateOf(false) }
    val semanticsModifier = Modifier.semantics {
        contentDescription = "Apple TV touchpad"
        if (!enabled) disabled()
        onClick {
            if (enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onCommand(AppleTvCommand.SELECT)
                true
            } else {
                false
            }
        }
    }

    Box(
        modifier = modifier
            .then(semanticsModifier)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF171A20), RelayCarbon, Color(0xFF08090B)),
                    radius = 900f,
                ),
                shape = RoundedCornerShape(42.dp),
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    val start = down.position
                    val startTime = down.uptimeMillis
                    var last = start
                    var moved = false
                    val direction = directionFor(start, size.width.toFloat(), size.height.toFloat())
                    val repeatJob = direction?.let { command ->
                        repeatScope.launch {
                            delay(350)
                            while (true) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCommand(command)
                                delay(100)
                            }
                        }
                    }

                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        last = change.position
                        moved = moved || hypot(
                            (last.x - start.x).toDouble(),
                            (last.y - start.y).toDouble(),
                        ) > 22.dp.toPx()
                        if (moved) repeatJob?.cancel()
                        pressed = change.pressed
                        change.consume()
                    }
                    isPressed = false
                    repeatJob?.cancel()
                    val duration = (eventTimeMillis() - startTime).coerceAtLeast(1)

                    when {
                        moved -> {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTouch(
                                TouchGesture.Swipe(
                                    startX = normalize(start.x, size.width),
                                    startY = normalize(start.y, size.height),
                                    endX = normalize(last.x, size.width),
                                    endY = normalize(last.y, size.height),
                                    durationMillis = duration,
                                ),
                            )
                        }

                        direction != null && duration < 350 -> {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onCommand(direction)
                        }

                        direction == null && duration >= 350 -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTouch(
                                TouchGesture.Hold(
                                    x = normalize(start.x, size.width),
                                    y = normalize(start.y, size.height),
                                    durationMillis = duration,
                                ),
                            )
                        }

                        direction == null -> {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            if (pendingCenterTap?.isActive == true) {
                                pendingCenterTap?.cancel()
                                pendingCenterTap = null
                                onTouch(
                                    TouchGesture.DoubleTap(
                                        x = normalize(start.x, size.width),
                                        y = normalize(start.y, size.height),
                                    ),
                                )
                            } else {
                                pendingCenterTap = repeatScope.launch {
                                    delay(300)
                                    onCommand(AppleTvCommand.SELECT)
                                    pendingCenterTap = null
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        RelayCyan.copy(alpha = if (enabled) 0.7f else 0.18f),
                        Color.Transparent,
                        RelayViolet.copy(alpha = if (enabled) 0.55f else 0.16f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(42.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            val cueColor = Color.White.copy(alpha = if (enabled) 0.35f else 0.12f)
            val cueLength = 13.dp.toPx()
            val inset = 22.dp.toPx()
            drawLine(
                cueColor,
                Offset(center.x - cueLength, inset),
                Offset(center.x + cueLength, inset),
                2.dp.toPx(),
                StrokeCap.Round,
            )
            drawLine(
                cueColor,
                Offset(center.x - cueLength, size.height - inset),
                Offset(center.x + cueLength, size.height - inset),
                2.dp.toPx(),
                StrokeCap.Round,
            )
            drawLine(
                cueColor,
                Offset(inset, center.y - cueLength),
                Offset(inset, center.y + cueLength),
                2.dp.toPx(),
                StrokeCap.Round,
            )
            drawLine(
                cueColor,
                Offset(size.width - inset, center.y - cueLength),
                Offset(size.width - inset, center.y + cueLength),
                2.dp.toPx(),
                StrokeCap.Round,
            )
            drawCircle(
                color = Color.White.copy(alpha = when {
                    !enabled -> 0.03f
                    isPressed -> 0.16f
                    else -> 0.08f
                }),
                radius = if (isPressed) 38.dp.toPx() else 34.dp.toPx(),
                center = center,
            )
        }
        Text(
            text = if (enabled) {
                "Swipe to navigate  ·  Tap to select"
            } else {
                "Connect to an Apple TV to use touch controls"
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            color = RelayFog.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun directionFor(position: Offset, width: Float, height: Float): AppleTvCommand? {
    val x = position.x / width.coerceAtLeast(1f)
    val y = position.y / height.coerceAtLeast(1f)
    val dx = x - 0.5f
    val dy = y - 0.5f
    if (abs(dx) < 0.27f && abs(dy) < 0.27f) return null
    return if (abs(dx) > abs(dy)) {
        if (dx < 0) AppleTvCommand.LEFT else AppleTvCommand.RIGHT
    } else {
        if (dy < 0) AppleTvCommand.UP else AppleTvCommand.DOWN
    }
}

private fun normalize(value: Float, extent: Int): Float =
    (value / extent.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

private fun eventTimeMillis(): Long = android.os.SystemClock.uptimeMillis()
