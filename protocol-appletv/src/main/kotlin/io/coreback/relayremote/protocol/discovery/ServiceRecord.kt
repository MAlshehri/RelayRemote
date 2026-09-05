package io.coreback.relayremote.protocol.discovery

enum class ServiceKind {
    COMPANION,
    MEDIA_REMOTE,
    AIRPLAY,
}

data class ServiceRecord(
    val recordId: String,
    val kind: ServiceKind,
    val serviceName: String,
    val addresses: Set<String>,
    val port: Int,
    val properties: Map<String, String>,
)

data class ResolvedEndpoint(
    val kind: ServiceKind,
    val addresses: Set<String>,
    val port: Int,
)

data class DiscoveredAppleTv(
    val id: String,
    val name: String,
    val model: String?,
    val online: Boolean,
    val services: Map<ServiceKind, ResolvedEndpoint>,
    val properties: Map<String, String>,
    val identityAliases: Set<String> = emptySet(),
) {
    val companionEndpoint: ResolvedEndpoint?
        get() = services[ServiceKind.COMPANION]
}
