package io.coreback.relayremote.remote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.Composable
import io.coreback.relayremote.protocol.AppleTvCommand
import io.coreback.relayremote.protocol.Capability
import io.coreback.relayremote.protocol.CapabilitySet
import io.coreback.relayremote.protocol.ConnectionState
import io.coreback.relayremote.protocol.PowerState
import io.coreback.relayremote.protocol.NowPlaying
import io.coreback.relayremote.protocol.TouchGesture
import io.coreback.relayremote.ui.RelayRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RemoteScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun remote_showsSelectedDeviceAndCapabilityGatesControls() {
        val available = setOf(
            Capability.NAVIGATION,
            Capability.SELECT,
            Capability.BACK,
            Capability.HOME,
            Capability.PLAY_PAUSE,
            Capability.VOLUME,
        )
        compose.setContent {
            RelayRemoteTheme {
                RemoteScreen(
                    state = RemoteUiState(
                        selectedDeviceName = "Living Room",
                        connectionState = ConnectionState.Connected,
                        powerState = PowerState.ON,
                        capabilities = CapabilitySet(
                            implemented = available,
                            available = available,
                        ),
                    ),
                    onOpenSheet = {},
                    onCloseSheet = {},
                    onSelectDevice = {},
                    onStartPairing = {},
                    onPinChanged = {},
                    onSubmitPin = {},
                    onKeyboardTextChanged = {},
                    onKeyboardOperation = {},
                    onCommand = { _: AppleTvCommand -> },
                    onSkipBy = {},
                    onTouch = { _: TouchGesture -> },
                    onWake = {},
                    onSleep = {},
                    onVolumeUp = {},
                    onVolumeDown = {},
                    onMute = {},
                    onLaunchApp = {},
                    onFavoriteChanged = { _, _ -> },
                    onRename = { _, _ -> },
                    onForget = {},
                    onKeepScreenAwakeChanged = {},
                    onSkipSecondsChanged = {},
                    onClearError = {},
                )
            }
        }

        compose.onNodeWithText("Living Room").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsEnabled()
        compose.onNodeWithContentDescription("Mute").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Apps").assertIsNotEnabled()
    }

    @Test
    fun devicesButtonOpensDeviceSheet() {
        var opened: RemoteSheet? = null
        compose.setContent {
            RelayRemoteTheme {
                RemoteScreen(
                    state = RemoteUiState(selectedDeviceName = "Choose Apple TV"),
                    onOpenSheet = { opened = it },
                    onCloseSheet = {},
                    onSelectDevice = {},
                    onStartPairing = {},
                    onPinChanged = {},
                    onSubmitPin = {},
                    onKeyboardTextChanged = {},
                    onKeyboardOperation = {},
                    onCommand = {},
                    onSkipBy = {},
                    onTouch = {},
                    onWake = {},
                    onSleep = {},
                    onVolumeUp = {},
                    onVolumeDown = {},
                    onMute = {},
                    onLaunchApp = {},
                    onFavoriteChanged = { _, _ -> },
                    onRename = { _, _ -> },
                    onForget = {},
                    onKeepScreenAwakeChanged = {},
                    onSkipSecondsChanged = {},
                    onClearError = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Choose Apple TV").performClick()
        assert(opened == RemoteSheet.DEVICES)
    }

    @Test
    fun playbackRowShowsConfiguredDurationAndIndependentCapabilityGates() {
        var skippedBy: Int? = null
        val available = setOf(Capability.SKIP_BACKWARD)
        compose.setContent {
            RelayRemoteTheme {
                RemoteScreen(
                    state = RemoteUiState(
                        skipSeconds = 30,
                        connectionState = ConnectionState.Connected,
                        capabilities = CapabilitySet(
                            implemented = available,
                            available = available,
                        ),
                    ),
                    onOpenSheet = {},
                    onCloseSheet = {},
                    onSelectDevice = {},
                    onStartPairing = {},
                    onPinChanged = {},
                    onSubmitPin = {},
                    onKeyboardTextChanged = {},
                    onKeyboardOperation = {},
                    onCommand = {},
                    onSkipBy = { skippedBy = it },
                    onTouch = {},
                    onWake = {},
                    onSleep = {},
                    onVolumeUp = {},
                    onVolumeDown = {},
                    onMute = {},
                    onLaunchApp = {},
                    onFavoriteChanged = { _, _ -> },
                    onRename = { _, _ -> },
                    onForget = {},
                    onKeepScreenAwakeChanged = {},
                    onSkipSecondsChanged = {},
                    onClearError = {},
                )
            }
        }

        compose.onNodeWithTag("Playback skip controls").assertIsDisplayed()
        compose.onAllNodesWithText("30s").assertCountEquals(2)
        compose.onNodeWithContentDescription("Rewind 30 seconds")
            .assertIsEnabled()
            .performClick()
        assertEquals(-30, skippedBy)
        compose.onNodeWithContentDescription("Forward 30 seconds")
            .assertIsNotEnabled()
    }

    @Test
    fun playbackRowUsesConfiguredSixtySecondDuration() {
        val available = setOf(Capability.SKIP_BACKWARD, Capability.SKIP_FORWARD)
        compose.setContent {
            RelayRemoteTheme {
                RemoteScreen(
                    state = RemoteUiState(
                        skipSeconds = 60,
                        connectionState = ConnectionState.Connected,
                        capabilities = CapabilitySet(
                            implemented = available,
                            available = available,
                        ),
                    ),
                    onOpenSheet = {},
                    onCloseSheet = {},
                    onSelectDevice = {},
                    onStartPairing = {},
                    onPinChanged = {},
                    onSubmitPin = {},
                    onKeyboardTextChanged = {},
                    onKeyboardOperation = {},
                    onCommand = {},
                    onSkipBy = {},
                    onTouch = {},
                    onWake = {},
                    onSleep = {},
                    onVolumeUp = {},
                    onVolumeDown = {},
                    onMute = {},
                    onLaunchApp = {},
                    onFavoriteChanged = { _, _ -> },
                    onRename = { _, _ -> },
                    onForget = {},
                    onKeepScreenAwakeChanged = {},
                    onSkipSecondsChanged = {},
                    onClearError = {},
                )
            }
        }

        compose.onAllNodesWithText("60s").assertCountEquals(2)
        compose.onNodeWithContentDescription("Rewind 60 seconds").assertIsEnabled()
        compose.onNodeWithContentDescription("Forward 60 seconds").assertIsEnabled()
    }

    @Test
    fun pairingConfirmationIsDisplayed() {
        compose.setContent {
            RelayRemoteTheme {
                RemoteScreen(
                    state = RemoteUiState(confirmation = "Paired and saved"),
                    onOpenSheet = {},
                    onCloseSheet = {},
                    onSelectDevice = {},
                    onStartPairing = {},
                    onPinChanged = {},
                    onSubmitPin = {},
                    onKeyboardTextChanged = {},
                    onKeyboardOperation = {},
                    onCommand = {},
                    onSkipBy = {},
                    onTouch = {},
                    onWake = {},
                    onSleep = {},
                    onVolumeUp = {},
                    onVolumeDown = {},
                    onMute = {},
                    onLaunchApp = {},
                    onFavoriteChanged = { _, _ -> },
                    onRename = { _, _ -> },
                    onForget = {},
                    onKeepScreenAwakeChanged = {},
                    onSkipSecondsChanged = {},
                    onClearError = {},
                )
            }
        }

        compose.onNodeWithText("Paired and saved").assertIsDisplayed()
    }

    @Test
    fun settingsOffersFixedSkipDurations() {
        var selectedSeconds: Int? = null
        compose.setContent {
            RelayRemoteTheme {
                RemoteScreen(
                    state = RemoteUiState(
                        sheet = RemoteSheet.SETTINGS,
                        skipSeconds = 30,
                    ),
                    onOpenSheet = {},
                    onCloseSheet = {},
                    onSelectDevice = {},
                    onStartPairing = {},
                    onPinChanged = {},
                    onSubmitPin = {},
                    onKeyboardTextChanged = {},
                    onKeyboardOperation = {},
                    onCommand = {},
                    onSkipBy = {},
                    onTouch = {},
                    onWake = {},
                    onSleep = {},
                    onVolumeUp = {},
                    onVolumeDown = {},
                    onMute = {},
                    onLaunchApp = {},
                    onFavoriteChanged = { _, _ -> },
                    onRename = { _, _ -> },
                    onForget = {},
                    onKeepScreenAwakeChanged = {},
                    onSkipSecondsChanged = { selectedSeconds = it },
                    onClearError = {},
                )
            }
        }

        compose.onNodeWithText("60 seconds").performScrollTo().performClick()
        assertEquals(60, selectedSeconds)
    }

    @Test
    fun discoveredAppleTvShowsReadyGuidanceInsteadOfFalseEmptyState() {
        compose.setContent {
            TestRemoteScreen(
                RemoteUiState(
                    discoveryRefreshing = false,
                    devices = listOf(
                        DeviceItem("tv", "Living Room", "AppleTV14,1", online = true, paired = false, selected = false),
                    ),
                ),
            )
        }

        compose.onNodeWithText("1 Apple TV ready").assertIsDisplayed()
        compose.onAllNodesWithText("Choose Apple TV").assertCountEquals(2)
        compose.onAllNodesWithText("No Apple TV found yet").assertCountEquals(0)
    }

    @Test
    fun pairingBusyStateDisablesCodeAndSubmission() {
        compose.setContent {
            TestRemoteScreen(
                RemoteUiState(
                    sheet = RemoteSheet.PAIRING,
                    selectedDeviceName = "Living Room",
                    pin = "1234",
                    pairingInProgress = true,
                ),
            )
        }

        compose.onNodeWithText("Saving connection…").assertIsDisplayed()
        compose.onNodeWithText("Pairing code").assertIsNotEnabled()
    }

    @Test
    fun keyboardLoadsExistingTvTextAndShowsSyncedState() {
        var reloadCalls = 0
        compose.setContent {
            TestRemoteScreen(
                state = RemoteUiState(
                    sheet = RemoteSheet.KEYBOARD,
                    keyboardText = "Already on TV",
                    keyboardSyncState = KeyboardSyncState.SYNCED,
                    keyboardSnapshotLoaded = true,
                ),
                onRefreshKeyboard = { reloadCalls += 1 },
            )
        }

        compose.onNodeWithText("Already on TV").assertIsDisplayed()
        compose.onNodeWithText("Ready · changes send live").assertIsDisplayed()
        compose.onNodeWithText("Reload TV text").performClick()
        assertEquals(1, reloadCalls)
    }

    @Test
    fun keyboardInitialErrorOffersRetryAndExplicitEmptyStart() {
        var retryCalls = 0
        var startFreshCalls = 0
        compose.setContent {
            TestRemoteScreen(
                state = RemoteUiState(
                    sheet = RemoteSheet.KEYBOARD,
                    keyboardSyncState = KeyboardSyncState.ERROR,
                    keyboardError = "Could not read the TV field",
                ),
                onRetryKeyboard = { retryCalls += 1 },
                onStartFreshKeyboard = { startFreshCalls += 1 },
            )
        }

        compose.onNodeWithText("Could not read the TV field").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Start with empty field").performClick()
        assertEquals(1, retryCalls)
        assertEquals(1, startFreshCalls)
    }

    @Test
    fun unavailableKeyboardDisablesClearAndExplainsRecovery() {
        compose.setContent {
            TestRemoteScreen(
                RemoteUiState(
                    sheet = RemoteSheet.KEYBOARD,
                    keyboardText = "Local text",
                    keyboardSyncState = KeyboardSyncState.UNAVAILABLE,
                    keyboardSnapshotLoaded = true,
                    keyboardError = "Open a text field on Apple TV, then retry",
                ),
            )
        }

        compose.onNodeWithText("Open a text field on Apple TV, then retry").assertIsDisplayed()
        compose.onNodeWithText("Clear").assertIsNotEnabled()
    }

    @Test
    fun unsentKeyboardTextDisablesNormalCloseAndOffersCloseAnyway() {
        var closeAnywayCalls = 0
        compose.setContent {
            TestRemoteScreen(
                state = RemoteUiState(
                    sheet = RemoteSheet.KEYBOARD,
                    keyboardText = "Unsent",
                    keyboardSyncState = KeyboardSyncState.ERROR,
                    keyboardSnapshotLoaded = true,
                    keyboardDirty = true,
                    keyboardError = "Could not synchronize with Apple TV.",
                ),
                onCloseKeyboardAnyway = { closeAnywayCalls += 1 },
            )
        }

        compose.onNodeWithText("Close").assertIsNotEnabled()
        compose.onNodeWithText("Close anyway").performClick()
        assertEquals(1, closeAnywayCalls)
    }

    @Test
    fun authenticationFailureOffersDirectPairAgainRecovery() {
        var pairedId: String? = null
        compose.setContent {
            TestRemoteScreen(
                state = RemoteUiState(
                    selectedDeviceId = "tv",
                    selectedDeviceName = "Living Room",
                    connectionState = ConnectionState.AuthenticationFailed,
                ),
                onStartPairing = { pairedId = it },
            )
        }

        compose.onNodeWithText("Pairing needs attention").assertIsDisplayed()
        compose.onNodeWithTag("Primary connection action").performClick()
        assertEquals("tv", pairedId)
    }

    @Test
    fun sleepUsesVisibleConfirmationBeforeSendingCommand() {
        var sleepCalls = 0
        val capabilities = setOf(Capability.SLEEP)
        compose.setContent {
            TestRemoteScreen(
                state = RemoteUiState(
                    selectedDeviceName = "Living Room",
                    connectionState = ConnectionState.Connected,
                    powerState = PowerState.ON,
                    capabilities = CapabilitySet(capabilities, capabilities),
                ),
                onSleep = { sleepCalls += 1 },
            )
        }

        compose.onNodeWithContentDescription("Sleep").performClick()
        compose.onNodeWithText("Put Apple TV to sleep?").assertIsDisplayed()
        assertEquals(0, sleepCalls)
        compose.onNodeWithText("Sleep").performClick()
        assertEquals(1, sleepCalls)
    }

    @Test
    fun settingsRequiresConfirmationBeforeForgettingDevice() {
        var forgotten: String? = null
        compose.setContent {
            TestRemoteScreen(
                state = RemoteUiState(
                    sheet = RemoteSheet.SETTINGS,
                    selectedDeviceId = "tv",
                    selectedDeviceName = "Living Room",
                    devices = listOf(
                        DeviceItem("tv", "Living Room", null, online = false, paired = true, selected = true),
                    ),
                ),
                onForget = { forgotten = it },
            )
        }

        compose.onNodeWithText("Forget this Apple TV").performScrollTo().performClick()
        compose.onNodeWithText("Forget Living Room?").assertIsDisplayed()
        assertEquals(null, forgotten)
        compose.onNodeWithText("Forget").performClick()
        assertEquals("tv", forgotten)
    }

    @Test
    fun nestedSheetsExposeBackAndCloseTaskNavigation() {
        var opened: RemoteSheet? = null
        var closed = false
        compose.setContent {
            TestRemoteScreen(
                state = RemoteUiState(sheet = RemoteSheet.DIAGNOSTICS),
                onOpenSheet = { opened = it },
                onCloseSheet = { closed = true },
            )
        }

        compose.onNodeWithText("Back").performClick()
        assertEquals(RemoteSheet.SETTINGS, opened)
        compose.onNodeWithText("Close").performClick()
        assertEquals(true, closed)
    }

    @Test
    fun nowPlayingExposesPlaybackStateAndProgress() {
        compose.setContent {
            TestRemoteScreen(
                RemoteUiState(
                    connectionState = ConnectionState.Connected,
                    nowPlaying = NowPlaying(
                        title = "Foundation",
                        artist = "Apple TV+",
                        album = null,
                        appName = "TV",
                        durationMillis = 100_000,
                        positionMillis = 40_000,
                        isPlaying = true,
                    ),
                ),
            )
        }

        compose.onNodeWithText("Foundation").assertIsDisplayed()
        compose.onNodeWithText("Playing").assertIsDisplayed()
    }
}

@Composable
private fun TestRemoteScreen(
    state: RemoteUiState,
    onOpenSheet: (RemoteSheet) -> Unit = {},
    onCloseSheet: () -> Unit = {},
    onStartPairing: (String) -> Unit = {},
    onSleep: () -> Unit = {},
    onForget: (String) -> Unit = {},
    onRefreshKeyboard: () -> Unit = {},
    onRetryKeyboard: () -> Unit = {},
    onStartFreshKeyboard: () -> Unit = {},
    onCloseKeyboardAnyway: () -> Unit = {},
) {
    RelayRemoteTheme {
        RemoteScreen(
            state = state,
            onOpenSheet = onOpenSheet,
            onCloseSheet = onCloseSheet,
            onSelectDevice = {},
            onStartPairing = onStartPairing,
            onPinChanged = {},
            onSubmitPin = {},
            onKeyboardTextChanged = {},
            onKeyboardOperation = {},
            onCommand = {},
            onSkipBy = {},
            onTouch = {},
            onWake = {},
            onSleep = onSleep,
            onVolumeUp = {},
            onVolumeDown = {},
            onMute = {},
            onLaunchApp = {},
            onFavoriteChanged = { _, _ -> },
            onRename = { _, _ -> },
            onForget = onForget,
            onKeepScreenAwakeChanged = {},
            onSkipSecondsChanged = {},
            onClearError = {},
            onRefreshKeyboard = onRefreshKeyboard,
            onRetryKeyboard = onRetryKeyboard,
            onStartFreshKeyboard = onStartFreshKeyboard,
            onCloseKeyboardAnyway = onCloseKeyboardAnyway,
        )
    }
}
