package io.coreback.relayremote.data

import com.google.protobuf.ByteString
import io.coreback.relayremote.data.proto.SavedDevice
import io.coreback.relayremote.data.proto.SavedEndpoint
import io.coreback.relayremote.protocol.discovery.DiscoveredAppleTv
import io.coreback.relayremote.protocol.discovery.ResolvedEndpoint
import io.coreback.relayremote.protocol.discovery.ServiceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class DeviceSettings(
    val devices: List<StoredAppleTv>,
    val selectedDeviceId: String?,
    val keepScreenAwake: Boolean,
    val skipSeconds: Int,
    val directionalButtons: Boolean = false,
)

data class StoredAppleTv(
    val id: String,
    val discoveredName: String,
    val alias: String?,
    val model: String?,
    val identityAliases: Set<String>,
    val endpoints: List<StoredEndpoint>,
    val favoriteBundleIds: Set<String>,
    val lastSeenEpochMillis: Long,
    val paired: Boolean,
) {
    val displayName: String get() = alias ?: discoveredName
}

data class StoredEndpoint(
    val kind: ServiceKind,
    val addresses: Set<String>,
    val port: Int,
)

class DeviceRepository(
    private val source: DeviceStoreDataSource,
    private val credentialCipher: CredentialCipher,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    val settings: Flow<DeviceSettings> = source.data.map { store ->
        DeviceSettings(
            devices = store.devicesList.map(::toStored),
            selectedDeviceId = store.selectedDeviceId.takeIf(String::isNotBlank),
            keepScreenAwake = store.keepScreenAwake,
            skipSeconds = store.skipSeconds.takeIf(SUPPORTED_SKIP_SECONDS::contains)
                ?: DEFAULT_SKIP_SECONDS,
            directionalButtons = store.directionalButtons,
        )
    }.distinctUntilChanged()

    suspend fun upsert(device: DiscoveredAppleTv) {
        source.update { current ->
            val storedDevices = current.devicesList.map(::toStored)
            val matched = DeviceIdentityMatcher.bestMatch(storedDevices, device)
            val index = matched?.let { match ->
                current.devicesList.indexOfFirst { it.id == match.id }
            } ?: -1
            val prior = current.devicesList.getOrNull(index)
            val retainedId = prior?.id ?: device.id
            val identityAliases = buildSet {
                prior?.identityAliasesList?.forEach { alias ->
                    normalizeIdentityAlias(alias)?.let(::add)
                }
                device.identityAliases.forEach { alias ->
                    normalizeIdentityAlias(alias)?.let(::add)
                }
            }
            val updated = (prior?.toBuilder() ?: SavedDevice.newBuilder())
                .setId(retainedId)
                .setDiscoveredName(device.name)
                .setModel(device.model.orEmpty())
                .setLastSeenEpochMillis(currentTimeMillis())
                .clearIdentityAliases()
                .addAllIdentityAliases(identityAliases.sorted())
                .clearEndpoints()
                .addAllEndpoints(device.services.values.map(::toProto))
                .build()
            current.toBuilder().apply {
                if (index >= 0) setDevices(index, updated) else addDevices(updated)
            }.build()
        }
    }

    suspend fun rename(deviceId: String, alias: String?) {
        updateDevice(deviceId) {
            alias?.trim()?.takeIf(String::isNotEmpty)?.let(::setAlias) ?: clearAlias()
        }
    }

    suspend fun select(deviceId: String) {
        source.update { current ->
            require(current.devicesList.any { it.id == deviceId }) { "Unknown Apple TV" }
            current.toBuilder().setSelectedDeviceId(deviceId).build()
        }
    }

    suspend fun forget(deviceId: String) {
        source.update { current ->
            val index = current.devicesList.indexOfFirst { it.id == deviceId }
            if (index < 0) return@update current
            current.toBuilder().apply {
                removeDevices(index)
                if (current.selectedDeviceId == deviceId) clearSelectedDeviceId()
            }.build()
        }
    }

    suspend fun saveCredentials(deviceId: String, plaintext: ByteArray) {
        val envelope = credentialCipher.encrypt(deviceId, plaintext)
        try {
            updateDevice(deviceId) {
                credentialEnvelope = ByteString.copyFrom(envelope)
            }
        } finally {
            plaintext.fill(0)
            envelope.fill(0)
        }
    }

    suspend fun loadCredentials(deviceId: String): ByteArray? {
        val device = source.data.first().devicesList.firstOrNull { it.id == deviceId }
            ?: return null
        if (device.credentialEnvelope.isEmpty) return null
        val envelope = device.credentialEnvelope.toByteArray()
        return try {
            credentialCipher.decrypt(deviceId, envelope)
        } finally {
            envelope.fill(0)
        }
    }

    suspend fun setFavorite(deviceId: String, bundleId: String, favorite: Boolean) {
        updateDevice(deviceId) {
            val values = favoriteBundleIdsList.toMutableSet()
            if (favorite) values += bundleId else values -= bundleId
            clearFavoriteBundleIds()
            addAllFavoriteBundleIds(values.sorted())
        }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        source.update { it.toBuilder().setKeepScreenAwake(enabled).build() }
    }

    suspend fun setDirectionalButtons(enabled: Boolean) {
        source.update { it.toBuilder().setDirectionalButtons(enabled).build() }
    }

    suspend fun setSkipSeconds(seconds: Int) {
        require(seconds in SUPPORTED_SKIP_SECONDS) {
            "Unsupported skip duration: $seconds"
        }
        source.update { it.toBuilder().setSkipSeconds(seconds).build() }
    }

    private suspend fun updateDevice(
        deviceId: String,
        change: SavedDevice.Builder.() -> Unit,
    ) {
        source.update { current ->
            val index = current.devicesList.indexOfFirst { it.id == deviceId }
            require(index >= 0) { "Unknown Apple TV" }
            val updated = current.devicesList[index].toBuilder().apply(change).build()
            current.toBuilder().setDevices(index, updated).build()
        }
    }

    private fun toStored(device: SavedDevice): StoredAppleTv = StoredAppleTv(
        id = device.id,
        discoveredName = device.discoveredName,
        alias = device.alias.takeIf(String::isNotBlank),
        model = device.model.takeIf(String::isNotBlank),
        identityAliases = device.identityAliasesList.mapNotNullTo(linkedSetOf()) {
            normalizeIdentityAlias(it)
        },
        endpoints = device.endpointsList.map {
            StoredEndpoint(
                kind = it.kind.toServiceKind(),
                addresses = it.addressesList.toSet(),
                port = it.port,
            )
        },
        favoriteBundleIds = device.favoriteBundleIdsList.toSet(),
        lastSeenEpochMillis = device.lastSeenEpochMillis,
        paired = !device.credentialEnvelope.isEmpty,
    )

    private fun toProto(endpoint: ResolvedEndpoint): SavedEndpoint =
        SavedEndpoint.newBuilder()
            .setKind(endpoint.kind.toProtoKind())
            .addAllAddresses(endpoint.addresses.sorted())
            .setPort(endpoint.port)
            .build()

    private fun ServiceKind.toProtoKind(): SavedEndpoint.Kind = when (this) {
        ServiceKind.COMPANION -> SavedEndpoint.Kind.COMPANION
        ServiceKind.MEDIA_REMOTE -> SavedEndpoint.Kind.MEDIA_REMOTE
        ServiceKind.AIRPLAY -> SavedEndpoint.Kind.AIRPLAY
    }

    private fun SavedEndpoint.Kind.toServiceKind(): ServiceKind = when (this) {
        SavedEndpoint.Kind.COMPANION -> ServiceKind.COMPANION
        SavedEndpoint.Kind.MEDIA_REMOTE -> ServiceKind.MEDIA_REMOTE
        SavedEndpoint.Kind.AIRPLAY,
        SavedEndpoint.Kind.KIND_UNSPECIFIED,
        SavedEndpoint.Kind.UNRECOGNIZED,
        -> ServiceKind.AIRPLAY
    }

    private companion object {
        const val DEFAULT_SKIP_SECONDS = 30
        val SUPPORTED_SKIP_SECONDS = setOf(10, 15, 30, 60)
    }
}
