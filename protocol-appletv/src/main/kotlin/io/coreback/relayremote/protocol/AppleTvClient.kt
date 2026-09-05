package io.coreback.relayremote.protocol

import kotlinx.coroutines.flow.StateFlow

/**
 * Protocol-facing contract for one Apple TV.
 *
 * UI code must check [capabilities] before offering an action. Implementations
 * must also reject unsupported actions to keep non-UI callers honest.
 */
interface AppleTvClient {
    val connectionState: StateFlow<ConnectionState>
    val capabilities: StateFlow<CapabilitySet>
    val nowPlaying: StateFlow<NowPlaying?>
    val powerState: StateFlow<PowerState>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun beginPairing()
    suspend fun pair(pin: String): PairingCredentials
    suspend fun sendCommand(command: AppleTvCommand)
    suspend fun skipBy(seconds: Int)
    suspend fun sendTouchGesture(gesture: TouchGesture)
    suspend fun listApps(): List<AppleTvApp>
    suspend fun launchApp(bundleId: String)
    suspend fun getKeyboardText(): String?
    suspend fun setKeyboardText(operation: KeyboardOperation)
    suspend fun wake()
    suspend fun sleep()
    suspend fun volumeUp()
    suspend fun volumeDown()
    suspend fun mute()
}

sealed interface ConnectionState {
    data object Scanning : ConnectionState
    data object Pairing : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object Waking : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    data object Offline : ConnectionState
    data object AuthenticationFailed : ConnectionState
    data class Error(val message: String, val retryable: Boolean) : ConnectionState
}

enum class Capability {
    NAVIGATION,
    SELECT,
    BACK,
    HOME,
    HOME_LONG_PRESS,
    HOME_DOUBLE_PRESS,
    PLAY_PAUSE,
    PREVIOUS,
    NEXT,
    SKIP_FORWARD,
    SKIP_BACKWARD,
    WAKE,
    SLEEP,
    VOLUME,
    MUTE,
    TOUCH,
    APPS,
    KEYBOARD,
    NOW_PLAYING,
}

data class CapabilitySet(
    val implemented: Set<Capability> = emptySet(),
    val available: Set<Capability> = emptySet(),
) {
    fun supports(capability: Capability): Boolean =
        capability in implemented && capability in available

    companion object {
        val None = CapabilitySet()
    }
}

enum class AppleTvCommand {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    SELECT,
    BACK,
    HOME,
    HOME_LONG_PRESS,
    HOME_DOUBLE_PRESS,
    PLAY_PAUSE,
    PREVIOUS,
    NEXT,
}

sealed interface TouchGesture {
    data class Tap(val x: Float, val y: Float) : TouchGesture
    data class DoubleTap(val x: Float, val y: Float) : TouchGesture
    data class Hold(val x: Float, val y: Float, val durationMillis: Long) : TouchGesture
    data class Swipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val durationMillis: Long,
    ) : TouchGesture
}

sealed interface KeyboardOperation {
    data class Replace(val text: String) : KeyboardOperation
    data class Append(val text: String) : KeyboardOperation
    data class Paste(val text: String) : KeyboardOperation
    data object Clear : KeyboardOperation
}

data class AppleTvApp(
    val bundleId: String,
    val name: String,
)

data class NowPlaying(
    val title: String?,
    val artist: String?,
    val album: String?,
    val appName: String?,
    val durationMillis: Long?,
    val positionMillis: Long?,
    val isPlaying: Boolean,
)

enum class PowerState {
    UNKNOWN,
    OFF,
    ON,
}

data class PairingCredentials(
    val clientIdentifier: ByteArray,
    val clientPrivateKey: ByteArray,
    val clientPublicKey: ByteArray,
    val accessoryIdentifier: ByteArray,
    val accessoryPublicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is PairingCredentials &&
            clientIdentifier.contentEquals(other.clientIdentifier) &&
            clientPrivateKey.contentEquals(other.clientPrivateKey) &&
            clientPublicKey.contentEquals(other.clientPublicKey) &&
            accessoryIdentifier.contentEquals(other.accessoryIdentifier) &&
            accessoryPublicKey.contentEquals(other.accessoryPublicKey)

    override fun hashCode(): Int =
        listOf(
            clientIdentifier,
            clientPrivateKey,
            clientPublicKey,
            accessoryIdentifier,
            accessoryPublicKey,
        ).fold(1) { result, bytes -> 31 * result + bytes.contentHashCode() }
}

class UnsupportedCapabilityException(
    val capability: Capability,
) : IllegalStateException("$capability is not supported by this Apple TV session")
