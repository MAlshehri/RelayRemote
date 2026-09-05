package io.coreback.relayremote.protocol.companion

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

    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override val capabilities: StateFlow<CapabilitySet> = _capabilities.asStateFlow()
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()
    override val powerState: StateFlow<PowerState> = _powerState.asStateFlow()

    @Volatile private var transport: CompanionFrameTransport? = null
    @Volatile private var protocol: CompanionProtocol? = null
    private var pendingPairing: PairingSession? = null
    private var pairingProtocol: CompanionProtocol? = null
    private var reconnectJob: Job? = null
    private var touchEpochNanos = 0L

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
                AppleTvCommand.BACK -> press(HidButton.MENU)
                AppleTvCommand.HOME -> press(HidButton.HOME)
                AppleTvCommand.HOME_LONG_PRESS -> press(HidButton.CONTROL_CENTER)
                AppleTvCommand.HOME_DOUBLE_PRESS -> {
                    press(HidButton.HOME)
                    press(HidButton.HOME)
                }
                AppleTvCommand.PLAY_PAUSE -> press(HidButton.PLAY_PAUSE)
                AppleTvCommand.PREVIOUS -> mediaControl(4)
                AppleTvCommand.NEXT -> mediaControl(3)
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
        runQueued(Capability.TOUCH) {
            when (gesture) {
                is TouchGesture.Tap -> {
                    press(HidButton.SELECT)
                    sendTouch(gesture.x, gesture.y, 5)
                }
                is TouchGesture.DoubleTap -> repeat(2) {
                    press(HidButton.SELECT)
                    sendTouch(gesture.x, gesture.y, 5)
                    delay(20)
                }
                is TouchGesture.Hold -> {
                    sendTouch(gesture.x, gesture.y, 1)
                    delay(gesture.durationMillis.coerceIn(1, 2_000))
                    sendTouch(gesture.x, gesture.y, 4)
                }
                is TouchGesture.Swipe -> swipe(gesture)
            }
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
        runQueued(Capability.VOLUME) { press(HidButton.VOLUME_UP) }
    }

    override suspend fun volumeDown() {
        runQueued(Capability.VOLUME) { press(HidButton.VOLUME_DOWN) }
    }

    override suspend fun mute() {
        throw UnsupportedCapabilityException(Capability.MUTE)
    }

    private suspend fun connectAuthenticated() {
        protocol?.close()
        protocol = null
        transport = null
        commandQueue.invalidate()
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
        touchEpochNanos = System.nanoTime()
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

    private suspend fun press(button: HidButton) {
        requireProtocol().exchange("_hidC", CompanionCommandEncoder.hid(button, true))
        requireProtocol().exchange("_hidC", CompanionCommandEncoder.hid(button, false))
    }

    private suspend fun mediaControl(command: Long, args: Map<String, Any?> = emptyMap()) {
        requireProtocol().exchange("_mcc", mapOf("_mcc" to command) + args)
    }

    private suspend fun sendTouch(x: Float, y: Float, phase: Long) {
        val point = CompanionCommandEncoder.touchPoint(x, y)
        requireProtocol().sendEvent(
            "_hidT",
            mapOf(
                "_ns" to (System.nanoTime() - touchEpochNanos).coerceAtLeast(0),
                "_tFg" to 1L,
                "_cx" to point.first.toLong(),
                "_tPh" to phase,
                "_cy" to point.second.toLong(),
            ),
        )
    }

    private suspend fun swipe(gesture: TouchGesture.Swipe) {
        val duration = gesture.durationMillis.coerceIn(16, 2_000)
        sendTouch(gesture.startX, gesture.startY, 1)
        val steps = (duration / 16).coerceAtLeast(1)
        for (step in 1 until steps) {
            val progress = step.toFloat() / steps
            sendTouch(
                gesture.startX + (gesture.endX - gesture.startX) * progress,
                gesture.startY + (gesture.endY - gesture.startY) * progress,
                3,
            )
            delay(16)
        }
        sendTouch(gesture.endX, gesture.endY, 4)
    }

    private suspend fun runQueued(
        capability: Capability,
        action: suspend () -> Unit,
    ) {
        if (!_capabilities.value.supports(capability)) {
            throw UnsupportedCapabilityException(capability)
        }
        when (val result = commandQueue.enqueue(action).await()) {
            CommandResult.Executed -> Unit
            CommandResult.DroppedStale -> throw IllegalStateException("Command expired after disconnect")
            CommandResult.RejectedFull -> throw IllegalStateException("Too many queued remote commands")
            is CommandResult.Failed -> {
                scheduleReconnect()
                throw result.error
            }
        }
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
        AppleTvCommand.SELECT -> Capability.SELECT
        AppleTvCommand.BACK -> Capability.BACK
        AppleTvCommand.HOME -> Capability.HOME
        AppleTvCommand.HOME_LONG_PRESS -> Capability.HOME_LONG_PRESS
        AppleTvCommand.HOME_DOUBLE_PRESS -> Capability.HOME_DOUBLE_PRESS
        AppleTvCommand.PLAY_PAUSE -> Capability.PLAY_PAUSE
        AppleTvCommand.PREVIOUS -> Capability.PREVIOUS
        AppleTvCommand.NEXT -> Capability.NEXT
    }

    private class PairingRequiredException : IllegalStateException()

    private companion object {
        val IMPLEMENTED_CAPABILITIES = Capability.entries.toSet() -
            setOf(Capability.MUTE, Capability.NOW_PLAYING)
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
