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

    /** True while [mute] has silenced the session and can restore the prior level. */
    val muted: StateFlow<Boolean>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun beginPairing()
    suspend fun pair(pin: String): PairingCredentials
    suspend fun sendCommand(command: AppleTvCommand)
    suspend fun skipBy(seconds: Int)

    /**
     * Forwards one live touchpad event. Events must be sent in the order they happened;
     * a held Select must be ended with [TouchGesture.HoldEnded], and a begun touch
     * with [TouchGesture.Ended].
     */
    suspend fun sendTouchGesture(gesture: TouchGesture)

    /** Starts or stops continuous rewind or fast-forward. Every start needs a matching stop. */
    suspend fun setScan(direction: ScanDirection, active: Boolean)

    suspend fun listApps(): List<AppleTvApp>
    suspend fun launchApp(bundleId: String)
    suspend fun listAccounts(): List<AppleTvAccount>
    suspend fun switchAccount(accountId: String)
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
    FAST_FORWARD,
    REWIND,
    CHANNELS,
    SCREENSAVER,
    ACCOUNTS,
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

    /** Holds Select: opens the menu for the highlighted item, such as an app on the Home Screen. */
    SELECT_LONG_PRESS,
    BACK,

    /** Holds Back: returns to the Home Screen. */
    BACK_LONG_PRESS,
    HOME,

    /** Opens Control Center, the same action as holding the TV button. */
    HOME_LONG_PRESS,

    /** Opens the App Switcher. */
    HOME_DOUBLE_PRESS,
    PLAY_PAUSE,
    PREVIOUS,
    NEXT,
    GUIDE,
    CHANNEL_UP,
    CHANNEL_DOWN,
    SCREENSAVER,
}

enum class ScanDirection {
    REWIND,
    FAST_FORWARD,
}

/** Live events from the touchpad, in normalized 0..1 coordinates. */
sealed interface TouchGesture {
    data class Began(val x: Float, val y: Float) : TouchGesture
    data class Moved(val x: Float, val y: Float) : TouchGesture
    data class Ended(val x: Float, val y: Float) : TouchGesture

    /** The finger stayed still long enough: press and keep holding Select. */
    data object HoldStarted : TouchGesture

    /** Releases the held Select, no earlier than tvOS needs to recognize the hold. */
    data object HoldEnded : TouchGesture
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

data class AppleTvAccount(
    val id: String,
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
