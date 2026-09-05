package io.coreback.relayremote.devices

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import io.coreback.relayremote.protocol.discovery.DiscoveredAppleTv
import io.coreback.relayremote.protocol.discovery.DiscoveryRecordMerger
import io.coreback.relayremote.protocol.discovery.ServiceKind
import io.coreback.relayremote.protocol.discovery.ServiceRecord
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppleTvDiscovery {
    val devices: StateFlow<List<DiscoveredAppleTv>>
    val failure: StateFlow<String?>
    fun start()
    fun stop()
    fun refresh() {
        stop()
        start()
    }
}

class AndroidNsdDiscovery(
    private val nsdManager: NsdManager,
) : AppleTvDiscovery {
    private val merger = DiscoveryRecordMerger()
    private val lock = Any()
    private val listeners = ConcurrentHashMap<ServiceKind, NsdManager.DiscoveryListener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val restartDiscovery = Runnable(::start)
    private val _devices = MutableStateFlow<List<DiscoveredAppleTv>>(emptyList())
    private val _failure = MutableStateFlow<String?>(null)

    override val devices: StateFlow<List<DiscoveredAppleTv>> = _devices.asStateFlow()
    override val failure: StateFlow<String?> = _failure.asStateFlow()

    override fun start() {
        SERVICE_TYPES.forEach { (kind, serviceType) ->
            if (listeners.containsKey(kind)) return@forEach
            val listener = createDiscoveryListener(kind)
            listeners[kind] = listener
            try {
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener,
                )
            } catch (error: RuntimeException) {
                listeners.remove(kind)
                _failure.value = "Could not start local Apple TV discovery."
            }
        }
    }

    override fun stop() {
        mainHandler.removeCallbacks(restartDiscovery)
        listeners.entries.toList().forEach { (kind, listener) ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (_: IllegalArgumentException) {
                // Discovery already stopped by the platform.
            } finally {
                listeners.remove(kind)
            }
        }
    }

    override fun refresh() {
        stop()
        // Give NsdManager time to finish the asynchronous stop callback before
        // registering fresh listeners. Existing results remain visible meanwhile.
        mainHandler.postDelayed(restartDiscovery, 250)
    }

    private fun createDiscoveryListener(kind: ServiceKind) =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _failure.value = null
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolve(kind, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(lock) {
                    _devices.value = merger.remove(recordId(kind, serviceInfo))
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                listeners.remove(kind)
                _failure.value = "Apple TV discovery could not start (error $errorCode)."
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                listeners.remove(kind)
            }
        }

    @Suppress("DEPRECATION")
    private fun resolve(kind: ServiceKind, unresolved: NsdServiceInfo) {
        nsdManager.resolveService(
            unresolved,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    _failure.value = "Found an Apple TV but could not resolve its address."
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val addresses = serviceInfo.hostAddresses
                        .mapTo(linkedSetOf()) { it.hostAddress ?: it.hostName }
                        .filterTo(linkedSetOf()) { it.isNotBlank() }
                    if (addresses.isEmpty() || serviceInfo.port !in 1..65535) return
                    val properties = serviceInfo.attributes.mapValues { (_, value) ->
                        value.toString(Charsets.UTF_8)
                    }
                    val record = ServiceRecord(
                        recordId = recordId(kind, unresolved),
                        kind = kind,
                        serviceName = serviceInfo.serviceName,
                        addresses = addresses,
                        port = serviceInfo.port,
                        properties = properties,
                    )
                    synchronized(lock) {
                        _devices.value = merger.upsert(record)
                    }
                }
            },
        )
    }

    private fun recordId(kind: ServiceKind, serviceInfo: NsdServiceInfo): String =
        "${kind.name}:${serviceInfo.serviceName}:${serviceInfo.serviceType}"

    private companion object {
        val SERVICE_TYPES = linkedMapOf(
            ServiceKind.COMPANION to "_companion-link._tcp.",
            ServiceKind.MEDIA_REMOTE to "_mediaremotetv._tcp.",
            ServiceKind.AIRPLAY to "_airplay._tcp.",
        )
    }
}
