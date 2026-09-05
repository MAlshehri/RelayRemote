package io.coreback.relayremote.remote

import io.coreback.relayremote.protocol.AppleTvApp
import io.coreback.relayremote.protocol.Capability
import io.coreback.relayremote.protocol.CapabilitySet
import io.coreback.relayremote.protocol.ConnectionState
import io.coreback.relayremote.protocol.NowPlaying
import io.coreback.relayremote.protocol.PowerState

enum class RemoteSheet {
    NONE,
    DEVICES,
    PAIRING,
    APPS,
    KEYBOARD,
    MORE,
    SETTINGS,
    DIAGNOSTICS,
    LICENSES,
}

enum class KeyboardSyncState {
    IDLE,
    LOADING,
    SYNCING,
    SYNCED,
    ERROR,
    UNAVAILABLE,
}

data class DeviceItem(
    val id: String,
    val name: String,
    val model: String?,
    val online: Boolean,
    val paired: Boolean,
    val selected: Boolean,
)

data class RemoteUiState(
    val devices: List<DeviceItem> = emptyList(),
    val selectedDeviceId: String? = null,
    val selectedDeviceName: String? = null,
    val connectionState: ConnectionState = ConnectionState.Scanning,
    val capabilities: CapabilitySet = CapabilitySet.None,
    val powerState: PowerState = PowerState.UNKNOWN,
    val nowPlaying: NowPlaying? = null,
    val sheet: RemoteSheet = RemoteSheet.NONE,
    val apps: List<AppleTvApp> = emptyList(),
    val favoriteBundleIds: Set<String> = emptySet(),
    val appsLoading: Boolean = false,
    val discoveryRefreshing: Boolean = true,
    val discoveryFailure: String? = null,
    val connectionRetrying: Boolean = false,
    val pairingInProgress: Boolean = false,
    val pin: String = "",
    val keyboardText: String = "",
    val keyboardSyncState: KeyboardSyncState = KeyboardSyncState.IDLE,
    val keyboardSnapshotLoaded: Boolean = false,
    val keyboardDirty: Boolean = false,
    val keyboardError: String? = null,
    val keepScreenAwake: Boolean = true,
    val skipSeconds: Int = 30,
    val confirmation: String? = null,
    val error: String? = null,
) {
    val selectedDevice: DeviceItem?
        get() = devices.firstOrNull { it.id == selectedDeviceId }

    fun controlEnabled(capability: Capability): Boolean =
        connectionState == ConnectionState.Connected && capabilities.supports(capability)
}
