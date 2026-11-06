package io.coreback.relayremote.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.coreback.relayremote.protocol.AppleTvCommand
import io.coreback.relayremote.R
import io.coreback.relayremote.protocol.Capability
import io.coreback.relayremote.protocol.ConnectionState
import io.coreback.relayremote.protocol.KeyboardOperation
import io.coreback.relayremote.protocol.PowerState
import io.coreback.relayremote.protocol.ScanDirection
import io.coreback.relayremote.protocol.TouchGesture
import io.coreback.relayremote.ui.RelayBlack
import io.coreback.relayremote.ui.RelayCarbon
import io.coreback.relayremote.ui.RelayCarbonRaised
import io.coreback.relayremote.ui.RelayCyan
import io.coreback.relayremote.ui.RelayFog
import io.coreback.relayremote.ui.RelayMoon
import io.coreback.relayremote.ui.RelayViolet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    state: RemoteUiState,
    onOpenSheet: (RemoteSheet) -> Unit,
    onCloseSheet: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onStartPairing: (String) -> Unit,
    onPinChanged: (String) -> Unit,
    onSubmitPin: () -> Unit,
    onKeyboardTextChanged: (String) -> Unit,
    onKeyboardOperation: (KeyboardOperation) -> Unit,
    onCommand: (AppleTvCommand) -> Unit,
    onSkipBy: (Int) -> Unit,
    onScan: (ScanDirection, Boolean) -> Unit = { _, _ -> },
    onTouch: (TouchGesture) -> Unit,
    onWake: () -> Unit,
    onSleep: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onMute: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onFavoriteChanged: (String, Boolean) -> Unit,
    onSwitchAccount: (String) -> Unit = {},
    onRename: (String, String?) -> Unit,
    onForget: (String) -> Unit,
    onKeepScreenAwakeChanged: (Boolean) -> Unit,
    onDirectionalButtonsChanged: (Boolean) -> Unit = {},
    onSkipSecondsChanged: (Int) -> Unit,
    onRefreshDiscovery: () -> Unit = {},
    onRetryConnection: () -> Unit = {},
    onClearError: () -> Unit,
    onRefreshKeyboard: () -> Unit = {},
    onRetryKeyboard: () -> Unit = {},
    onStartFreshKeyboard: () -> Unit = {},
    onCloseKeyboardAnyway: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RelayBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp),
    ) {
        RemoteHeader(
            state = state,
            onDevices = { onOpenSheet(RemoteSheet.DEVICES) },
            onSettings = { onOpenSheet(RemoteSheet.SETTINGS) },
            onWake = onWake,
            onSleep = onSleep,
        )

        if (state.connectionState != ConnectionState.Connected) {
            ConnectionGuidance(
                state = state,
                onChooseDevice = { onOpenSheet(RemoteSheet.DEVICES) },
                onRefresh = onRefreshDiscovery,
                onRetry = onRetryConnection,
                onRePair = { state.selectedDeviceId?.let(onStartPairing) },
            )
            Spacer(Modifier.height(12.dp))
        }

        state.error?.let { message ->
            InlineMessage(
                message = message,
                error = true,
                actionLabel = "Dismiss",
                onAction = onClearError,
            )
            Spacer(Modifier.height(10.dp))
        }

        state.confirmation?.let { message ->
            InlineMessage(message = message, error = false)
            Spacer(Modifier.height(10.dp))
        }

        if (state.connectionState == ConnectionState.Connected &&
            state.powerState == PowerState.OFF &&
            state.controlEnabled(Capability.WAKE)
        ) {
            InlineMessage(
                message = "${state.selectedDeviceName ?: "Apple TV"} is asleep",
                error = false,
                actionLabel = "Wake",
                onAction = onWake,
            )
            Spacer(Modifier.height(10.dp))
        }

        state.nowPlaying?.let { media ->
            NowPlayingStrip(media)
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickAction(
                label = "Apps",
                icon = RemoteIcon.APPS,
                description = "Apps",
                enabled = state.controlEnabled(Capability.APPS),
                onClick = { onOpenSheet(RemoteSheet.APPS) },
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                label = "Keyboard",
                icon = RemoteIcon.KEYBOARD,
                description = "Keyboard",
                enabled = state.controlEnabled(Capability.KEYBOARD),
                highlighted = state.controlEnabled(Capability.KEYBOARD),
                onClick = { onOpenSheet(RemoteSheet.KEYBOARD) },
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                label = "More",
                icon = RemoteIcon.MORE,
                description = "More controls",
                enabled = state.connectionState == ConnectionState.Connected,
                onClick = { onOpenSheet(RemoteSheet.MORE) },
                modifier = Modifier.weight(1f),
            )
        }

        if (state.controlEnabled(Capability.APPS) && state.favoriteApps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FavoriteAppsRow(state.favoriteApps, onLaunchApp)
        }

        Spacer(Modifier.height(14.dp))
        val touchEnabled = state.controlEnabled(Capability.TOUCH) ||
            state.controlEnabled(Capability.NAVIGATION)
        val padModifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        if (state.directionalButtons) {
            RelayDirectionalPad(touchEnabled, onCommand, onTouch, padModifier)
        } else {
            RelayTouchpad(touchEnabled, onCommand, onTouch, padModifier)
        }
        Spacer(Modifier.height(14.dp))
        CommandDock(
            state = state,
            onCommand = onCommand,
            onMute = onMute,
            onVolumeUp = onVolumeUp,
            onVolumeDown = onVolumeDown,
        )
        Spacer(Modifier.height(8.dp))
        PlaybackSkipRow(
            state = state,
            onSkipBy = onSkipBy,
            onScan = onScan,
        )
        Spacer(
            Modifier.height(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp,
            ),
        )
    }

    if (state.sheet != RemoteSheet.NONE) {
        ModalBottomSheet(
            onDismissRequest = onCloseSheet,
            // Long sheets would otherwise open half hidden, with no sign that there is more.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = RelayCarbon,
            contentColor = RelayMoon,
        ) {
            SheetNavigation(
                sheet = state.sheet,
                closeEnabled = !state.pairingInProgress &&
                    !(state.sheet == RemoteSheet.KEYBOARD && state.keyboardDirty),
                onBack = {
                    if (state.sheet == RemoteSheet.PAIRING) {
                        onCloseSheet()
                        onOpenSheet(RemoteSheet.DEVICES)
                    } else {
                        val parent = parentSheet(state.sheet)
                        if (parent == RemoteSheet.NONE) onCloseSheet() else onOpenSheet(parent)
                    }
                },
                onClose = onCloseSheet,
            )
            when (state.sheet) {
                RemoteSheet.DEVICES -> DeviceSheet(
                    state = state,
                    onSelect = onSelectDevice,
                    onPair = onStartPairing,
                    onRetry = onRetryConnection,
                    onDiagnostics = { onOpenSheet(RemoteSheet.DIAGNOSTICS) },
                    onRefresh = onRefreshDiscovery,
                )
                RemoteSheet.PAIRING -> PairingSheet(
                    state = state,
                    onPinChanged = onPinChanged,
                    onSubmit = onSubmitPin,
                )
                RemoteSheet.APPS -> AppsSheet(state, onLaunchApp, onFavoriteChanged)
                RemoteSheet.KEYBOARD -> KeyboardSheet(
                    state = state,
                    onTextChanged = onKeyboardTextChanged,
                    onRefresh = onRefreshKeyboard,
                    onRetry = onRetryKeyboard,
                    onStartFresh = onStartFreshKeyboard,
                    onCloseAnyway = onCloseKeyboardAnyway,
                    onDone = onCloseSheet,
                )
                RemoteSheet.MORE -> MoreSheet(state, onCommand, onSwitchAccount, onCloseSheet)
                RemoteSheet.SETTINGS -> SettingsSheet(
                    state = state,
                    onKeepScreenAwakeChanged = onKeepScreenAwakeChanged,
                    onDirectionalButtonsChanged = onDirectionalButtonsChanged,
                    onSkipSecondsChanged = onSkipSecondsChanged,
                    onRename = onRename,
                    onForget = onForget,
                    onRePair = onStartPairing,
                    onDiagnostics = { onOpenSheet(RemoteSheet.DIAGNOSTICS) },
                    onLicenses = { onOpenSheet(RemoteSheet.LICENSES) },
                )
                RemoteSheet.DIAGNOSTICS -> DiagnosticsSheet(state)
                RemoteSheet.LICENSES -> LicensesSheet()
                RemoteSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun ConnectionGuidance(
    state: RemoteUiState,
    onChooseDevice: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRePair: () -> Unit,
) {
    val selectedName = state.selectedDeviceName
    val presentation = when {
        state.discoveryFailure != null -> GuidancePresentation(
            title = "Local discovery paused",
            detail = state.discoveryFailure,
            primaryLabel = "Try again",
            primaryAction = onRefresh,
            error = true,
        )
        state.connectionState == ConnectionState.AuthenticationFailed -> GuidancePresentation(
            title = "Pairing needs attention",
            detail = "$selectedName rejected the saved connection. Enter a new code from Apple TV to reconnect.",
            primaryLabel = "Pair again",
            primaryAction = onRePair,
            error = true,
        )
        state.connectionState == ConnectionState.Offline && selectedName != null -> GuidancePresentation(
            title = "$selectedName is offline",
            detail = "Make sure Apple TV is awake and on the same Wi-Fi network, then reconnect.",
            primaryLabel = if (state.connectionRetrying) "Connecting…" else "Reconnect",
            primaryAction = onRetry,
            secondaryLabel = "Choose another",
            secondaryAction = onChooseDevice,
        )
        state.connectionState is ConnectionState.Error -> GuidancePresentation(
            title = "Couldn’t connect",
            detail = state.connectionState.message,
            primaryLabel = if (state.connectionRetrying) "Connecting…" else "Try again",
            primaryAction = onRetry,
            secondaryLabel = "Devices",
            secondaryAction = onChooseDevice,
            error = true,
        )
        state.connectionState == ConnectionState.Scanning && selectedName == null -> GuidancePresentation(
            title = when {
                state.devices.any { it.online } -> {
                    val count = state.devices.count { it.online }
                    "$count Apple TV${if (count == 1) "" else "s"} ready"
                }
                state.discoveryRefreshing -> "Looking for Apple TV"
                else -> "No Apple TV found yet"
            },
            detail = if (state.devices.any { it.online }) {
                "Choose a device to pair or reconnect."
            } else {
                "Keep this phone and Apple TV on the same local network."
            },
            primaryLabel = when {
                state.devices.any { it.online } -> "Choose Apple TV"
                state.discoveryRefreshing -> null
                else -> "Scan again"
            },
            primaryAction = if (state.devices.any { it.online }) onChooseDevice else onRefresh,
            secondaryLabel = if (state.devices.any { it.online } || state.discoveryRefreshing) null else "View devices",
            secondaryAction = onChooseDevice,
        )
        state.connectionState == ConnectionState.Pairing -> GuidancePresentation(
            title = "Ready for the code",
            detail = "Enter the four-digit code shown on Apple TV to save this connection.",
        )
        state.connectionState == ConnectionState.Waking -> GuidancePresentation(
            title = "Waking $selectedName",
            detail = "This can take a few seconds.",
        )
        state.connectionState is ConnectionState.Reconnecting -> GuidancePresentation(
            title = "Reconnecting to $selectedName",
            detail = "Attempt ${state.connectionState.attempt}. Controls will return when the session is ready.",
        )
        else -> GuidancePresentation(
            title = "Connecting to ${selectedName ?: "Apple TV"}",
            detail = "Resolving the current local-network address and opening a secure session.",
        )
    }
    val accent = if (presentation.error) MaterialTheme.colorScheme.error else RelayViolet
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = RelayCarbon,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (state.discoveryRefreshing || state.connectionRetrying ||
                    state.connectionState == ConnectionState.Connecting ||
                    state.connectionState is ConnectionState.Reconnecting ||
                    state.connectionState == ConnectionState.Waking
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp),
                        color = accent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    ConnectionMark(state.connectionState, accent)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(presentation.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        presentation.detail,
                        color = RelayFog,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            if (presentation.primaryLabel != null || presentation.secondaryLabel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    presentation.secondaryLabel?.let { label ->
                        TextButton(onClick = presentation.secondaryAction ?: {}) { Text(label) }
                    }
                    presentation.primaryLabel?.let { label ->
                        TextButton(
                            onClick = presentation.primaryAction ?: {},
                            enabled = when (state.connectionState) {
                                ConnectionState.Scanning -> !state.discoveryRefreshing
                                ConnectionState.Offline, is ConnectionState.Error -> !state.connectionRetrying
                                else -> !state.pairingInProgress
                            },
                            modifier = Modifier.testTag("Primary connection action"),
                        ) { Text(label) }
                    }
                }
            }
        }
    }
}

private data class GuidancePresentation(
    val title: String,
    val detail: String,
    val primaryLabel: String? = null,
    val primaryAction: (() -> Unit)? = null,
    val secondaryLabel: String? = null,
    val secondaryAction: (() -> Unit)? = null,
    val error: Boolean = false,
)

@Composable
private fun InlineMessage(
    message: String,
    error: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val color = if (error) MaterialTheme.colorScheme.error else RelayCyan
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = color.copy(alpha = 0.12f),
        contentColor = RelayMoon,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionMark(
                if (error) ConnectionState.Error(message, true) else ConnectionState.Connected,
                color,
            )
            Spacer(Modifier.width(9.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            } else {
                Spacer(Modifier.width(14.dp))
            }
        }
    }
}

