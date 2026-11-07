package io.coreback.relayremote

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.coreback.relayremote.remote.RemoteScreen
import io.coreback.relayremote.remote.RemoteViewModel
import io.coreback.relayremote.ui.RelayRemoteTheme

class MainActivity : ComponentActivity() {
    private val remoteViewModel: RemoteViewModel by viewModels {
        RemoteViewModel.Factory((application as RelayRemoteApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by remoteViewModel.uiState.collectAsStateWithLifecycle()
            DisposableEffect(state.keepScreenAwake, state.connectionState) {
                val keepAwake = state.keepScreenAwake &&
                    state.connectionState == io.coreback.relayremote.protocol.ConnectionState.Connected
                if (keepAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            RelayRemoteTheme {
                RemoteScreen(
                    state = state,
                    onOpenSheet = remoteViewModel::openSheet,
                    onCloseSheet = remoteViewModel::closeSheet,
                    onSelectDevice = remoteViewModel::selectDevice,
                    onStartPairing = remoteViewModel::startPairing,
                    onPinChanged = remoteViewModel::updatePin,
                    onSubmitPin = remoteViewModel::submitPin,
                    onKeyboardTextChanged = remoteViewModel::updateKeyboardText,
                    onKeyboardOperation = remoteViewModel::keyboard,
                    onCommand = remoteViewModel::send,
                    onSkipBy = remoteViewModel::skipBy,
                    onScan = remoteViewModel::scan,
                    onSwitchAccount = remoteViewModel::switchAccount,
                    onDirectionalButtonsChanged = remoteViewModel::setDirectionalButtons,
                    onTouch = remoteViewModel::touch,
                    onWake = remoteViewModel::wake,
                    onSleep = remoteViewModel::sleep,
                    onVolumeUp = remoteViewModel::volumeUp,
                    onVolumeDown = remoteViewModel::volumeDown,
                    onMute = remoteViewModel::mute,
                    onLaunchApp = remoteViewModel::launchApp,
                    onFavoriteChanged = remoteViewModel::setFavorite,
                    onRename = remoteViewModel::rename,
                    onForget = remoteViewModel::forget,
                    onKeepScreenAwakeChanged = remoteViewModel::setKeepScreenAwake,
                    onSkipSecondsChanged = remoteViewModel::setSkipSeconds,
                    onRefreshDiscovery = remoteViewModel::refreshDiscovery,
                    onRetryConnection = remoteViewModel::retryConnection,
                    onClearError = remoteViewModel::clearError,
                    onRefreshKeyboard = remoteViewModel::refreshKeyboardText,
                    onRetryKeyboard = remoteViewModel::retryKeyboardSync,
                    onStartFreshKeyboard = remoteViewModel::startFreshKeyboardText,
                    onCloseKeyboardAnyway = remoteViewModel::closeKeyboardAnyway,
                    onAddWidget = { RemoteShortcuts.requestHomeScreenWidget(this) },
                    onAddQuickSettingsTile = { RemoteShortcuts.requestQuickSettingsTile(this) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        remoteViewModel.onAppResumed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Holding a volume key keeps changing the level, at a pace the TV can follow.
        if (event.repeatCount % VOLUME_REPEAT_DIVISOR == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> if (remoteViewModel.hardwareVolume(up = true)) return true
                KeyEvent.KEYCODE_VOLUME_DOWN -> if (remoteViewModel.hardwareVolume(up = false)) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        const val VOLUME_REPEAT_DIVISOR = 3
    }
}
