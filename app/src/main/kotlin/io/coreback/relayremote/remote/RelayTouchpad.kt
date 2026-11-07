package io.coreback.relayremote.remote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.coreback.relayremote.protocol.AppleTvCommand
import io.coreback.relayremote.protocol.TouchGesture
import io.coreback.relayremote.ui.RelayCarbon
import io.coreback.relayremote.ui.RelayCarbonRaised
import io.coreback.relayremote.ui.RelayCyan
import io.coreback.relayremote.ui.RelayFog
import io.coreback.relayremote.ui.RelayMoon
import io.coreback.relayremote.ui.RelayViolet
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val REMOTE_HOLD_MILLIS = 400L
private const val REPEAT_INTERVAL_MILLIS = 100L
private const val TOUCH_FRAME_MILLIS = 16L

@Composable
fun RelayTouchpad(
    enabled: Boolean,
    onCommand: (AppleTvCommand) -> Unit,
    onTouch: (TouchGesture) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnCommand by rememberUpdatedState(onCommand)
    val currentOnTouch by rememberUpdatedState(onTouch)
    var isPressed by remember { mutableStateOf(false) }
    var isCenterPress by remember { mutableStateOf(false) }
    var isHolding by remember { mutableStateOf(false) }
    var swipePosition by remember { mutableStateOf<Offset?>(null) }
    val holdProgress by animateFloatAsState(
        targetValue = if (isCenterPress && swipePosition == null) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isCenterPress && swipePosition == null) REMOTE_HOLD_MILLIS.toInt() else 120,
        ),
        label = "Hold progress",
    )

    Box(
        modifier = modifier
            .testTag("Touchpad")
            .semantics {
                contentDescription = "Apple TV touchpad"
                if (!enabled) {
                    disabled()
                } else {
                    onClick("Select") {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        currentOnCommand(AppleTvCommand.SELECT)
                        true
                    }
                    onLongClick("Open menu for the highlighted item") {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnCommand(AppleTvCommand.SELECT_LONG_PRESS)
                        true
                    }
                    customActions = listOf(
                        "Move up" to AppleTvCommand.UP,
                        "Move down" to AppleTvCommand.DOWN,
                        "Move left" to AppleTvCommand.LEFT,
                        "Move right" to AppleTvCommand.RIGHT,
                    ).map { (label, command) ->
                        CustomAccessibilityAction(label) {
                            currentOnCommand(command)
                            true
                        }
                    }
                }
            }
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF171A20), RelayCarbon, Color(0xFF08090B)),
                    radius = 900f,
                ),
                shape = RoundedCornerShape(42.dp),
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val swipeSlop = SWIPE_SLOP.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val start = down.position
                    val direction = directionFor(start, size.width.toFloat(), size.height.toFloat())
                    var last = start
                    var lastMoveSentAt = down.uptimeMillis
                    var swiping = false
                    var held = false
                    var finished = false
                    isPressed = true
                    isCenterPress = direction == null
                    val holdJob = scope.launch {
                        delay(REMOTE_HOLD_MILLIS)
                        held = true
                        if (direction == null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            isHolding = true
                            currentOnTouch(TouchGesture.HoldStarted)
                        } else {
                            while (true) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnCommand(direction)
                                delay(REPEAT_INTERVAL_MILLIS)
                            }
                        }
                    }
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id }
                                ?: break
                            last = change.position
                            val pressed = change.pressed
                            change.consume()
                            if (!swiping && !held && distance(start, last) > swipeSlop) {
                                swiping = true
                                holdJob.cancel()
                                currentOnTouch(
                                    TouchGesture.Began(
                                        normalize(start.x, size.width),
                                        normalize(start.y, size.height),
                                    ),
                                )
                            }
                            if (swiping) {
                                swipePosition = last
                                if (pressed && change.uptimeMillis - lastMoveSentAt >= TOUCH_FRAME_MILLIS) {
                                    lastMoveSentAt = change.uptimeMillis
                                    currentOnTouch(
                                        TouchGesture.Moved(
                                            normalize(last.x, size.width),
                                            normalize(last.y, size.height),
                                        ),
                                    )
                                }
                            }
                            if (!pressed) {
                                finished = true
                                break
                            }
                        }
                    } finally {
                        // Also runs if the pad is disabled mid-gesture, so nothing stays held on the TV.
                        holdJob.cancel()
                        when {
                            swiping -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnTouch(
                                    TouchGesture.Ended(
                                        normalize(last.x, size.width),
                                        normalize(last.y, size.height),
                                    ),
                                )
                            }
                            isHolding -> currentOnTouch(TouchGesture.HoldEnded)
                            finished && !held -> {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                currentOnCommand(direction ?: AppleTvCommand.SELECT)
                            }
                        }
                        isPressed = false
                        isCenterPress = false
                        isHolding = false
                        swipePosition = null
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
                cornerRadius = CornerRadius(42.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            val cueColor = Color.White.copy(alpha = if (enabled) 0.35f else 0.12f)
            val cueLength = 13.dp.toPx()
            val inset = 22.dp.toPx()
            drawLine(cueColor, Offset(center.x - cueLength, inset), Offset(center.x + cueLength, inset), 2.dp.toPx(), StrokeCap.Round)
            drawLine(cueColor, Offset(center.x - cueLength, size.height - inset), Offset(center.x + cueLength, size.height - inset), 2.dp.toPx(), StrokeCap.Round)
            drawLine(cueColor, Offset(inset, center.y - cueLength), Offset(inset, center.y + cueLength), 2.dp.toPx(), StrokeCap.Round)
            drawLine(cueColor, Offset(size.width - inset, center.y - cueLength), Offset(size.width - inset, center.y + cueLength), 2.dp.toPx(), StrokeCap.Round)
            val coreRadius = if (isPressed) 38.dp.toPx() else 34.dp.toPx()
            drawCircle(
                color = Color.White.copy(
                    alpha = when {
                        !enabled -> 0.03f
                        isHolding -> 0.22f
                        isPressed -> 0.16f
                        else -> 0.08f
                    },
                ),
                radius = coreRadius,
                center = center,
            )
            if (holdProgress > 0f) {
                val ringRadius = coreRadius + 8.dp.toPx()
                drawArc(
                    color = RelayCyan,
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            swipePosition?.let { position ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(RelayCyan.copy(alpha = 0.4f), Color.Transparent),
                        center = position,
                        radius = 72.dp.toPx(),
                    ),
                    radius = 72.dp.toPx(),
                    center = position,
                )
            }
        }
        Text(
            text = when {
                !enabled -> "Connect to an Apple TV to use touch controls"
                isHolding -> "Opening the menu on Apple TV…"
                else -> "Swipe to move  ·  Tap to select  ·  Hold for menu"
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 20.dp, top = 36.dp, end = 20.dp),
            color = if (isHolding) RelayCyan else RelayFog.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Buttons instead of swipes, like Directional Buttons in Apple's remote accessibility settings. */
@Composable
fun RelayDirectionalPad(
    enabled: Boolean,
    onCommand: (AppleTvCommand) -> Unit,
    onTouch: (TouchGesture) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .testTag("Directional buttons")
            .background(RelayCarbon, RoundedCornerShape(42.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val diameter = minOf(maxWidth, maxHeight) - 16.dp
        val arrowSize = diameter * 0.32f
        Box(
            Modifier
                .size(diameter)
                .background(RelayCarbonRaised, CircleShape),
        ) {
            DirectionalArrow("Up", 0f, AppleTvCommand.UP, enabled, onCommand, Modifier.align(Alignment.TopCenter).size(arrowSize))
            DirectionalArrow("Right", 90f, AppleTvCommand.RIGHT, enabled, onCommand, Modifier.align(AbsoluteAlignment.CenterRight).size(arrowSize))
            DirectionalArrow("Down", 180f, AppleTvCommand.DOWN, enabled, onCommand, Modifier.align(Alignment.BottomCenter).size(arrowSize))
            DirectionalArrow("Left", 270f, AppleTvCommand.LEFT, enabled, onCommand, Modifier.align(AbsoluteAlignment.CenterLeft).size(arrowSize))
            SelectButton(
                enabled = enabled,
                onCommand = onCommand,
                onTouch = onTouch,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(diameter * 0.36f),
            )
        }
    }
}

@Composable
private fun DirectionalArrow(
    description: String,
    rotation: Float,
    command: AppleTvCommand,
    enabled: Boolean,
    onCommand: (AppleTvCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnCommand by rememberUpdatedState(onCommand)
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (pressed) RelayCyan.copy(alpha = 0.2f) else Color.Transparent)
            .semantics {
                contentDescription = description
                role = Role.Button
                if (!enabled) {
                    disabled()
                } else {
                    onClick {
                        currentOnCommand(command)
                        true
                    }
                }
            }
            .remotePress(
                enabled = enabled,
                onPressedChange = { pressed = it },
                onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    currentOnCommand(command)
                },
                onHoldStart = {
                    repeatJob = scope.launch {
                        while (true) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnCommand(command)
                            delay(REPEAT_INTERVAL_MILLIS)
                        }
                    }
                },
                onHoldEnd = {
                    repeatJob?.cancel()
                    repeatJob = null
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val color = if (enabled) RelayMoon else RelayFog
            val stroke = 2.4.dp.toPx()
            rotate(rotation) {
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.66f), Offset(center.x, size.height * 0.32f), stroke, StrokeCap.Round)
                drawLine(color, Offset(center.x, size.height * 0.32f), Offset(size.width * 0.82f, size.height * 0.66f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun SelectButton(
    enabled: Boolean,
    onCommand: (AppleTvCommand) -> Unit,
    onTouch: (TouchGesture) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnCommand by rememberUpdatedState(onCommand)
    val currentOnTouch by rememberUpdatedState(onTouch)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (pressed) RelayCyan.copy(alpha = 0.3f) else RelayCyan.copy(alpha = 0.12f))
            .semantics {
                contentDescription = "Select"
                role = Role.Button
                if (!enabled) {
                    disabled()
                } else {
                    onClick {
                        currentOnCommand(AppleTvCommand.SELECT)
                        true
                    }
                    onLongClick("Open menu for the highlighted item") {
                        currentOnCommand(AppleTvCommand.SELECT_LONG_PRESS)
                        true
                    }
                }
            }
            .remotePress(
                enabled = enabled,
                onPressedChange = { pressed = it },
                onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    currentOnCommand(AppleTvCommand.SELECT)
                },
                onHoldStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnTouch(TouchGesture.HoldStarted)
                },
                onHoldEnd = { currentOnTouch(TouchGesture.HoldEnded) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("OK", color = if (enabled) RelayMoon else RelayFog, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * A control with a tap action and an optional hold action that lasts as long as the finger stays down.
 * Sliding off the control cancels a tap but never leaves a started hold running.
 */
@Suppress("ComposableModifierFactory")
@Composable
internal fun Modifier.remotePress(
    enabled: Boolean,
    onTap: () -> Unit,
    onPressedChange: (Boolean) -> Unit = {},
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: () -> Unit = {},
): Modifier {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnPressedChange by rememberUpdatedState(onPressedChange)
    val currentOnHoldStart by rememberUpdatedState(onHoldStart)
    val currentOnHoldEnd by rememberUpdatedState(onHoldEnd)
    val holdEnabled = onHoldStart != null
    return pointerInput(enabled, holdEnabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        var holding = false
        var tapCandidate = false
        var change: PointerInputChange = down
        currentOnPressedChange(true)
        try {
            val deadline = down.uptimeMillis + REMOTE_HOLD_MILLIS
            var cancelled = false
            while (change.pressed) {
                if (holdEnabled && !holding && !cancelled) {
                    val remaining = deadline - change.uptimeMillis
                    val event = withTimeoutOrNull(remaining.coerceAtLeast(0)) { awaitPointerEvent() }
                    if (event == null) {
                        holding = true
                        currentOnHoldStart?.invoke()
                        continue
                    }
                    change = event.changes.firstOrNull { it.id == down.id } ?: break
                } else {
                    change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                }
                if (change.isOutOfBounds(size, extendedTouchPadding)) cancelled = true
                change.consume()
            }
            tapCandidate = !change.pressed && !holding && !cancelled
        } finally {
            currentOnPressedChange(false)
            if (holding) currentOnHoldEnd()
        }
        if (tapCandidate) currentOnTap()
    }
    }
}

private val SWIPE_SLOP = 14.dp

private fun distance(from: Offset, to: Offset): Float =
    hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble()).toFloat()

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
