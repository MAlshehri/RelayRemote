package io.coreback.relayremote.remote

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import io.coreback.relayremote.protocol.AppleTvApp
import io.coreback.relayremote.protocol.Capability
import io.coreback.relayremote.protocol.CapabilitySet
import io.coreback.relayremote.protocol.ConnectionState
import io.coreback.relayremote.protocol.PowerState
import io.coreback.relayremote.ui.RelayRemoteTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Reproducible documentation images: fictional data, no network or credentials. */
class PublicScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remote() = capture("remote", RemoteSheet.NONE, "Living Room")
    @Test fun settings() = capture("settings", RemoteSheet.SETTINGS, "Settings")
    @Test fun more() = capture("more", RemoteSheet.MORE, "More controls")
    @Test fun directionalButtons() = capture(
        "directional-buttons",
        RemoteSheet.NONE,
        "Living Room",
        directionalButtons = true,
    )

    private fun capture(
        name: String,
        sheet: RemoteSheet,
        expectedText: String,
        directionalButtons: Boolean = false,
    ) {
        val available = Capability.entries.toSet() - Capability.NOW_PLAYING
        compose.setContent {
            RelayRemoteTheme {
                RemoteScreen(
                    state = RemoteUiState(
                        selectedDeviceId = "demo-living-room",
                        selectedDeviceName = "Living Room",
                        connectionState = ConnectionState.Connected,
                        powerState = PowerState.ON,
                        capabilities = CapabilitySet(implemented = available, available = available),
                        discoveryRefreshing = false,
                        sheet = sheet,
                        directionalButtons = directionalButtons,
                        apps = listOf(
                            AppleTvApp("demo.movies", "Movies"),
                            AppleTvApp("demo.music", "Music"),
                            AppleTvApp("demo.photos", "Photos"),
                        ),
                        favoriteBundleIds = setOf("demo.movies", "demo.music", "demo.photos"),
                    ),
                    onOpenSheet = {}, onCloseSheet = {}, onSelectDevice = {},
                    onStartPairing = {}, onPinChanged = {}, onSubmitPin = {},
                    onKeyboardTextChanged = {}, onKeyboardOperation = {}, onCommand = {},
                    onSkipBy = {}, onTouch = {}, onWake = {}, onSleep = {},
                    onVolumeUp = {}, onVolumeDown = {}, onMute = {}, onLaunchApp = {},
                    onFavoriteChanged = { _, _ -> }, onRename = { _, _ -> }, onForget = {},
                    onKeepScreenAwakeChanged = {}, onSkipSecondsChanged = {}, onClearError = {},
                )
            }
        }
        compose.onNodeWithText(expectedText).assertIsDisplayed()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
