package io.coreback.relayremote

import android.content.Context
import android.net.nsd.NsdManager
import android.util.Log
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import io.coreback.relayremote.data.AndroidKeystoreKeyProvider
import io.coreback.relayremote.data.CredentialCipher
import io.coreback.relayremote.data.DeviceIdentityMatcher
import io.coreback.relayremote.data.DeviceRepository
import io.coreback.relayremote.data.DeviceStoreSerializer
import io.coreback.relayremote.data.ProtoDeviceStoreDataSource
import io.coreback.relayremote.devices.AndroidNsdDiscovery
import io.coreback.relayremote.protocol.AppleTvClient
import io.coreback.relayremote.protocol.companion.CompanionAppleTvClient
import io.coreback.relayremote.protocol.discovery.ServiceKind
import io.coreback.relayremote.protocol.pairing.PairingCredentialsCodec
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dataStore = DataStoreFactory.create(
        serializer = DeviceStoreSerializer,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.dataStoreFile("device_store.pb") },
    )
    val deviceRepository = DeviceRepository(
        ProtoDeviceStoreDataSource(dataStore),
        CredentialCipher(AndroidKeystoreKeyProvider()),
    )
    val discovery = AndroidNsdDiscovery(
        context.getSystemService(NsdManager::class.java),
    )
    private val activeClient = AtomicReference<AppleTvClient?>()

    fun createClient(deviceId: String): AppleTvClient {
        val client = CompanionAppleTvClient(
            deviceIdentifier = deviceId,
            endpointResolver = {
                val saved = deviceRepository.settings
                    .first()
                    .devices
                    .firstOrNull { it.id == deviceId }
                val live = saved?.let {
                    DeviceIdentityMatcher.bestDiscovery(it, discovery.devices.value)
                } ?: discovery.devices.value.firstOrNull { it.id == deviceId && it.online }
                live?.companionEndpoint
                    ?: saved
                        ?.endpoints
                        ?.firstOrNull { it.kind == ServiceKind.COMPANION }
                        ?.let {
                            io.coreback.relayremote.protocol.discovery.ResolvedEndpoint(
                                it.kind,
                                it.addresses,
                                it.port,
                            )
                        }
            },
            credentialsProvider = {
                deviceRepository.loadCredentials(deviceId)?.let { encoded ->
                    try {
                        PairingCredentialsCodec.decode(encoded)
                    } finally {
                        encoded.fill(0)
                    }
                }
            },
            credentialsSink = { credentials ->
                val encoded = PairingCredentialsCodec.encode(credentials)
                deviceRepository.saveCredentials(deviceId, encoded)
            },
            scope = applicationScope,
            pairingTrace = { message ->
                if (BuildConfig.DEBUG) Log.d("RelayPairing", message)
            },
        )
        activeClient.getAndSet(client)?.let { previous ->
            applicationScope.launch { previous.disconnect() }
        }
        return client
    }

    fun enterForeground() {
        discovery.start()
        activeClient.get()?.let { client ->
            applicationScope.launch { runCatching { client.connect() } }
        }
    }

    fun enterBackground() {
        discovery.stop()
        activeClient.get()?.let { client ->
            applicationScope.launch { runCatching { client.disconnect() } }
        }
    }
}