@Composable
private fun SheetNavigation(
    sheet: RemoteSheet,
    onBack: () -> Unit,
    onClose: () -> Unit,
    closeEnabled: Boolean,
) {
    val parent = parentSheet(sheet)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (parent != RemoteSheet.NONE) {
            TextButton(onClick = onBack, enabled = closeEnabled, modifier = Modifier.height(48.dp)) { Text("Back") }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClose, enabled = closeEnabled, modifier = Modifier.height(48.dp)) { Text("Close") }
    }
}

private fun parentSheet(sheet: RemoteSheet): RemoteSheet = when (sheet) {
    RemoteSheet.PAIRING -> RemoteSheet.DEVICES
    RemoteSheet.DIAGNOSTICS, RemoteSheet.LICENSES -> RemoteSheet.SETTINGS
    else -> RemoteSheet.NONE
}

private enum class RemoteIcon {
    APPS,
    KEYBOARD,
    MORE,
    SETTINGS,
    CHEVRON_DOWN,
    PLAY,
    PAUSE,
    BACK,
    HOME,
    MUTE,
}

@Composable
private fun RemoteIconGraphic(
    icon: RemoteIcon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        when (icon) {
            RemoteIcon.APPS -> {
                val cell = size.minDimension * 0.28f
                val gap = size.minDimension * 0.12f
                val start = (size.minDimension - (cell * 2 + gap)) / 2
                repeat(2) { row -> repeat(2) { column ->
                    drawRoundRect(
                        color,
                        topLeft = Offset(start + column * (cell + gap), start + row * (cell + gap)),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.18f),
                        style = Stroke(stroke),
                    )
                } }
            }
            RemoteIcon.KEYBOARD -> {
                drawRoundRect(color, style = Stroke(stroke), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                repeat(3) { row -> repeat(4) { column ->
                    drawCircle(
                        color,
                        radius = 0.8.dp.toPx(),
                        center = Offset(size.width * (0.2f + column * 0.2f), size.height * (0.25f + row * 0.2f)),
                    )
                } }
                drawLine(color, Offset(size.width * 0.25f, size.height * 0.84f), Offset(size.width * 0.75f, size.height * 0.84f), stroke, StrokeCap.Round)
            }
            RemoteIcon.MORE -> repeat(3) { index ->
                drawCircle(color, size.minDimension * 0.10f, Offset(size.width * (0.22f + index * 0.28f), center.y))
            }
            RemoteIcon.SETTINGS -> {
                drawCircle(color, size.minDimension * 0.30f, center, style = Stroke(stroke))
                drawCircle(color, size.minDimension * 0.09f, center, style = Stroke(stroke))
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45.0))
                    val inner = size.minDimension * 0.35f
                    val outer = size.minDimension * 0.46f
                    drawLine(
                        color,
                        Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner),
                        Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }
            RemoteIcon.CHEVRON_DOWN -> {
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.35f), Offset(center.x, size.height * 0.68f), stroke, StrokeCap.Round)
                drawLine(color, Offset(center.x, size.height * 0.68f), Offset(size.width * 0.82f, size.height * 0.35f), stroke, StrokeCap.Round)
            }
            RemoteIcon.PLAY -> {
                val path = Path().apply {
                    moveTo(size.width * 0.30f, size.height * 0.18f)
                    lineTo(size.width * 0.80f, center.y)
                    lineTo(size.width * 0.30f, size.height * 0.82f)
                    close()
                }
                drawPath(path, color)
            }
            RemoteIcon.PAUSE -> {
                drawRoundRect(color, Offset(size.width * 0.24f, size.height * 0.18f), androidx.compose.ui.geometry.Size(size.width * 0.18f, size.height * 0.64f), androidx.compose.ui.geometry.CornerRadius(stroke))
                drawRoundRect(color, Offset(size.width * 0.58f, size.height * 0.18f), androidx.compose.ui.geometry.Size(size.width * 0.18f, size.height * 0.64f), androidx.compose.ui.geometry.CornerRadius(stroke))
            }
            RemoteIcon.BACK -> {
                drawLine(color, Offset(size.width * 0.68f, size.height * 0.18f), Offset(size.width * 0.34f, center.y), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.34f, center.y), Offset(size.width * 0.68f, size.height * 0.82f), stroke, StrokeCap.Round)
            }
            RemoteIcon.HOME -> {
                val path = Path().apply {
                    moveTo(size.width * 0.16f, size.height * 0.48f)
                    lineTo(center.x, size.height * 0.16f)
                    lineTo(size.width * 0.84f, size.height * 0.48f)
                    lineTo(size.width * 0.76f, size.height * 0.48f)
                    lineTo(size.width * 0.76f, size.height * 0.82f)
                    lineTo(size.width * 0.24f, size.height * 0.82f)
                    lineTo(size.width * 0.24f, size.height * 0.48f)
                    close()
                }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            RemoteIcon.MUTE -> {
                val path = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.40f)
                    lineTo(size.width * 0.38f, size.height * 0.40f)
                    lineTo(size.width * 0.62f, size.height * 0.20f)
                    lineTo(size.width * 0.62f, size.height * 0.80f)
                    lineTo(size.width * 0.38f, size.height * 0.60f)
                    lineTo(size.width * 0.18f, size.height * 0.60f)
                    close()
                }
                drawPath(path, color)
                drawLine(color, Offset(size.width * 0.72f, size.height * 0.35f), Offset(size.width * 0.90f, size.height * 0.65f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.90f, size.height * 0.35f), Offset(size.width * 0.72f, size.height * 0.65f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun RemoteHeader(
    state: RemoteUiState,
    onDevices: () -> Unit,
    onSettings: () -> Unit,
    onWake: () -> Unit,
    onSleep: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onDevices,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Choose Apple TV" },
            color = RelayBlack,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.selectedDeviceName ?: "Choose Apple TV",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    RemoteIconGraphic(RemoteIcon.CHEVRON_DOWN, RelayFog, Modifier.size(14.dp))
                }
                Spacer(Modifier.height(5.dp))
                ConnectionStatusChip(
                    state.connectionState,
                    if (state.connectionState == ConnectionState.Scanning && state.devices.any { it.online }) {
                        "Ready to pair"
                    } else {
                        null
                    },
                )
            }
        }
        PowerButton(state, onWake, onSleep)
        Spacer(Modifier.width(8.dp))
        MiniButton(
            icon = RemoteIcon.SETTINGS,
            description = "Settings",
            enabled = true,
            onClick = onSettings,
        )
    }
}

