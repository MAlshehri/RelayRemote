package io.coreback.relayremote.protocol.companion

import io.coreback.relayremote.protocol.AppleTvAccount
import io.coreback.relayremote.protocol.AppleTvApp
import io.coreback.relayremote.protocol.AppleTvClient
import io.coreback.relayremote.protocol.AppleTvCommand
import io.coreback.relayremote.protocol.Capability
import io.coreback.relayremote.protocol.CapabilitySet
import io.coreback.relayremote.protocol.ConnectionState
import io.coreback.relayremote.protocol.KeyboardOperation
import io.coreback.relayremote.protocol.NowPlaying
import io.coreback.relayremote.protocol.PairingCredentials
import io.coreback.relayremote.protocol.PowerState
import io.coreback.relayremote.protocol.ScanDirection
import io.coreback.relayremote.protocol.TouchGesture
import io.coreback.relayremote.protocol.UnsupportedCapabilityException
import io.coreback.relayremote.protocol.discovery.ResolvedEndpoint
import io.coreback.relayremote.protocol.pairing.CompanionAuthExchange
import io.coreback.relayremote.protocol.pairing.PairVerifySession
import io.coreback.relayremote.protocol.pairing.PairingAuthenticationException
import io.coreback.relayremote.protocol.pairing.PairingSession
import io.coreback.relayremote.protocol.session.CommandResult
import io.coreback.relayremote.protocol.session.CompanionFrameTransport
import io.coreback.relayremote.protocol.session.CompanionProtocol
import io.coreback.relayremote.protocol.session.CompanionTransport
import io.coreback.relayremote.protocol.session.OrderedCommandQueue
import io.coreback.relayremote.protocol.session.ReconnectRunner
import io.coreback.relayremote.protocol.session.RetryPolicy
import java.io.IOException
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompanionAppleTvClient(
    private val deviceIdentifier: String,
    private val endpointResolver: suspend () -> ResolvedEndpoint?,
    private val credentialsProvider: suspend () -> PairingCredentials?,
    private val credentialsSink: suspend (PairingCredentials) -> Unit,
    private val scope: CoroutineScope,
    private val transportFactory: () -> CompanionFrameTransport = { CompanionTransport() },
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val pairingTrace: (String) -> Unit = {},
) : AppleTvClient {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Offline)
    private val _capabilities = MutableStateFlow(CapabilitySet.None)
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    private val _powerState = MutableStateFlow(PowerState.UNKNOWN)
    private val secureRandom = SecureRandom()
    private val commandQueue = OrderedCommandQueue<suspend () -> Unit>(scope, 64) { it() }
    private val reconnectRunner = ReconnectRunner(retryPolicy)
    private val input = CompanionInput(
        channel = object : CompanionCommandChannel {
            override suspend fun exchange(identifier: String, content: Map<String, Any?>) =
                requireProtocol().exchange(identifier, content)

            override suspend fun sendEvent(identifier: String, content: Map<String, Any?>) =
                requireProtocol().sendEvent(identifier, content)
        },
    )
    private val moveCoalescer = TouchMoveCoalescer()

    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override val capabilities: StateFlow<CapabilitySet> = _capabilities.asStateFlow()
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()
    override val powerState: StateFlow<PowerState> = _powerState.asStateFlow()
    override val muted: StateFlow<Boolean> = input.muted

    @Volatile private var transport: CompanionFrameTransport? = null
    @Volatile private var protocol: CompanionProtocol? = null
    private var pendingPairing: PairingSession? = null
    private var pairingProtocol: CompanionProtocol? = null
    private var reconnectJob: Job? = null

    override suspend fun connect() {
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            connectAuthenticated()
        } catch (error: PairingRequiredException) {
            _connectionState.value = ConnectionState.AuthenticationFailed
        } catch (error: Throwable) {
            val failure = companionConnectionFailure(error)
            _connectionState.value = failure
            if (failure is ConnectionState.Error) throw error
        }
    }

    override suspend fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        commandQueue.invalidate()
        resetInputState()
        protocol?.close()
        protocol = null
        transport = null
        pairingProtocol?.close()
        pairingProtocol = null
        pendingPairing = null
        _capabilities.value = CapabilitySet.None
        _powerState.value = PowerState.UNKNOWN
        _connectionState.value = ConnectionState.Offline
    }

    override suspend fun beginPairing() {
        disconnect()
        _connectionState.value = ConnectionState.Pairing
        val endpoint = endpointResolver()
            ?: throw IllegalStateException("Apple TV is no longer discoverable")
        val pairTransport = connectTransport(endpoint)
        val pairProtocol = CompanionProtocol(
            transport = pairTransport,
            scope = scope,
        )
        pairProtocol.start()
        val session = PairingSession(
            CompanionAuthExchange(pairProtocol::exchangeAuth),
            "Relay Remote",
            trace = pairingTrace,
        )
        try {
            session.begin()
        } catch (error: Throwable) {
            pairProtocol.close()
            _connectionState.value = ConnectionState.Error(
                "Apple TV did not start pairing.",
                retryable = true,
            )
            throw error
        }
        transport = pairTransport
        pairingProtocol = pairProtocol
        pendingPairing = session
    }

    override suspend fun pair(pin: String): PairingCredentials {
        val session = pendingPairing ?: throw IllegalStateException("Pairing has not started")
        val credentials = try {
            session.finish(pin)
        } catch (error: Throwable) {
            pairingTrace(
                "Pairing failed: ${error::class.java.simpleName}: " +
                    "${error.message ?: "no message"}",
            )
            _connectionState.value = ConnectionState.Error(
                "Pairing failed. Check the PIN and try again.",
                retryable = true,
            )
            throw error
        }
        return try {
            completePairingDurably(
                credentials = credentials,
                persist = credentialsSink,
                onPersisted = {
                    pairingProtocol?.close()
                    pairingProtocol = null
                    pendingPairing = null
                    transport = null
                },
                connect = ::connectAuthenticated,
                onConnectionFailure = { failure ->
                    _connectionState.value = failure
                    if (failure != ConnectionState.AuthenticationFailed) scheduleReconnect()
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _connectionState.value = ConnectionState.Error(
                "Pairing succeeded, but credentials could not be saved. Try again.",
                retryable = true,
            )
            throw error
        }
    }

    override suspend fun sendCommand(command: AppleTvCommand) {
        val capability = command.capability()
        runQueued(capability) {
            when (command) {
                AppleTvCommand.UP -> press(HidButton.UP)
                AppleTvCommand.DOWN -> press(HidButton.DOWN)
                AppleTvCommand.LEFT -> press(HidButton.LEFT)
                AppleTvCommand.RIGHT -> press(HidButton.RIGHT)
                AppleTvCommand.SELECT -> press(HidButton.SELECT)
                AppleTvCommand.SELECT_LONG_PRESS -> input.pressAndHold(HidButton.SELECT)
                AppleTvCommand.BACK -> press(HidButton.MENU)
                AppleTvCommand.BACK_LONG_PRESS -> input.pressAndHold(HidButton.MENU)
                AppleTvCommand.HOME -> press(HidButton.HOME)
                AppleTvCommand.HOME_LONG_PRESS -> press(HidButton.CONTROL_CENTER)
                AppleTvCommand.HOME_DOUBLE_PRESS -> {
                    press(HidButton.HOME)
                    press(HidButton.HOME)
                }
                AppleTvCommand.PLAY_PAUSE -> press(HidButton.PLAY_PAUSE)
                AppleTvCommand.PREVIOUS -> mediaControl(4)
                AppleTvCommand.NEXT -> mediaControl(3)
                AppleTvCommand.GUIDE -> press(HidButton.GUIDE)
                AppleTvCommand.CHANNEL_UP -> press(HidButton.CHANNEL_UP)
                AppleTvCommand.CHANNEL_DOWN -> press(HidButton.CHANNEL_DOWN)
                AppleTvCommand.SCREENSAVER -> press(HidButton.SCREENSAVER)
            }
        }
    }

    override suspend fun skipBy(seconds: Int) {
        val command = CompanionCommandEncoder.skipBy(seconds)
        runQueued(command.capability) {
            requireProtocol().exchange("_mcc", command.payload)
        }
    }

    override suspend fun sendTouchGesture(gesture: TouchGesture) {
        when (gesture) {
            is TouchGesture.Began -> {
                moveCoalescer.startSwipe()
                runQueued(Capability.TOUCH) {
                    input.touch(gesture.x, gesture.y, CompanionInput.TOUCH_PRESS)
                }
            }
            is TouchGesture.Moved -> sendCoalescedMove(gesture)
            is TouchGesture.Ended -> runQueuedReliably(Capability.TOUCH) {
                input.touch(gesture.x, gesture.y, CompanionInput.TOUCH_RELEASE)
            }
            TouchGesture.HoldStarted -> runQueued(Capability.SELECT) { input.beginSelectHold() }
            TouchGesture.HoldEnded -> runQueuedReliably(Capability.SELECT) { input.endSelectHold() }
        }
    }

    private suspend fun sendCoalescedMove(gesture: TouchGesture.Moved) {
        val pending = moveCoalescer.offer(gesture) ?: return
        try {
            runQueued(Capability.TOUCH) {
                pending.take()?.let { latest ->
                    input.touch(latest.x, latest.y, CompanionInput.TOUCH_HOLD)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // A lost position is harmless, and the touch's end reports any real failure.
            pending.take()
        }
    }

    override suspend fun setScan(direction: ScanDirection, active: Boolean) {
        val capability = when (direction) {
            ScanDirection.FAST_FORWARD -> Capability.FAST_FORWARD
            ScanDirection.REWIND -> Capability.REWIND
        }
        val action: suspend () -> Unit = {
            requireProtocol().exchange("_mcc", CompanionCommandEncoder.scan(direction, active))
        }
        when {
            active -> runQueued(capability, action)
            // An app can withdraw scanning mid-hold, and a running scan must still be stopped.
            // Without a connection there is nothing to stop.
            protocol != null -> runQueuedReliably(capability = null, action)
        }
    }

    override suspend fun listApps(): List<AppleTvApp> {
        var result = emptyList<AppleTvApp>()
        runQueued(Capability.APPS) {
            result = requireProtocol()
                .exchange("FetchLaunchableApplicationsEvent", emptyMap())
                .mapNotNull { (bundleId, value) ->
                    (value as? String)?.let { AppleTvApp(bundleId, it) }
                }
                .sortedBy { it.name.lowercase() }
        }
        return result
    }

    override suspend fun launchApp(bundleId: String) {
        require(bundleId.isNotBlank())
        runQueued(Capability.APPS) {
            requireProtocol().exchange("_launchApp", mapOf("_bundleID" to bundleId))
        }
    }

    override suspend fun listAccounts(): List<AppleTvAccount> {
        var result = emptyList<AppleTvAccount>()
        runQueued(Capability.ACCOUNTS) {
            result = requireProtocol()
                .exchange("FetchUserAccountsEvent", emptyMap())
                .mapNotNull { (id, value) ->
                    (value as? String)?.let { AppleTvAccount(id, it) }
                }
                .sortedBy { it.name.lowercase() }
        }
        return result
    }

    override suspend fun switchAccount(accountId: String) {
        require(accountId.isNotBlank())
        runQueued(Capability.ACCOUNTS) {
            requireProtocol().exchange(
                "SwitchUserAccountEvent",
                CompanionCommandEncoder.switchAccount(accountId),
            )
        }
    }

    override suspend fun getKeyboardText(): String? {
        var text: String? = null
        runQueued(Capability.KEYBOARD) {
            text = restartKeyboardSession(requireProtocol()).currentText
        }
        return text
    }

    override suspend fun setKeyboardText(operation: KeyboardOperation) {
        runQueued(Capability.KEYBOARD) {
            val activeProtocol = requireProtocol()
            val session = restartKeyboardSession(activeProtocol)
            val clear = operation == KeyboardOperation.Clear ||
                operation is KeyboardOperation.Replace
            if (clear) {
                activeProtocol.sendEvent(
                    "_tiC",
                    mapOf(
                        "_tiV" to 1L,
                        "_tiD" to RtiKeyboardArchive.clearText(session.uuid),
                    ),
                )
            }
            val text = when (operation) {
                is KeyboardOperation.Replace -> operation.text
                is KeyboardOperation.Append -> operation.text
                is KeyboardOperation.Paste -> operation.text
                KeyboardOperation.Clear -> ""
            }
            if (text.isNotEmpty()) {
                activeProtocol.sendEvent(
                    "_tiC",
                    mapOf(
                        "_tiV" to 1L,
                        "_tiD" to RtiKeyboardArchive.inputText(session.uuid, text),
                    ),
                )
            }
        }
    }

    private suspend fun restartKeyboardSession(
        activeProtocol: CompanionProtocol,
    ): RtiKeyboardSession {
        activeProtocol.exchange("_tiStop", emptyMap())
        val sessionData = activeProtocol.exchange("_tiStart", emptyMap())["_tiD"] as? ByteArray
        return sessionData?.let(RtiKeyboardArchive::readSession) ?: run {
            setCapabilityAvailable(Capability.KEYBOARD, false)
            throw UnsupportedCapabilityException(Capability.KEYBOARD)
        }
    }

    override suspend fun wake() {
        runQueued(Capability.WAKE) {
            _connectionState.value = ConnectionState.Waking
            press(HidButton.WAKE)
            _connectionState.value = ConnectionState.Connected
        }
    }

    override suspend fun sleep() {
        runQueued(Capability.SLEEP) { press(HidButton.SLEEP) }
    }

    override suspend fun volumeUp() {
        runQueued(Capability.VOLUME) {
            press(HidButton.VOLUME_UP)
            input.volumeChangedByUser()
        }
    }

    override suspend fun volumeDown() {
        runQueued(Capability.VOLUME) {
            press(HidButton.VOLUME_DOWN)
            input.volumeChangedByUser()
        }
    }

    override suspend fun mute() {
        runQueued(Capability.MUTE) { input.toggleMute() }
    }

    private suspend fun connectAuthenticated() {
        protocol?.close()
        protocol = null
        transport = null
        commandQueue.invalidate()
        resetInputState()
        _connectionState.value = ConnectionState.Connecting
        val credentials = credentialsProvider() ?: throw PairingRequiredException()
        val endpoint = endpointResolver()
            ?: throw AppleTvOfflineException()
        val activeTransport = connectTransport(endpoint)
        val activeProtocol = CompanionProtocol(
            transport = activeTransport,
            scope = scope,
            eventListener = ::handleEvent,
        )
        activeProtocol.start()
        try {
            val keys = PairVerifySession(
                CompanionAuthExchange(activeProtocol::exchangeAuth),
            ).verify(credentials)
            activeTransport.enableEncryption(keys.outputKey, keys.inputKey)
            keys.outputKey.fill(0)
            keys.inputKey.fill(0)
            protocol = activeProtocol
            transport = activeTransport
            initializeSession(activeProtocol, credentials)
            _capabilities.value = CapabilitySet(
                implemented = IMPLEMENTED_CAPABILITIES,
                available = BASE_CAPABILITIES,
            )
            _connectionState.value = ConnectionState.Connected
        } catch (error: Throwable) {
            activeProtocol.close()
            protocol = null
            transport = null
            throw error
        }
    }

    private suspend fun connectTransport(endpoint: ResolvedEndpoint): CompanionFrameTransport {
        var lastError: Throwable? = null
        endpoint.addresses.forEach { address ->
            val candidate = transportFactory()
            try {
                candidate.connect(address, endpoint.port)
                return candidate
            } catch (error: Throwable) {
                candidate.close()
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Apple TV has no resolved network address")
    }

    private suspend fun initializeSession(
        activeProtocol: CompanionProtocol,
        credentials: PairingCredentials,
    ) {
        activeProtocol.exchange(
            "_systemInfo",
            mapOf(
                "_bf" to 0L,
                "_cf" to 512L,
                "_clFl" to 128L,
                "_i" to deviceIdentifier,
                "_idsID" to credentials.clientIdentifier,
                "_pubID" to deviceIdentifier,
                "_sf" to 256L,
                "_sv" to "18.0",
                "model" to "Android",
                "name" to "Relay Remote",
            ),
        )
        input.startTouchClock()
        activeProtocol.exchange(
            "_touchStart",
            mapOf("_height" to 1000.0, "_tFl" to 0L, "_width" to 1000.0),
        )
        val localSessionId = secureRandom.nextInt().toLong() and 0xFFFF_FFFFL
        activeProtocol.exchange(
            "_sessionStart",
            mapOf("_srvT" to "com.apple.tvremoteservices", "_sid" to localSessionId),
        )
        try {
            activeProtocol.exchange("TVRCSessionStart", mapOf("ProtocolVersionKey" to "1.2"))
        } catch (_: Throwable) {
            // Older devices can still use the core Companion session.
        }
        activeProtocol.sendEvent("_interest", mapOf("_regEvents" to listOf("_iMC")))
        activeProtocol.sendEvent(
            "_interest",
            mapOf("_regEvents" to listOf("SystemStatus", "TVSystemStatus")),
        )
        try {
            val attention = activeProtocol.exchange("FetchAttentionState", emptyMap())
            updatePower(attention["state"])
        } catch (_: Throwable) {
            _powerState.value = PowerState.UNKNOWN
        }
        try {
            val textInput = activeProtocol.exchange("_tiStart", emptyMap())
            setCapabilityAvailable(
                Capability.KEYBOARD,
                (textInput["_tiD"] as? ByteArray)?.let(RtiKeyboardArchive::readSession) != null,
            )
        } catch (_: Throwable) {
            setCapabilityAvailable(Capability.KEYBOARD, false)
        }
    }

    private fun handleEvent(name: String, content: Map<String, Any?>) {
        when (name) {
            "_iMC" -> {
                val flags = (content["_mcF"] as? Number)?.toInt() ?: return
                _capabilities.value = CapabilitySet(
                    implemented = IMPLEMENTED_CAPABILITIES,
                    available = BASE_CAPABILITIES +
                        CompanionCommandEncoder.mediaCapabilities(flags) +
                        _capabilities.value.available.intersect(DYNAMIC_CAPABILITIES),
                )
            }
            "SystemStatus", "TVSystemStatus" -> updatePower(content["state"])
            "_tiStarted", "_tiStart" ->
                setCapabilityAvailable(Capability.KEYBOARD, content["_tiD"] is ByteArray)
            "_tiStopped" -> setCapabilityAvailable(Capability.KEYBOARD, false)
        }
    }

    private fun setCapabilityAvailable(capability: Capability, available: Boolean) {
        val current = _capabilities.value
        _capabilities.value = current.copy(
            available = if (available) {
                current.available + capability
            } else {
                current.available - capability
            },
        )
    }

    private fun updatePower(value: Any?) {
        _powerState.value = when ((value as? Number)?.toInt()) {
            1 -> PowerState.OFF
            2, 3, 4 -> PowerState.ON
            else -> PowerState.UNKNOWN
        }
    }

    private suspend fun press(button: HidButton) = input.press(button)

    private fun resetInputState() {
        moveCoalescer.startSwipe()
        input.resetSession()
    }

    private suspend fun mediaControl(command: Long, args: Map<String, Any?> = emptyMap()) {
        requireProtocol().exchange("_mcc", mapOf("_mcc" to command) + args)
    }

    /** A null [capability] is only for releasing something that was already started. */
    private suspend fun runQueued(
        capability: Capability?,
        action: suspend () -> Unit,
    ) {
        if (capability != null && !_capabilities.value.supports(capability)) {
            throw UnsupportedCapabilityException(capability)
        }
        when (val result = commandQueue.enqueue(action).await()) {
            CommandResult.Executed -> Unit
            CommandResult.DroppedStale -> throw IllegalStateException("That command was not sent because the connection dropped.")
            CommandResult.RejectedFull -> throw CommandQueueFullException()
            is CommandResult.Failed -> {
                scheduleReconnect()
                throw result.error
            }
        }
    }

    /** For releases: a press left down must not be lost because the queue is briefly full. */
    private suspend fun runQueuedReliably(
        capability: Capability?,
        action: suspend () -> Unit,
    ) {
        repeat(RELEASE_RETRIES) {
            try {
                return runQueued(capability, action)
            } catch (_: CommandQueueFullException) {
                delay(RELEASE_RETRY_DELAY_MILLIS)
            }
        }
        runQueued(capability, action)
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        commandQueue.invalidate()
        reconnectJob = scope.launch {
            var terminalState: ConnectionState? = null
            val connected = reconnectRunner.run(
                onAttempt = { attempt ->
                    _connectionState.value = ConnectionState.Reconnecting(attempt)
                },
                shouldRetry = { error ->
                    val failure = if (error is PairingRequiredException) {
                        ConnectionState.AuthenticationFailed
                    } else {
                        companionConnectionFailure(error)
                    }
                    if (failure == ConnectionState.AuthenticationFailed) {
                        terminalState = failure
                        false
                    } else {
                        true
                    }
                },
                connect = ::connectAuthenticated,
            )
            if (!connected) {
                _connectionState.value = terminalState ?: ConnectionState.Offline
            }
        }
    }

    private fun requireProtocol(): CompanionProtocol =
        protocol ?: throw IllegalStateException("Apple TV is not connected")

    private fun AppleTvCommand.capability(): Capability = when (this) {
        AppleTvCommand.UP,
        AppleTvCommand.DOWN,
        AppleTvCommand.LEFT,
        AppleTvCommand.RIGHT,
        -> Capability.NAVIGATION
        AppleTvCommand.SELECT,
        AppleTvCommand.SELECT_LONG_PRESS,
        -> Capability.SELECT
        AppleTvCommand.BACK,
        AppleTvCommand.BACK_LONG_PRESS,
        -> Capability.BACK
        AppleTvCommand.HOME -> Capability.HOME
        AppleTvCommand.HOME_LONG_PRESS -> Capability.HOME_LONG_PRESS
        AppleTvCommand.HOME_DOUBLE_PRESS -> Capability.HOME_DOUBLE_PRESS
        AppleTvCommand.PLAY_PAUSE -> Capability.PLAY_PAUSE
        AppleTvCommand.PREVIOUS -> Capability.PREVIOUS
        AppleTvCommand.NEXT -> Capability.NEXT
        AppleTvCommand.GUIDE,
        AppleTvCommand.CHANNEL_UP,
        AppleTvCommand.CHANNEL_DOWN,
        -> Capability.CHANNELS
        AppleTvCommand.SCREENSAVER -> Capability.SCREENSAVER
    }

    private class PairingRequiredException : IllegalStateException()

    private class CommandQueueFullException :
        IllegalStateException("Too many queued remote commands")

    private companion object {
        const val RELEASE_RETRIES = 20
        const val RELEASE_RETRY_DELAY_MILLIS = 100L
        val IMPLEMENTED_CAPABILITIES = Capability.entries.toSet() - Capability.NOW_PLAYING
        val DYNAMIC_CAPABILITIES = setOf(Capability.KEYBOARD)
        val BASE_CAPABILITIES = setOf(
            Capability.NAVIGATION,
            Capability.SELECT,
            Capability.BACK,
            Capability.HOME,
            Capability.HOME_LONG_PRESS,
            Capability.HOME_DOUBLE_PRESS,
            Capability.PLAY_PAUSE,
            Capability.WAKE,
            Capability.SLEEP,
            Capability.TOUCH,
            Capability.APPS,
            Capability.CHANNELS,
            Capability.SCREENSAVER,
            Capability.ACCOUNTS,
        )
    }
}

internal fun companionConnectionFailure(error: Throwable): ConnectionState =
    when (error) {
        is PairingAuthenticationException -> ConnectionState.AuthenticationFailed
        is IOException,
        is AppleTvOfflineException,
        -> ConnectionState.Offline
        else -> ConnectionState.Error(
            message = error.message ?: "Could not connect to Apple TV.",
            retryable = true,
        )
    }

internal class AppleTvOfflineException : IllegalStateException("Apple TV is offline")

internal suspend fun completePairingDurably(
    credentials: PairingCredentials,
    persist: suspend (PairingCredentials) -> Unit,
    onPersisted: () -> Unit,
    connect: suspend () -> Unit,
    onConnectionFailure: (ConnectionState) -> Unit,
): PairingCredentials {
    persist(credentials)
    onPersisted()
    try {
        connect()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        onConnectionFailure(companionConnectionFailure(error))
    }
    return credentials
}
