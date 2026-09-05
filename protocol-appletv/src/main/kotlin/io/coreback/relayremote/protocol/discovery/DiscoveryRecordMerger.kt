package io.coreback.relayremote.protocol.discovery

class DiscoveryRecordMerger {
    private val records = linkedMapOf<String, ServiceRecord>()
    private val offline = linkedMapOf<String, DiscoveredAppleTv>()

    fun upsert(record: ServiceRecord): List<DiscoveredAppleTv> {
        require(record.recordId.isNotBlank())
        require(record.port in 1..65535)
        records[record.recordId] = record
        val active = activeDevices()
        active.forEach { current ->
            offline.entries.removeAll { (_, previous) -> sameDevice(previous, current) }
        }
        return sorted(active + offline.values)
    }

    fun remove(recordId: String): List<DiscoveredAppleTv> {
        val before = activeDevices()
        records.remove(recordId) ?: return sorted(before + offline.values)
        val active = activeDevices()
        before.filter { previous -> active.none { current -> sameDevice(previous, current) } }
            .forEach { vanished ->
            offline[vanished.id] = vanished.copy(online = false, services = emptyMap())
        }
        return sorted(
            active + offline.values.filter { previous ->
                active.none { current -> sameDevice(previous, current) }
            },
        )
    }

    fun snapshot(): List<DiscoveredAppleTv> = sorted(activeDevices() + offline.values)

    private fun activeDevices(): List<DiscoveredAppleTv> {
        val remaining = records.values.toMutableList()
        val clusters = mutableListOf<List<ServiceRecord>>()
        while (remaining.isNotEmpty()) {
            val cluster = mutableListOf(remaining.removeAt(0))
            var changed: Boolean
            do {
                changed = false
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (cluster.any { matches(it, candidate) }) {
                        cluster += candidate
                        iterator.remove()
                        changed = true
                    }
                }
            } while (changed)
            clusters += cluster
        }
        return clusters.map(::merge).filter(::isPairableAppleTv)
    }

    private fun matches(left: ServiceRecord, right: ServiceRecord): Boolean {
        val sharedAddress = left.addresses.intersect(right.addresses).isNotEmpty()
        if (!sharedAddress) return false
        val sameName = normalize(left.serviceName) == normalize(right.serviceName)
        return sameName || left.kind != right.kind
    }

    private fun merge(cluster: List<ServiceRecord>): DiscoveredAppleTv {
        val combinedProperties = linkedMapOf<String, String>()
        cluster.sortedBy { it.kind.ordinal }.forEach { combinedProperties.putAll(it.properties) }
        val identityAliases = identityAliases(cluster)
        val id = stableId(cluster, identityAliases)
        val endpoints = cluster
            .groupBy { it.kind }
            .mapValues { (kind, services) ->
                val preferred = services.last()
                ResolvedEndpoint(
                    kind = kind,
                    addresses = services.flatMapTo(linkedSetOf()) { it.addresses },
                    port = preferred.port,
                )
            }
        return DiscoveredAppleTv(
            id = id,
            name = combinedProperties["Name"]
                ?: combinedProperties["name"]
                ?: cluster.first().serviceName,
            model = combinedProperties["rpmd"] ?: combinedProperties["model"],
            online = true,
            services = endpoints,
            properties = combinedProperties,
            identityAliases = identityAliases,
        )
    }

    private fun identityAliases(cluster: List<ServiceRecord>): Set<String> = buildSet {
        cluster.forEach { record ->
            when (record.kind) {
                ServiceKind.COMPANION -> record.properties.caseInsensitive("rpmrtid")
                    ?.normalizedIdentifier()
                    ?.let { add("companion:$it") }

                ServiceKind.MEDIA_REMOTE -> record.properties.caseInsensitive("UniqueIdentifier")
                    ?.normalizedIdentifier()
                    ?.let { add("mrp:$it") }

                ServiceKind.AIRPLAY -> record.properties.caseInsensitive("deviceid")
                    ?.normalizedIdentifier()
                    ?.let { add("airplay:$it") }
            }
        }
    }

    private fun stableId(cluster: List<ServiceRecord>, identityAliases: Set<String>): String {
        identityAliases.firstOrNull { it.startsWith("companion:") }?.let { return it }
        identityAliases.firstOrNull { it.startsWith("mrp:") }?.let { return it }
        identityAliases.firstOrNull { it.startsWith("airplay:") }?.let { return it }
        cluster.asSequence()
            .flatMap { it.addresses.asSequence() }
            .sorted()
            .firstOrNull()
            ?.let { return "endpoint:${normalize(cluster.first().serviceName)}@$it" }
        return "service:${normalize(cluster.first().serviceName)}"
    }

    private fun isPairableAppleTv(device: DiscoveredAppleTv): Boolean {
        if (device.companionEndpoint == null) return false
        val model = device.model?.trim()?.takeIf(String::isNotEmpty)
        if (model != null && !model.startsWith("AppleTV", ignoreCase = true)) return false
        return model != null ||
            ServiceKind.MEDIA_REMOTE in device.services ||
            device.identityAliases.any { it.startsWith("companion:") }
    }

    private fun sameDevice(left: DiscoveredAppleTv, right: DiscoveredAppleTv): Boolean {
        if (left.id == right.id) return true
        if (left.identityAliases.intersect(right.identityAliases).isNotEmpty()) return true
        val leftAddresses = left.companionEndpoint?.addresses.orEmpty()
        val rightAddresses = right.companionEndpoint?.addresses.orEmpty()
        return leftAddresses.isNotEmpty() &&
            rightAddresses.isNotEmpty() &&
            leftAddresses.intersect(rightAddresses).isNotEmpty()
    }

    private fun Map<String, String>.caseInsensitive(key: String): String? =
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    private fun normalize(value: String): String = value.trim().lowercase()

    private fun String.normalizedIdentifier(): String? =
        trim().lowercase().filter(Char::isLetterOrDigit).takeIf(String::isNotEmpty)

    private fun sorted(devices: Collection<DiscoveredAppleTv>): List<DiscoveredAppleTv> =
        devices.distinctBy { it.id }.sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
}