@Composable
private fun PowerButton(state: RemoteUiState, onWake: () -> Unit, onSleep: () -> Unit) {
    val enabled = when (state.powerState) {
        PowerState.OFF -> state.controlEnabled(Capability.WAKE)
        else -> state.controlEnabled(Capability.SLEEP)
    }
    val isWake = state.powerState == PowerState.OFF
    var confirmSleep by remember { mutableStateOf(false) }
    Surface(
        onClick = {
            if (isWake) onWake() else confirmSleep = true
        },
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = if (isWake) "Wake" else "Sleep"
                if (!enabled) disabled()
            },
        shape = CircleShape,
        color = RelayCarbon,
        contentColor = if (enabled) RelayMoon else RelayFog,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = if (enabled) RelayMoon else RelayFog,
                    startAngle = -45f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawLine(
                    color = if (enabled) RelayMoon else RelayFog,
                    start = androidx.compose.ui.geometry.Offset(center.x, 0f),
                    end = androidx.compose.ui.geometry.Offset(center.x, center.y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
    if (confirmSleep) {
        AlertDialog(
            onDismissRequest = { confirmSleep = false },
            title = { Text("Put Apple TV to sleep?") },
            text = { Text("Playback will stop and the connected television may also turn off.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmSleep = false
                        onSleep()
                    },
                ) { Text("Sleep") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSleep = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ConnectionStatusChip(state: ConnectionState, labelOverride: String? = null) {
    val color = when (state) {
        ConnectionState.Connected -> RelayCyan
        is ConnectionState.Error, ConnectionState.AuthenticationFailed -> MaterialTheme.colorScheme.error
        ConnectionState.Offline -> RelayFog
        else -> RelayViolet
    }
    val label = labelOverride ?: connectionLabel(state)
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        modifier = Modifier.semantics { contentDescription = "Connection status: $label" },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionMark(state, color)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ConnectionMark(state: ConnectionState, color: Color) {
    Canvas(Modifier.size(9.dp)) {
        when (state) {
            ConnectionState.Connected -> {
                drawLine(color, Offset(size.width * 0.12f, size.height * 0.52f), Offset(size.width * 0.40f, size.height * 0.80f), 1.7.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.40f, size.height * 0.80f), Offset(size.width * 0.90f, size.height * 0.18f), 1.7.dp.toPx(), StrokeCap.Round)
            }
            is ConnectionState.Error, ConnectionState.AuthenticationFailed -> {
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.18f), Offset(size.width * 0.82f, size.height * 0.82f), 1.7.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.82f, size.height * 0.18f), Offset(size.width * 0.18f, size.height * 0.82f), 1.7.dp.toPx(), StrokeCap.Round)
            }
            ConnectionState.Offline -> drawCircle(color, size.minDimension * 0.38f, style = Stroke(1.5.dp.toPx()))
            else -> {
                drawArc(color, -80f, 250f, false, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(color, 1.1.dp.toPx(), center)
            }
        }
    }
}

@Composable
private fun NowPlayingStrip(media: io.coreback.relayremote.protocol.NowPlaying) {
    val title = media.title ?: media.appName ?: "Now playing"
    val subtitle = listOfNotNull(media.artist, media.album).joinToString(" · ")
    val duration = media.durationMillis?.takeIf { it > 0 }
    val position = media.positionMillis?.coerceAtLeast(0)
    val progress = if (duration != null && position != null) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = RelayCarbon,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(RelayViolet.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                RemoteIconGraphic(
                    if (media.isPlaying) RemoteIcon.PLAY else RemoteIcon.PAUSE,
                    RelayViolet,
                    Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = RelayFog,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    if (media.isPlaying) "Playing" else "Paused",
                    color = if (media.isPlaying) RelayCyan else RelayFog,
                    style = MaterialTheme.typography.labelSmall,
                )
                progress?.let {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp)
                            .height(3.dp),
                        color = RelayCyan,
                        trackColor = RelayFog.copy(alpha = 0.2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: RemoteIcon,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val accent = if (highlighted) RelayCyan else if (enabled) RelayMoon else RelayFog
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(62.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(20.dp),
        color = if (highlighted) RelayCyan.copy(alpha = 0.14f) else RelayCarbon,
        contentColor = accent,
    ) {
        // Stacked, because a side-by-side icon leaves too little room for "Keyboard" on a phone.
        Column(
            Modifier.padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RemoteIconGraphic(icon, accent, Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FavoriteAppsRow(
    apps: List<io.coreback.relayremote.protocol.AppleTvApp>,
    onLaunchApp: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("Favorite apps"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(apps, key = { it.bundleId }) { app ->
            Surface(
                onClick = { onLaunchApp(app.bundleId) },
                modifier = Modifier
                    .height(48.dp)
                    .semantics { contentDescription = "Open ${app.name}" },
                shape = RoundedCornerShape(16.dp),
                color = RelayCarbon,
                contentColor = RelayMoon,
            ) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(app.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CommandDock(
    state: RemoteUiState,
    onCommand: (AppleTvCommand) -> Unit,
    onMute: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = RelayCarbon,
    ) {
        Row(
            modifier = Modifier.padding(7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockButton(
                icon = RemoteIcon.BACK,
                description = "Back",
                enabled = state.controlEnabled(Capability.BACK),
                longClickLabel = "Go to the Home Screen",
                onLongClick = { onCommand(AppleTvCommand.BACK_LONG_PRESS) },
                onClick = { onCommand(AppleTvCommand.BACK) },
            )
            DockButton(
                icon = RemoteIcon.HOME,
                description = "Home",
                enabled = state.controlEnabled(Capability.HOME),
                longClickLabel = "Open Control Center",
                onLongClick = if (state.controlEnabled(Capability.HOME_LONG_PRESS)) {
                    { onCommand(AppleTvCommand.HOME_LONG_PRESS) }
                } else {
                    null
                },
                onClick = { onCommand(AppleTvCommand.HOME) },
            )
            DockButton(
                if (state.nowPlaying?.isPlaying == true) RemoteIcon.PAUSE else RemoteIcon.PLAY,
                "Play or pause",
                state.controlEnabled(Capability.PLAY_PAUSE),
            ) {
                onCommand(AppleTvCommand.PLAY_PAUSE)
            }
            DockButton(
                icon = RemoteIcon.MUTE,
                description = if (state.muted) "Unmute" else "Mute",
                enabled = state.controlEnabled(Capability.MUTE),
                active = state.muted,
                onClick = onMute,
            )
            VolumeRocker(
                enabled = state.controlEnabled(Capability.VOLUME),
                onUp = onVolumeUp,
                onDown = onVolumeDown,
            )
        }
    }
}

@Composable
private fun PlaybackSkipRow(
    state: RemoteUiState,
    onSkipBy: (Int) -> Unit,
    onScan: (ScanDirection, Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("Playback skip controls"),
        shape = RoundedCornerShape(22.dp),
        color = RelayCarbon,
        contentColor = RelayMoon,
        tonalElevation = 2.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaybackSkipSegment(
                seconds = state.skipSeconds,
                forward = false,
                enabled = state.controlEnabled(Capability.SKIP_BACKWARD),
                scanEnabled = state.controlEnabled(Capability.REWIND),
                onClick = { onSkipBy(-state.skipSeconds) },
                onScan = { active -> onScan(ScanDirection.REWIND, active) },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(RelayFog.copy(alpha = 0.24f)),
            )
            PlaybackSkipSegment(
                seconds = state.skipSeconds,
                forward = true,
                enabled = state.controlEnabled(Capability.SKIP_FORWARD),
                scanEnabled = state.controlEnabled(Capability.FAST_FORWARD),
                onClick = { onSkipBy(state.skipSeconds) },
                onScan = { active -> onScan(ScanDirection.FAST_FORWARD, active) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaybackSkipSegment(
    seconds: Int,
    forward: Boolean,
    enabled: Boolean,
    scanEnabled: Boolean,
    onClick: () -> Unit,
    onScan: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val accent = if (forward) RelayCyan else RelayViolet
    // An app can offer scanning without fixed skips, and then only the hold does anything.
    val usable = enabled || scanEnabled
    val contentColor = if (usable) accent else RelayFog
    val description = if (forward) {
        "Forward $seconds seconds"
    } else {
        "Rewind $seconds seconds"
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = description
                role = Role.Button
                if (!usable) disabled()
                if (enabled) {
                    onClick {
                        onClick()
                        true
                    }
                }
            }
            .background(if (pressed) accent.copy(alpha = 0.14f) else Color.Transparent)
            .remotePress(
                enabled = usable,
                onPressedChange = { pressed = it },
                onTap = {
                    if (enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onClick()
                    }
                },
                onHoldStart = if (scanEnabled) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onScan(true)
                    }
                } else {
                    null
                },
                onHoldEnd = { onScan(false) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!forward) {
                OrbitSkipIcon(forward = false, color = contentColor)
            }
            Text(
                "$seconds\u0073",
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (forward) {
                OrbitSkipIcon(forward = true, color = contentColor)
            }
        }
    }
}

@Composable
private fun OrbitSkipIcon(
    forward: Boolean,
    color: androidx.compose.ui.graphics.Color,
) {
    Canvas(Modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        val direction = if (forward) 1f else -1f
        drawArc(
            color = color,
            startAngle = if (forward) 130f else 50f,
            sweepAngle = if (forward) 250f else -250f,
            useCenter = false,
            topLeft = Offset(strokeWidth, strokeWidth),
            size = androidx.compose.ui.geometry.Size(
                width = size.width - strokeWidth * 2,
                height = size.height - strokeWidth * 2,
            ),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        val tip = Offset(
            x = size.width * if (forward) 0.88f else 0.12f,
            y = size.height * 0.58f,
        )
        drawLine(
            color = color,
            start = tip,
            end = Offset(tip.x - direction * size.width * 0.20f, tip.y - size.height * 0.03f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = tip,
            end = Offset(tip.x - direction * size.width * 0.06f, tip.y + size.height * 0.18f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun DockButton(
    icon: RemoteIcon,
    description: String,
    enabled: Boolean,
    active: Boolean = false,
    longClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val tint = when {
        !enabled -> RelayFog
        active -> RelayCyan
        else -> RelayMoon
    }
    Surface(
        modifier = Modifier
            .size(50.dp)
            .semantics { contentDescription = description }
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = longClickLabel,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
            ),
        shape = CircleShape,
        color = if (active) RelayCyan.copy(alpha = 0.16f) else RelayBlack,
        contentColor = tint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            RemoteIconGraphic(icon, tint, Modifier.size(21.dp))
        }
    }
}

@Composable
private fun VolumeRocker(enabled: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = RelayBlack,
        modifier = Modifier.width(48.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RepeatingMiniAction("+", "Volume up", enabled, onUp)
            HorizontalDivider(Modifier.width(20.dp), color = RelayFog.copy(alpha = 0.3f))
            RepeatingMiniAction("−", "Volume down", enabled, onDown)
        }
    }
}

@Composable
private fun RepeatingMiniAction(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val repeatScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 44.dp)
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
                onClick {
                    if (enabled) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                    val repeatJob = repeatScope.launch {
                        delay(350)
                        while (true) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                            delay(100)
                        }
                    }
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        pressed = change.pressed
                        change.consume()
                    }
                    repeatJob.cancel()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) RelayMoon else RelayFog,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun MiniButton(
    icon: RemoteIcon,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = RelayCarbon,
    ) {
        Box(contentAlignment = Alignment.Center) {
            RemoteIconGraphic(icon, if (enabled) RelayMoon else RelayFog, Modifier.size(21.dp))
        }
    }
}

@Composable
private fun SheetTitle(title: String, detail: String? = null) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (detail != null) {
            Text(
                detail,
                color = RelayFog,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DeviceSheet(
    state: RemoteUiState,
    onSelect: (String) -> Unit,
    onPair: (String) -> Unit,
    onRetry: () -> Unit,
    onDiagnostics: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            SheetTitle("Apple TVs", "Only compatible Apple TV devices on this network")
        }
        TextButton(onClick = onRefresh, enabled = !state.discoveryRefreshing) {
            if (state.discoveryRefreshing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.discoveryRefreshing) "Scanning" else "Refresh")
        }
    }
    val groups = listOf(
        "Connected & saved" to state.devices.filter { it.online && it.paired },
        "Available to pair" to state.devices.filter { it.online && !it.paired },
        "Saved but offline" to state.devices.filter { !it.online && it.paired },
    ).filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            color = RelayBlack.copy(alpha = 0.46f),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.discoveryRefreshing) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Looking for Apple TV…", modifier = Modifier.padding(top = 14.dp))
                } else {
                    Text("No compatible Apple TV found", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Make sure Apple TV is awake and connected to this phone’s network.",
                        color = RelayFog,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Button(onClick = onRefresh, modifier = Modifier.padding(top = 14.dp)) {
                        Text("Scan again")
                    }
                }
            }
        }
    } else {
        LazyColumn {
            groups.forEach { (title, devices) ->
                item(key = "header:$title") {
                    Text(
                        title,
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 5.dp),
                        color = RelayFog,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                items(devices, key = { it.id }) { device ->
                Surface(
                    onClick = {
                        when {
                            device.selected && device.paired -> onRetry()
                            device.paired -> onSelect(device.id)
                            device.online -> onPair(device.id)
                        }
                    },
                    enabled = device.online || device.paired,
                    color = if (device.selected) RelayCyan.copy(alpha = 0.1f) else RelayCarbon,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .semantics {
                            contentDescription = "${device.name}, ${deviceStatusLabel(device)}"
                        },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                device.model ?: "Apple TV",
                                color = RelayFog,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            ConnectionStatusChip(
                                if (device.online) ConnectionState.Connected else ConnectionState.Offline,
                                if (device.online) "Online" else "Offline",
                            )
                            Text(
                                when {
                                    device.selected && device.online -> "In use"
                                    device.selected -> "Reconnect"
                                    device.paired -> "Use"
                                    else -> "Pair"
                                },
                                color = if (device.online) RelayCyan else RelayFog,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
                }
            }
        }
    }
    TextButton(
        onClick = onDiagnostics,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text("Discovery diagnostics") }
}

private fun deviceStatusLabel(device: DeviceItem): String = when {
    device.online && device.paired -> "online and paired"
    device.online -> "available to pair"
    device.paired -> "paired but offline"
    else -> "offline"
}

@Composable
private fun PairingSheet(
    state: RemoteUiState,
    onPinChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // The code has a fixed length, so the fourth digit finishes the task without another tap.
    LaunchedEffect(state.pin) {
        if (state.pin.length == 4 && !state.pairingInProgress) onSubmit()
    }
    SheetTitle(
        "Pair ${state.selectedDeviceName ?: "Apple TV"}",
        "Enter the four-digit code shown on the television. It is never stored.",
    )
    OutlinedTextField(
        value = state.pin,
        onValueChange = onPinChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 14.dp)
            .focusRequester(focusRequester),
        label = { Text("Pairing code") },
        placeholder = { Text("•  •  •  •", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        singleLine = true,
        enabled = !state.pairingInProgress,
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    Button(
        onClick = onSubmit,
        enabled = state.pin.length == 4 && !state.pairingInProgress,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
    ) {
        if (state.pairingInProgress) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(9.dp))
            Text("Saving connection…")
        } else {
            Text("Pair securely")
        }
    }
    state.error?.let { message ->
        Text(
            message,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun AppsSheet(
    state: RemoteUiState,
    onLaunchApp: (String) -> Unit,
    onFavoriteChanged: (String, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    SheetTitle("Apps", state.selectedDeviceName)
    if (state.apps.size > APP_SEARCH_THRESHOLD) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            label = { Text("Search installed apps") },
            singleLine = true,
        )
    }
    val visible = state.apps.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val favorites = visible.filter { it.bundleId in state.favoriteBundleIds }
    val others = visible.filter { it.bundleId !in state.favoriteBundleIds }
    if (state.appsLoading && state.apps.isEmpty()) {
        CircularProgressIndicator(
            Modifier
                .padding(22.dp)
                .size(24.dp),
            strokeWidth = 2.dp,
        )
    } else if (visible.isEmpty()) {
        Text(
            if (query.isBlank()) "No applications were returned." else "No matching applications.",
            modifier = Modifier.padding(22.dp),
            color = RelayFog,
        )
    } else {
        LazyColumn {
            if (favorites.isNotEmpty()) {
                item(key = "header:favorites") { SettingsSectionLabel("Favorites") }
                items(favorites, key = { it.bundleId }) { app ->
                    AppRow(app, favorite = true, onLaunchApp, onFavoriteChanged)
                }
                if (others.isNotEmpty()) {
                    item(key = "header:all") { SettingsSectionLabel("All apps") }
                }
            }
            items(others, key = { it.bundleId }) { app ->
                AppRow(app, favorite = false, onLaunchApp, onFavoriteChanged)
            }
            item(key = "footer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AppRow(
    app: io.coreback.relayremote.protocol.AppleTvApp,
    favorite: Boolean,
    onLaunchApp: (String) -> Unit,
    onFavoriteChanged: (String, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = { onLaunchApp(app.bundleId) },
            color = RelayCarbon,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Open ${app.name}" },
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(RelayCarbonRaised, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        app.name.take(1).uppercase(),
                        color = RelayCyan,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(
            onClick = { onFavoriteChanged(app.bundleId, !favorite) },
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = if (favorite) {
                        "Remove ${app.name} from favorites"
                    } else {
                        "Add ${app.name} to favorites"
                    }
                },
        ) {
            Text(if (favorite) "★" else "☆", color = if (favorite) RelayCyan else RelayFog)
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun KeyboardSheet(
    state: RemoteUiState,
    onTextChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onStartFresh: () -> Unit,
    onCloseAnyway: () -> Unit,
    onDone: () -> Unit,
) {
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember { mutableStateOf(TextFieldValue(state.keyboardText)) }
    LaunchedEffect(state.keyboardText) {
        if (fieldValue.composition == null && fieldValue.text != state.keyboardText) {
            fieldValue = TextFieldValue(
                text = state.keyboardText,
                selection = TextRange(state.keyboardText.length),
            )
        }
    }
    val editingEnabled = state.keyboardSnapshotLoaded &&
        state.keyboardSyncState != KeyboardSyncState.LOADING &&
        state.keyboardSyncState != KeyboardSyncState.UNAVAILABLE
    LaunchedEffect(editingEnabled) {
        if (editingEnabled) focusRequester.requestFocus()
    }
    val finish = {
        softwareKeyboard?.hide()
        if (!state.keyboardDirty) onDone()
    }
    val status = when (state.keyboardSyncState) {
        KeyboardSyncState.IDLE -> "Waiting for an Apple TV text field"
        KeyboardSyncState.LOADING -> "Loading current text from Apple TV…"
        KeyboardSyncState.SYNCING -> "Sending changes…"
        KeyboardSyncState.SYNCED -> "Ready · changes send live"
        KeyboardSyncState.ERROR -> state.keyboardError ?: "Keyboard sync failed"
        KeyboardSyncState.UNAVAILABLE -> state.keyboardError
            ?: "Open a text field on Apple TV, then retry"
    }
    SheetTitle(
        "Keyboard",
        "What you type appears on Apple TV as you go. Password fields may hide their current text.",
    )
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { value ->
            fieldValue = value
            if (value.composition == null) onTextChanged(value.text)
        },
        enabled = editingEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .focusRequester(focusRequester),
        label = { Text("Text") },
        minLines = 2,
        maxLines = 5,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finish() }),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.keyboardSyncState == KeyboardSyncState.LOADING ||
            state.keyboardSyncState == KeyboardSyncState.SYNCING
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.keyboardSyncState == KeyboardSyncState.ERROR ||
                state.keyboardSyncState == KeyboardSyncState.UNAVAILABLE
            ) {
                MaterialTheme.colorScheme.error
            } else {
                RelayFog
            },
        )
    }
    if (state.keyboardSyncState == KeyboardSyncState.ERROR ||
        state.keyboardSyncState == KeyboardSyncState.UNAVAILABLE
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = onRetry) { Text("Retry") }
            if (!state.keyboardSnapshotLoaded &&
                state.keyboardSyncState == KeyboardSyncState.ERROR
            ) {
                TextButton(onClick = onStartFresh) { Text("Start with empty field") }
            }
            if (state.keyboardDirty) {
                TextButton(onClick = onCloseAnyway) { Text("Close anyway") }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
                onClick = onRefresh,
                enabled = state.keyboardSyncState == KeyboardSyncState.SYNCED &&
                    !state.keyboardDirty,
            ) { Text("Reload TV text") }
            TextButton(
                onClick = { onTextChanged("") },
                enabled = editingEnabled && state.keyboardText.isNotEmpty(),
            ) { Text("Clear") }
        }
        Button(onClick = { finish() }) { Text("Done") }
    }
    Spacer(Modifier.height(24.dp))
}

private const val APP_SEARCH_THRESHOLD = 6

private data class MoreAction(
    val label: String,
    val detail: String,
    val capability: Capability,
    val command: AppleTvCommand,
    val closesSheet: Boolean,
)

private val SYSTEM_ACTIONS = listOf(
    MoreAction("Item menu", "Options for the highlighted item", Capability.SELECT, AppleTvCommand.SELECT_LONG_PRESS, true),
    MoreAction("Control Center", "Or hold Home", Capability.HOME_LONG_PRESS, AppleTvCommand.HOME_LONG_PRESS, true),
    MoreAction("Open apps", "Or double-tap Home", Capability.HOME_DOUBLE_PRESS, AppleTvCommand.HOME_DOUBLE_PRESS, true),
    MoreAction("Home Screen", "Or hold Back", Capability.BACK, AppleTvCommand.BACK_LONG_PRESS, true),
    MoreAction("Screensaver", "Start it now", Capability.SCREENSAVER, AppleTvCommand.SCREENSAVER, true),
)

private val PLAYBACK_ACTIONS = listOf(
    MoreAction("Previous", "Track or chapter", Capability.PREVIOUS, AppleTvCommand.PREVIOUS, false),
    MoreAction("Next", "Track or chapter", Capability.NEXT, AppleTvCommand.NEXT, false),
)

private val LIVE_TV_ACTIONS = listOf(
    MoreAction("Guide", "Live TV listings", Capability.CHANNELS, AppleTvCommand.GUIDE, true),
    MoreAction("Channel up", "Or page up", Capability.CHANNELS, AppleTvCommand.CHANNEL_UP, false),
    MoreAction("Channel down", "Or page down", Capability.CHANNELS, AppleTvCommand.CHANNEL_DOWN, false),
)

private val GESTURE_TIPS = listOf(
    "Touchpad" to "Swipe to move. Tap to select. Tap an edge to move one step, or hold it to keep moving.",
    "Back" to "Tap to go back. Hold to go to the Home Screen.",
    "Home" to "Tap for the Apple TV app. Double-tap for open apps. Hold for Control Center.",
    "Rewind and Forward" to "Tap to skip. Hold to scan when the app supports it.",
    "Phone volume keys" to "Change the TV volume when your audio setup supports it.",
)

private val ARRANGE_APP_STEPS = listOf(
    "On Apple TV, highlight the app.",
    "Hold the center of the touchpad until the menu opens.",
    "To move it, choose Edit Home Screen, swipe to the new place, then tap to drop it.",
    "To remove it, choose Delete App, then Delete or Offload.",
)

@Composable
private fun MoreSheet(
    state: RemoteUiState,
    onCommand: (AppleTvCommand) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onClose: () -> Unit,
) {
    LazyColumn {
        item { SheetTitle("More controls", state.selectedDeviceName) }
        item { MoreActionSection("Apple TV", SYSTEM_ACTIONS, state, onCommand, onClose) }
        item { MoreActionSection("Playback", PLAYBACK_ACTIONS, state, onCommand, onClose) }
        item { MoreActionSection("Live TV", LIVE_TV_ACTIONS, state, onCommand, onClose) }
        if (state.accounts.size > 1) {
            item { SettingsSectionLabel("Switch profile") }
            items(state.accounts, key = { it.id }) { account ->
                SettingsLink(account.name, "Use this profile on Apple TV") {
                    onSwitchAccount(account.id)
                }
            }
        }
        item { SettingsSectionLabel("Move or remove an app") }
        item {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) {
                ARRANGE_APP_STEPS.forEachIndexed { index, step ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${index + 1}.", Modifier.width(24.dp), color = RelayFog, style = MaterialTheme.typography.bodyMedium)
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { SettingsSectionLabel("Gestures") }
        items(GESTURE_TIPS, key = { it.first }) { (title, detail) ->
            Column(Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(detail, color = RelayFog, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Text(
                "Siri, subtitles, and InSight are not available yet because their commands are not publicly documented.",
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                color = RelayFog,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MoreActionSection(
    title: String,
    actions: List<MoreAction>,
    state: RemoteUiState,
    onCommand: (AppleTvCommand) -> Unit,
    onClose: () -> Unit,
) {
    SettingsSectionLabel(title)
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { action ->
                    val enabled = state.controlEnabled(action.capability)
                    Surface(
                        onClick = {
                            onCommand(action.command)
                            if (action.closesSheet) onClose()
                        },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        color = RelayCarbonRaised,
                        contentColor = if (enabled) RelayMoon else RelayFog,
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                            Text(action.label, style = MaterialTheme.typography.titleMedium)
                            Text(action.detail, color = RelayFog, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(
    state: RemoteUiState,
    onKeepScreenAwakeChanged: (Boolean) -> Unit,
    onDirectionalButtonsChanged: (Boolean) -> Unit,
    onSkipSecondsChanged: (Int) -> Unit,
    onRename: (String, String?) -> Unit,
    onForget: (String) -> Unit,
    onRePair: (String) -> Unit,
    onDiagnostics: () -> Unit,
    onLicenses: () -> Unit,
) {
    var alias by remember(state.selectedDeviceName) {
        mutableStateOf(state.selectedDeviceName.orEmpty())
    }
    var confirmForget by remember { mutableStateOf(false) }
    LazyColumn {
        item { SheetTitle("Settings", "Preferences and device details stay on this phone") }
        item { SettingsSectionLabel("Remote") }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep screen awake", fontWeight = FontWeight.Medium)
                    Text("Only while connected", color = RelayFog, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = state.keepScreenAwake,
                    onCheckedChange = onKeepScreenAwakeChanged,
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Directional buttons", fontWeight = FontWeight.Medium)
                    Text(
                        "Use buttons instead of swiping on the touchpad",
                        color = RelayFog,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = state.directionalButtons,
                    onCheckedChange = onDirectionalButtonsChanged,
                )
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            ) {
                Text("Skip duration", fontWeight = FontWeight.Medium)
                Text(
                    "Used by both rewind and forward",
                    color = RelayFog,
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(10, 15, 30, 60).forEach { seconds ->
                        FilterChip(
                            selected = state.skipSeconds == seconds,
                            onClick = { onSkipSecondsChanged(seconds) },
                            label = { Text("$seconds seconds") },
                        )
                    }
                }
            }
        }
        state.selectedDeviceId?.let { id ->
            item { SettingsSectionLabel("Selected Apple TV") }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 5.dp),
                    color = RelayBlack.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.selectedDeviceName ?: "Apple TV",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                            )
                            ConnectionStatusChip(state.connectionState)
                        }
                        Text(
                            if (state.selectedDevice?.paired == true) "Connection saved securely" else "Not paired",
                            color = RelayFog,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    label = { Text("Name on this phone") },
                    singleLine = true,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = { onRename(id, alias.ifBlank { null }) },
                        enabled = alias.trim() != state.selectedDeviceName,
                    ) { Text("Save name") }
                    TextButton(onClick = { onRePair(id) }) { Text("Pair again") }
                }
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                )
                TextButton(
                    onClick = { confirmForget = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Forget this Apple TV") }
            }
        }
        item { SettingsSectionLabel("Support") }
        item { SettingsLink("Diagnostics", "Connection and capability status", onDiagnostics) }
        item { SettingsLink("Open-source licenses", "Libraries used by Relay Remote", onLicenses) }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget ${state.selectedDeviceName ?: "this Apple TV"}?") },
            text = { Text("The saved pairing will be removed from this phone. You’ll need a new Apple TV code to reconnect.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmForget = false
                        state.selectedDeviceId?.let(onForget)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(
        label,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 5.dp),
        color = RelayCyan,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun SettingsLink(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = RelayCarbon,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(detail, color = RelayFog, style = MaterialTheme.typography.bodyMedium)
            }
            RemoteIconGraphic(RemoteIcon.BACK, RelayFog, Modifier.size(16.dp).graphicsLayer { rotationZ = 180f })
        }
    }
}

@Composable
private fun DiagnosticsSheet(state: RemoteUiState) {
    SheetTitle("Diagnostics", "No credentials, PINs, or typed text are shown")
    val rows = listOf(
        "STATE" to connectionLabel(state.connectionState),
        "POWER" to state.powerState.name,
        "DEVICE" to (state.selectedDeviceName ?: "None selected"),
        "ONLINE" to state.devices.count(DeviceItem::online).toString(),
        "IMPLEMENTED" to state.capabilities.implemented.size.toString(),
        "AVAILABLE" to state.capabilities.available.size.toString(),
    )
    rows.forEach { (label, value) ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            Text(label, Modifier.width(110.dp), color = RelayFog, style = MaterialTheme.typography.labelSmall)
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun LicensesSheet() {
    val resources = LocalResources.current
    val notices = remember(resources) {
        resources.openRawResource(R.raw.third_party_licenses)
            .bufferedReader()
            .use { it.readText() }
    }
    SheetTitle("Open-source licenses")
    LazyColumn(
        modifier = Modifier.padding(horizontal = 22.dp),
    ) {
        item {
            Text(
                notices,
                color = RelayFog,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.Scanning -> "Scanning"
    ConnectionState.Pairing -> "Pairing"
    ConnectionState.Connecting -> "Connecting"
    ConnectionState.Connected -> "Connected"
    ConnectionState.Waking -> "Waking"
    is ConnectionState.Reconnecting -> "Reconnecting · ${state.attempt}"
    ConnectionState.Offline -> "Offline"
    ConnectionState.AuthenticationFailed -> "Pair again"
    is ConnectionState.Error -> if (state.retryable) "Connection issue" else "Error"
}
