package io.coreback.relayremote.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.coreback.relayremote.AppContainer
import io.coreback.relayremote.data.DeviceIdentityMatcher
import io.coreback.relayremote.data.DeviceRepository
import io.coreback.relayremote.devices.AppleTvDiscovery
import io.coreback.relayremote.protocol.AppleTvClient
import io.coreback.relayremote.protocol.AppleTvCommand
import io.coreback.relayremote.protocol.Capability
import io.coreback.relayremote.protocol.KeyboardOperation
import io.coreback.relayremote.protocol.TouchGesture
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RemoteViewModel(
    private val discovery: AppleTvDiscovery,
    private val repository: DeviceRepository,
    private val clientFactory: (String) -> AppleTvClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()
    private var client: AppleTvClient? = null
    private var activeDeviceId: String? = null
    private var activeCanReconnect = false
    private var clientStateJob: Job? = null
    private var reconnectJob: Job? = null
    private var discoverySettleJob: Job? = null
    private var confirmationJob: Job? = null
    private var keyboardLoadJob: Job? = null
    private var keyboardRevision = 0L
    private val keyboardUpdates = Channel<KeyboardUpdate>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            discovery.devices.collect { discovered ->
                discovered.filter { it.online }.forEach { repository.upsert(it) }
                if (discovered.any { it.online }) {
                    discoverySettleJob?.cancel()
                    _uiState.update { it.copy(discoveryRefreshing = false, discoveryFailure = null) }
                }
                val active = activeDeviceId
                val selectedClient = client
                val activeSaved = repository.settings.first().devices
                    .firstOrNull { it.id == active }
                val activeIsOnline = activeSaved
                    ?.let { DeviceIdentityMatcher.bestDiscovery(it, discovered)?.online }
                    ?: discovered.any { it.id == active && it.online }
                val state = selectedClient?.connectionState?.value
                if (activeCanReconnect &&
                    activeIsOnline &&
                    selectedClient != null &&
                    reconnectJob?.isActive != true &&
                    (state == io.coreback.relayremote.protocol.ConnectionState.Offline ||
                        state is io.coreback.relayremote.protocol.ConnectionState.Error)
                ) {
                    reconnectJob = viewModelScope.launch {
                        _uiState.update { it.copy(connectionRetrying = true) }
                        runCatching { selectedClient.connect() }.onFailure(::showError)
                        _uiState.update { it.copy(connectionRetrying = false) }
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(discovery.devices, repository.settings) { discovered, settings ->
                discovered to settings
            }.collect { (discovered, settings) ->
                val items = settings.devices.map { saved ->
                    val live = DeviceIdentityMatcher.bestDiscovery(saved, discovered)
                    DeviceItem(
                        id = saved.id,
                        name = saved.displayName,
                        model = live?.model ?: saved.model,
                        online = live?.online == true,
                        paired = saved.paired,
                        selected = saved.id == settings.selectedDeviceId,
                    )
                }
                val selected = items.firstOrNull { it.selected }
                _uiState.update {
                    it.copy(
                        devices = items,
                        selectedDeviceId = selected?.id,
                        selectedDeviceName = selected?.name,
                        favoriteBundleIds = settings.devices
                            .firstOrNull { stored -> stored.id == settings.selectedDeviceId }
                            ?.favoriteBundleIds
                            .orEmpty(),
                        keepScreenAwake = settings.keepScreenAwake,
                        skipSeconds = settings.skipSeconds,
                    )
                }
                if (settings.selectedDeviceId != activeDeviceId) {
                    activate(settings.selectedDeviceId, selected?.paired == true)
                }
            }
        }
        viewModelScope.launch {
            discovery.failure.collect { failure ->
                _uiState.update { it.copy(discoveryFailure = failure, discoveryRefreshing = false) }
            }
        }
        viewModelScope.launch {
            for (update in keyboardUpdates) {
                if (update.deviceId != activeDeviceId || update.revision != keyboardRevision) continue
                runCatching {
                    requireClient().setKeyboardText(KeyboardOperation.Replace(update.text))
                }.onSuccess {
                    if (update.deviceId == activeDeviceId && update.revision == keyboardRevision &&
                        _uiState.value.sheet == RemoteSheet.KEYBOARD
                    ) {
                        _uiState.update {
                            it.copy(
                                keyboardSyncState = KeyboardSyncState.SYNCED,
                                keyboardDirty = false,
                                keyboardError = null,
                            )
                        }
                    }
                }.onFailure { error ->
                    if (update.deviceId == activeDeviceId && update.revision == keyboardRevision &&
                        _uiState.value.sheet == RemoteSheet.KEYBOARD
                    ) {
                        val available = _uiState.value.controlEnabled(Capability.KEYBOARD)
                        _uiState.update {
                            it.copy(
                                keyboardSyncState = if (available) {
                                    KeyboardSyncState.ERROR
                                } else {
                                    KeyboardSyncState.UNAVAILABLE
                                },
                                keyboardDirty = true,
                                keyboardError = keyboardErrorMessage(error),
                            )
                        }
                    }
                }
            }
        }
        settleDiscoveryIndicator()
    }

    fun openSheet(sheet: RemoteSheet) {
        _uiState.update { it.copy(sheet = sheet) }
        if (sheet == RemoteSheet.APPS) loadApps()
        if (sheet == RemoteSheet.KEYBOARD) {
            _uiState.update {
                it.copy(
                    keyboardText = "",
                    keyboardSyncState = KeyboardSyncState.LOADING,
                    keyboardSnapshotLoaded = false,
                    keyboardDirty = false,
                    keyboardError = null,
                )
            }
            refreshKeyboardText()
        }
    }

    fun closeSheet() {
        if (_uiState.value.pairingInProgress) return
        val currentSheet = _uiState.value.sheet
        if (currentSheet == RemoteSheet.KEYBOARD && _uiState.value.keyboardDirty) return
        val cancelPairing = currentSheet == RemoteSheet.PAIRING
        if (currentSheet == RemoteSheet.KEYBOARD) keyboardLoadJob?.cancel()
        _uiState.update {
            if (currentSheet == RemoteSheet.KEYBOARD) {
                it.copy(
                    sheet = RemoteSheet.NONE,
                    pin = "",
                    keyboardText = "",
                    keyboardSyncState = KeyboardSyncState.IDLE,
                    keyboardSnapshotLoaded = false,
                    keyboardDirty = false,
                    keyboardError = null,
                )
            } else {
                it.copy(sheet = RemoteSheet.NONE, pin = "")
            }
        }
        if (cancelPairing) {
            viewModelScope.launch {
                runCatching { client?.disconnect() }.onFailure(::showError)
            }
        }
    }

    fun closeKeyboardAnyway() {
        if (_uiState.value.sheet != RemoteSheet.KEYBOARD) return
        keyboardLoadJob?.cancel()
        ++keyboardRevision
        _uiState.update {
            it.copy(
                sheet = RemoteSheet.NONE,
                keyboardText = "",
                keyboardSyncState = KeyboardSyncState.IDLE,
                keyboardSnapshotLoaded = false,
                keyboardDirty = false,
                keyboardError = null,
            )
        }
    }

    fun selectDevice(deviceId: String) {
        viewModelScope.launch {
            runCatching { repository.select(deviceId) }.onFailure(::showError)
        }
    }

    fun startPairing(deviceId: String) {
        viewModelScope.launch {
            runCatching {
                repository.select(deviceId)
                if (activeDeviceId != deviceId) activate(deviceId, paired = false)
                requireClient().beginPairing()
                _uiState.update {
                    it.copy(
                        sheet = RemoteSheet.PAIRING,
                        pin = "",
                        confirmation = null,
                        pairingInProgress = false,
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun updatePin(value: String) {
        _uiState.update { it.copy(pin = value.filter(Char::isDigit).take(4)) }
    }

    fun updateKeyboardText(value: String) {
        val deviceId = activeDeviceId ?: return
        if (!_uiState.value.keyboardSnapshotLoaded) return
        val revision = ++keyboardRevision
        _uiState.update {
            it.copy(
                keyboardText = value,
                keyboardSyncState = KeyboardSyncState.SYNCING,
                keyboardDirty = true,
                keyboardError = null,
            )
        }
        keyboardUpdates.trySend(KeyboardUpdate(deviceId, revision, value))
    }

    fun keyboard(operation: KeyboardOperation) {
        when (operation) {
            is KeyboardOperation.Replace -> updateKeyboardText(operation.text)
            is KeyboardOperation.Append -> updateKeyboardText(_uiState.value.keyboardText + operation.text)
            is KeyboardOperation.Paste -> updateKeyboardText(_uiState.value.keyboardText + operation.text)
            KeyboardOperation.Clear -> updateKeyboardText("")
        }
    }

    fun refreshKeyboardText() {
        val deviceId = activeDeviceId ?: return
        val current = _uiState.value
        if (current.keyboardDirty || keyboardLoadJob?.isActive == true) return
        val revision = ++keyboardRevision
        _uiState.update {
            it.copy(
                keyboardSyncState = KeyboardSyncState.LOADING,
                keyboardError = null,
            )
        }
        keyboardLoadJob = viewModelScope.launch {
            runCatching { requireClient().getKeyboardText() }
                .onSuccess { remoteText ->
                    if (deviceId != activeDeviceId || revision != keyboardRevision ||
                        _uiState.value.sheet != RemoteSheet.KEYBOARD
                    ) return@onSuccess
                    if (remoteText == null) {
                        _uiState.update {
                            it.copy(
                                keyboardSyncState = KeyboardSyncState.UNAVAILABLE,
                                keyboardSnapshotLoaded = false,
                                keyboardError = "Apple TV is not exposing an editable text field.",
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                keyboardText = remoteText,
                                keyboardSyncState = KeyboardSyncState.SYNCED,
                                keyboardSnapshotLoaded = true,
                                keyboardDirty = false,
                                keyboardError = null,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (deviceId == activeDeviceId && revision == keyboardRevision &&
                        _uiState.value.sheet == RemoteSheet.KEYBOARD
                    ) {
                        val available = _uiState.value.controlEnabled(Capability.KEYBOARD)
                        _uiState.update {
                            it.copy(
                                keyboardSyncState = if (available) {
                                    KeyboardSyncState.ERROR
                                } else {
                                    KeyboardSyncState.UNAVAILABLE
                                },
                                keyboardError = keyboardErrorMessage(error),
                            )
                        }
                    }
                }
        }
    }

    fun retryKeyboardSync() {
        val current = _uiState.value
        if (!current.keyboardSnapshotLoaded) {
            refreshKeyboardText()
            return
        }
        val deviceId = activeDeviceId ?: return
        val revision = ++keyboardRevision
        _uiState.update {
            it.copy(
                keyboardSyncState = KeyboardSyncState.SYNCING,
                keyboardDirty = true,
                keyboardError = null,
            )
        }
        keyboardUpdates.trySend(KeyboardUpdate(deviceId, revision, current.keyboardText))
    }

    fun onAppResumed() {
        val current = _uiState.value
        if (current.sheet == RemoteSheet.KEYBOARD &&
            current.keyboardSnapshotLoaded &&
            !current.keyboardDirty
        ) {
            refreshKeyboardText()
        }
    }

    fun startFreshKeyboardText() {
        if (_uiState.value.keyboardSnapshotLoaded) return
        val deviceId = activeDeviceId ?: return
        val revision = ++keyboardRevision
        _uiState.update {
            it.copy(
                keyboardText = "",
                keyboardSyncState = KeyboardSyncState.SYNCING,
                keyboardSnapshotLoaded = true,
                keyboardDirty = true,
                keyboardError = null,
            )
        }
        keyboardUpdates.trySend(KeyboardUpdate(deviceId, revision, ""))
    }

    fun submitPin() {
        val pin = _uiState.value.pin
        if (pin.length != 4 || _uiState.value.pairingInProgress) return
        _uiState.update { it.copy(pairingInProgress = true, error = null) }
        viewModelScope.launch {
            runCatching {
                checkNotNull(client) { "No Apple TV is selected" }.pair(pin)
                activeCanReconnect = true
                showConfirmation("Paired and saved")
                _uiState.update { it.copy(sheet = RemoteSheet.NONE, pin = "") }
            }.onFailure(::showError)
            _uiState.update { it.copy(pairingInProgress = false) }
        }
    }

    fun refreshDiscovery() {
        if (_uiState.value.discoveryRefreshing) return
        _uiState.update { it.copy(discoveryRefreshing = true, discoveryFailure = null) }
        runCatching { discovery.refresh() }.onFailure(::showError)
        settleDiscoveryIndicator()
    }

    fun retryConnection() {
        val selectedClient = client
        if (selectedClient == null) {
            showError(IllegalStateException("Choose an Apple TV before connecting."))
            return
        }
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            _uiState.update { it.copy(connectionRetrying = true, error = null) }
            runCatching { selectedClient.connect() }.onFailure(::showError)
            _uiState.update { it.copy(connectionRetrying = false) }
        }
    }

    fun send(command: AppleTvCommand) {
        viewModelScope.launch {
            runCatching { requireClient().sendCommand(command) }.onFailure(::showError)
        }
    }

    fun skipBy(seconds: Int) = action { skipBy(seconds) }

    fun touch(gesture: TouchGesture) {
        viewModelScope.launch {
            runCatching { requireClient().sendTouchGesture(gesture) }.onFailure(::showError)
        }
    }

    fun wake() = action { wake() }

    fun sleep() = action { sleep() }

    fun volumeUp() = action { volumeUp() }

    fun volumeDown() = action { volumeDown() }

    fun mute() = action { mute() }

    fun launchApp(bundleId: String) {
        viewModelScope.launch {
            runCatching {
                requireClient().launchApp(bundleId)
                closeSheet()
            }.onFailure(::showError)
        }
    }

    fun setFavorite(bundleId: String, favorite: Boolean) {
        val deviceId = _uiState.value.selectedDeviceId ?: return
        viewModelScope.launch {
            runCatching { repository.setFavorite(deviceId, bundleId, favorite) }
                .onFailure(::showError)
        }
    }

    fun rename(deviceId: String, alias: String?) {
        viewModelScope.launch {
            runCatching { repository.rename(deviceId, alias) }.onFailure(::showError)
        }
    }

    fun forget(deviceId: String) {
        viewModelScope.launch {
            runCatching {
                if (deviceId == activeDeviceId) client?.disconnect()
                repository.forget(deviceId)
            }.onFailure(::showError)
        }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setKeepScreenAwake(enabled) }.onFailure(::showError)
        }
    }

    fun setSkipSeconds(seconds: Int) {
        viewModelScope.launch {
            runCatching { repository.setSkipSeconds(seconds) }.onFailure(::showError)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearConfirmation() {
        confirmationJob?.cancel()
        _uiState.update { it.copy(confirmation = null) }
    }

    fun hardwareVolume(up: Boolean): Boolean {
        if (!_uiState.value.controlEnabled(Capability.VOLUME)) return false
        if (up) volumeUp() else volumeDown()
        return true
    }

    private fun activate(deviceId: String?, paired: Boolean) {
        if (deviceId != activeDeviceId && _uiState.value.sheet == RemoteSheet.KEYBOARD) {
            keyboardLoadJob?.cancel()
            ++keyboardRevision
            _uiState.update {
                it.copy(
                    sheet = RemoteSheet.NONE,
                    keyboardText = "",
                    keyboardSyncState = KeyboardSyncState.IDLE,
                    keyboardSnapshotLoaded = false,
                    keyboardDirty = false,
                    keyboardError = null,
                )
            }
        }
        activeDeviceId = deviceId
        activeCanReconnect = paired
        clientStateJob?.cancel()
        reconnectJob?.cancel()
        reconnectJob = null
        clientStateJob = null
        val previous = client
        client = null
        if (previous != null) viewModelScope.launch { previous.disconnect() }
        if (deviceId == null) {
            _uiState.update {
                it.copy(
                    connectionState = io.coreback.relayremote.protocol.ConnectionState.Scanning,
                    capabilities = io.coreback.relayremote.protocol.CapabilitySet.None,
                )
            }
            return
        }
        val selectedClient = clientFactory(deviceId)
        client = selectedClient
        clientStateJob = viewModelScope.launch {
            combine(
                selectedClient.connectionState,
                selectedClient.capabilities,
                selectedClient.powerState,
                selectedClient.nowPlaying,
            ) { connection, capabilities, power, nowPlaying ->
                arrayOf(connection, capabilities, power, nowPlaying)
            }.collect { values ->
                val connection = values[0] as io.coreback.relayremote.protocol.ConnectionState
                val capabilities = values[1] as io.coreback.relayremote.protocol.CapabilitySet
                _uiState.update {
                    val keyboardBecameUnavailable = it.sheet == RemoteSheet.KEYBOARD &&
                        it.keyboardSyncState != KeyboardSyncState.LOADING &&
                        it.keyboardSyncState != KeyboardSyncState.SYNCING &&
                        (connection != io.coreback.relayremote.protocol.ConnectionState.Connected ||
                            !capabilities.supports(Capability.KEYBOARD))
                    it.copy(
                        connectionState = connection,
                        capabilities = capabilities,
                        powerState = values[2] as io.coreback.relayremote.protocol.PowerState,
                        nowPlaying = values[3] as io.coreback.relayremote.protocol.NowPlaying?,
                        keyboardSyncState = if (keyboardBecameUnavailable) {
                            KeyboardSyncState.UNAVAILABLE
                        } else {
                            it.keyboardSyncState
                        },
                        keyboardError = if (keyboardBecameUnavailable) {
                            if (connection == io.coreback.relayremote.protocol.ConnectionState.Connected) {
                                "Apple TV left the text field. Return to it to continue."
                            } else {
                                "Apple TV disconnected. Your text remains on this phone."
                            }
                        } else {
                            it.keyboardError
                        },
                    )
                }
                val current = _uiState.value
                if (current.sheet == RemoteSheet.KEYBOARD &&
                    !current.keyboardDirty &&
                    connection == io.coreback.relayremote.protocol.ConnectionState.Connected &&
                    capabilities.supports(Capability.KEYBOARD) &&
                    (current.keyboardSyncState == KeyboardSyncState.UNAVAILABLE ||
                        current.keyboardSyncState == KeyboardSyncState.ERROR)
                ) {
                    refreshKeyboardText()
                }
            }
        }
        if (paired) {
            viewModelScope.launch {
                runCatching { selectedClient.connect() }.onFailure(::showError)
            }
        }
    }

    private fun loadApps() {
        if (!_uiState.value.controlEnabled(Capability.APPS)) return
        viewModelScope.launch {
            _uiState.update { it.copy(appsLoading = true) }
            runCatching { requireClient().listApps() }
                .onSuccess { apps -> _uiState.update { it.copy(apps = apps) } }
                .onFailure(::showError)
            _uiState.update { it.copy(appsLoading = false) }
        }
    }

    private fun action(block: suspend AppleTvClient.() -> Unit) {
        viewModelScope.launch {
            runCatching { requireClient().block() }.onFailure(::showError)
        }
    }

    private fun requireClient(): AppleTvClient =
        checkNotNull(client) { "Choose an Apple TV before using the remote." }

    private fun settleDiscoveryIndicator() {
        discoverySettleJob?.cancel()
        discoverySettleJob = viewModelScope.launch {
            delay(4_000)
            _uiState.update { it.copy(discoveryRefreshing = false) }
        }
    }

    private fun showConfirmation(message: String) {
        confirmationJob?.cancel()
        _uiState.update { it.copy(confirmation = message) }
        confirmationJob = viewModelScope.launch {
            delay(3_000)
            _uiState.update { current ->
                if (current.confirmation == message) current.copy(confirmation = null) else current
            }
        }
    }

    private fun showError(error: Throwable) {
        _uiState.update {
            it.copy(error = error.message ?: "The Apple TV did not respond.")
        }
    }

    private fun keyboardErrorMessage(@Suppress("UNUSED_PARAMETER") error: Throwable): String =
        "Could not synchronize with Apple TV. Your text remains on this phone."

    override fun onCleared() {
        client?.let { active -> viewModelScope.launch { active.disconnect() } }
        super.onCleared()
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RemoteViewModel(
                discovery = container.discovery,
                repository = container.deviceRepository,
                clientFactory = container::createClient,
            ) as T
    }

    private data class KeyboardUpdate(
        val deviceId: String,
        val revision: Long,
        val text: String,
    )
}
