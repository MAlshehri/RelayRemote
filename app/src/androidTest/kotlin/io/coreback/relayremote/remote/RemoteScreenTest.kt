package io.coreback.relayremote.remote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.Composable
import io.coreback.relayremote.protocol.AppleTvAccount
import io.coreback.relayremote.protocol.AppleTvApp
import io.coreback.relayremote.protocol.AppleTvCommand
import io.coreback.relayremote.protocol.Capability
import io.coreback.relayremote.protocol.CapabilitySet
import io.coreback.relayremote.protocol.ConnectionState
import io.coreback.relayremote.protocol.PowerState
import io.coreback.relayremote.protocol.ScanDirection
import io.coreback.relayremote.protocol.NowPlaying
import io.coreback.relayremote.protocol.TouchGesture
import io.coreback.relayremote.ui.RelayRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Forget this Apple TV"))
        compose.onNodeWithText("Forget this Apple TV").performClick()
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

    private val allControls = CapabilitySet(
        implemented = Capability.entries.toSet(),
        available = Capability.entries.toSet(),
    )

    private fun connectedState() = RemoteUiState(
        selectedDeviceId = "tv",
        selectedDeviceName = "Living Room",
        connectionState = ConnectionState.Connected,
        powerState = PowerState.ON,
        capabilities = allControls,
    )

    private fun setRecordingScreen(
        state: RemoteUiState,
        commands: MutableList<AppleTvCommand> = mutableListOf(),
        touches: MutableList<TouchGesture> = mutableListOf(),
    ) {
        compose.setContent {
            TestRemoteScreen(
                state = state,
                onCommand = { commands += it },
                onTouch = { touches += it },
            )
        }
    }

    @Test
    fun touchpadTapSelectsImmediatelyWithoutTouchEvents() {
        val commands = mutableListOf<AppleTvCommand>()
        val touches = mutableListOf<TouchGesture>()
        setRecordingScreen(connectedState(), commands, touches)

        compose.onNodeWithTag("Touchpad").performTouchInput { click(center) }

        compose.runOnIdle {
            assertEquals(listOf(AppleTvCommand.SELECT), commands)
            assertEquals(emptyList<TouchGesture>(), touches)
        }
    }

    @Test
    fun touchpadHoldPressesSelectUntilTheFingerLifts() {
        val commands = mutableListOf<AppleTvCommand>()
        val touches = mutableListOf<TouchGesture>()
        setRecordingScreen(connectedState(), commands, touches)

        compose.onNodeWithTag("Touchpad").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(REMOTE_HOLD_MILLIS + 50)
        compose.runOnIdle { assertEquals(listOf<TouchGesture>(TouchGesture.HoldStarted), touches) }

        compose.onNodeWithTag("Touchpad").performTouchInput { up() }

        compose.runOnIdle {
            assertEquals(
                listOf(TouchGesture.HoldStarted, TouchGesture.HoldEnded),
                touches,
            )
            assertEquals(emptyList<AppleTvCommand>(), commands)
        }
    }

    @Test
    fun touchpadSwipeStreamsLiveAndEndsWhereTheFingerLifts() {
        val commands = mutableListOf<AppleTvCommand>()
        val touches = mutableListOf<TouchGesture>()
        setRecordingScreen(connectedState(), commands, touches)

        compose.onNodeWithTag("Touchpad").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(120f, 0f))
        }
        compose.runOnIdle {
            assertTrue(touches.first() is TouchGesture.Began)
            assertFalse(touches.any { it is TouchGesture.Ended })
        }

        compose.onNodeWithTag("Touchpad").performTouchInput { up() }

        compose.runOnIdle {
            assertTrue(touches.last() is TouchGesture.Ended)
            assertFalse(touches.contains(TouchGesture.HoldStarted))
            assertEquals(emptyList<AppleTvCommand>(), commands)
        }
    }

    @Test
    fun touchpadEdgeTapMovesOneStep() {
        val commands = mutableListOf<AppleTvCommand>()
        setRecordingScreen(connectedState(), commands)

        compose.onNodeWithTag("Touchpad").performTouchInput { click(centerLeft) }

        compose.runOnIdle { assertEquals(listOf(AppleTvCommand.LEFT), commands) }
    }

    @Test
    fun touchpadOffersAccessibleMenuAction() {
        val commands = mutableListOf<AppleTvCommand>()
        setRecordingScreen(connectedState(), commands)

        compose.onNodeWithTag("Touchpad").performSemanticsAction(SemanticsActions.OnLongClick)

        compose.runOnIdle { assertEquals(listOf(AppleTvCommand.SELECT_LONG_PRESS), commands) }
    }

    @Test
    fun holdingBackGoesToTheHomeScreen() {
        val commands = mutableListOf<AppleTvCommand>()
        setRecordingScreen(connectedState(), commands)

        compose.onNodeWithContentDescription("Back").performTouchInput { longClick() }

        compose.runOnIdle { assertEquals(listOf(AppleTvCommand.BACK_LONG_PRESS), commands) }
    }

    @Test
    fun directionalButtonsReplaceTheTouchpad() {
        val commands = mutableListOf<AppleTvCommand>()
        val touches = mutableListOf<TouchGesture>()
        setRecordingScreen(connectedState().copy(directionalButtons = true), commands, touches)

        compose.onNodeWithTag("Touchpad").assertDoesNotExist()
        compose.onNodeWithContentDescription("Up").performClick()
        compose.onNodeWithContentDescription("Select").performClick()

        compose.onNodeWithContentDescription("Select").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(REMOTE_HOLD_MILLIS + 50)
        compose.onNodeWithContentDescription("Select").performTouchInput { up() }

        compose.runOnIdle {
            assertEquals(listOf(AppleTvCommand.UP, AppleTvCommand.SELECT), commands)
            assertEquals(listOf(TouchGesture.HoldStarted, TouchGesture.HoldEnded), touches)
        }
    }

    @Test
    fun holdingForwardScansUntilReleasedAndTappingSkips() {
        val scans = mutableListOf<Pair<ScanDirection, Boolean>>()
        val skips = mutableListOf<Int>()
        compose.setContent {
            TestRemoteScreen(
                state = connectedState().copy(skipSeconds = 15),
                onScan = { direction, active -> scans += direction to active },
                onSkipBy = { skips += it },
            )
        }

        compose.onNodeWithContentDescription("Forward 15 seconds").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf(15), skips) }

        compose.onNodeWithContentDescription("Forward 15 seconds").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(REMOTE_HOLD_MILLIS + 50)
        compose.onNodeWithContentDescription("Forward 15 seconds").performTouchInput { up() }

        compose.runOnIdle {
            assertEquals(
                listOf(ScanDirection.FAST_FORWARD to true, ScanDirection.FAST_FORWARD to false),
                scans,
            )
            assertEquals(listOf(15), skips)
        }
    }

    @Test
    fun skipWithoutScanSupportStaysATapAfterALongPress() {
        val scans = mutableListOf<Pair<ScanDirection, Boolean>>()
        val skips = mutableListOf<Int>()
        val available = Capability.entries.toSet() - Capability.FAST_FORWARD
        compose.setContent {
            TestRemoteScreen(
                state = connectedState().copy(
                    capabilities = CapabilitySet(implemented = available, available = available),
                ),
                onScan = { direction, active -> scans += direction to active },
                onSkipBy = { skips += it },
            )
        }

        compose.onNodeWithContentDescription("Forward 30 seconds").performTouchInput { longClick() }

        compose.runOnIdle {
            assertEquals(emptyList<Pair<ScanDirection, Boolean>>(), scans)
            assertEquals(listOf(30), skips)
        }
    }

    @Test
    fun scanWorksWithoutFixedSkipAndTapDoesNothing() {
        val scans = mutableListOf<Pair<ScanDirection, Boolean>>()
        val skips = mutableListOf<Int>()
        val available = Capability.entries.toSet() - Capability.SKIP_FORWARD
        compose.setContent {
            TestRemoteScreen(
                state = connectedState().copy(
                    capabilities = CapabilitySet(implemented = available, available = available),
                ),
                onScan = { direction, active -> scans += direction to active },
                onSkipBy = { skips += it },
            )
        }

        compose.onNodeWithContentDescription("Forward 30 seconds").performTouchInput { click(center) }
        compose.onNodeWithContentDescription("Forward 30 seconds").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(REMOTE_HOLD_MILLIS + 50)
        compose.onNodeWithContentDescription("Forward 30 seconds").performTouchInput { up() }

        compose.runOnIdle {
            assertEquals(emptyList<Int>(), skips)
            assertEquals(
                listOf(ScanDirection.FAST_FORWARD to true, ScanDirection.FAST_FORWARD to false),
                scans,
            )
        }
    }

    @Test
    fun favoriteAppsAppearOnTheRemoteAndLaunch() {
        val launched = mutableListOf<String>()
        compose.setContent {
            TestRemoteScreen(
                state = connectedState().copy(
                    apps = listOf(AppleTvApp("com.a", "Alpha"), AppleTvApp("com.b", "Beta")),
                    favoriteBundleIds = setOf("com.b"),
                ),
                onLaunchApp = { launched += it },
            )
        }

        compose.onNodeWithContentDescription("Open Alpha").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open Beta").performClick()

        compose.runOnIdle { assertEquals(listOf("com.b"), launched) }
    }

    @Test
    fun muteShowsItsCurrentState() {
        compose.setContent { TestRemoteScreen(state = connectedState().copy(muted = true)) }

        compose.onNodeWithContentDescription("Unmute").assertIsEnabled()
        compose.onNodeWithContentDescription("Mute").assertDoesNotExist()
    }

    @Test
    fun moreSheetOpensItemMenuAndClosesAndExplainsMovingApps() {
        val commands = mutableListOf<AppleTvCommand>()
        var closed = false
        compose.setContent {
            TestRemoteScreen(
                state = connectedState().copy(sheet = RemoteSheet.MORE),
                onCommand = { commands += it },
                onCloseSheet = { closed = true },
            )
        }

        compose.onNodeWithText("Move or remove an app").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Item menu").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(listOf(AppleTvCommand.SELECT_LONG_PRESS), commands)
            assertTrue(closed)
        }
    }

    @Test
    fun profilesAppearOnlyWhenThereIsAChoice() {
        val switched = mutableListOf<String>()
        compose.setContent {
            TestRemoteScreen(
                state = connectedState().copy(
                    sheet = RemoteSheet.MORE,
                    accounts = listOf(AppleTvAccount("1", "Sam"), AppleTvAccount("2", "Noor")),
                ),
                onSwitchAccount = { switched += it },
            )
        }

        compose.onNodeWithText("Noor").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(listOf("2"), switched) }
    }

    @Test
    fun fourthPinDigitSubmitsPairing() {
        var submits = 0
        compose.setContent {
            TestRemoteScreen(
                state = RemoteUiState(selectedDeviceName = "Living Room", sheet = RemoteSheet.PAIRING, pin = "1234"),
                onSubmitPin = { submits += 1 },
            )
        }

        compose.runOnIdle { assertEquals(1, submits) }
    }

    @Test
    fun settingsOffersWidgetAndQuickSettingsIcon() {
        var widgetRequests = 0
        var tileRequests = 0
        compose.setContent {
            TestRemoteScreen(
                state = connectedState().copy(sheet = RemoteSheet.SETTINGS),
                onAddWidget = { widgetRequests += 1 },
                onAddQuickSettingsTile = { tileRequests += 1 },
            )
        }

        compose.onNodeWithText("Add Home screen widget").performClick()
        compose.onNodeWithText("Add Quick Settings icon").performClick()

        compose.runOnIdle {
            assertEquals(1, widgetRequests)
            assertEquals(1, tileRequests)
        }
    }

    @Test
    fun asleepTvOffersWake() {
        var wakes = 0
        compose.setContent {
            TestRemoteScreen(
                state = connectedState().copy(powerState = PowerState.OFF),
                onWake = { wakes += 1 },
            )
        }

        compose.onNodeWithText("Living Room is asleep").assertIsDisplayed()
        compose.onNodeWithText("Wake").performClick()

        compose.runOnIdle { assertEquals(1, wakes) }
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
    onCommand: (AppleTvCommand) -> Unit = {},
    onTouch: (TouchGesture) -> Unit = {},
    onScan: (ScanDirection, Boolean) -> Unit = { _, _ -> },
    onSkipBy: (Int) -> Unit = {},
    onLaunchApp: (String) -> Unit = {},
    onSwitchAccount: (String) -> Unit = {},
    onSubmitPin: () -> Unit = {},
    onWake: () -> Unit = {},
    onRefreshKeyboard: () -> Unit = {},
    onRetryKeyboard: () -> Unit = {},
    onStartFreshKeyboard: () -> Unit = {},
    onCloseKeyboardAnyway: () -> Unit = {},
    onAddWidget: () -> Unit = {},
    onAddQuickSettingsTile: () -> Unit = {},
) {
    RelayRemoteTheme {
        RemoteScreen(
            state = state,
            onOpenSheet = onOpenSheet,
            onCloseSheet = onCloseSheet,
            onSelectDevice = {},
            onStartPairing = onStartPairing,
            onPinChanged = {},
            onSubmitPin = onSubmitPin,
            onKeyboardTextChanged = {},
            onKeyboardOperation = {},
            onCommand = onCommand,
            onSkipBy = onSkipBy,
            onScan = onScan,
            onTouch = onTouch,
            onWake = onWake,
            onSleep = onSleep,
            onVolumeUp = {},
            onVolumeDown = {},
            onMute = {},
            onLaunchApp = onLaunchApp,
            onSwitchAccount = onSwitchAccount,
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
            onAddWidget = onAddWidget,
            onAddQuickSettingsTile = onAddQuickSettingsTile,
        )
    }
}
