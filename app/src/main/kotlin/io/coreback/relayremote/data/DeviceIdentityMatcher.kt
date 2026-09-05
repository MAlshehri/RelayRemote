package io.coreback.relayremote.data

import io.coreback.relayremote.protocol.discovery.DiscoveredAppleTv
import io.coreback.relayremote.protocol.discovery.ServiceKind

object DeviceIdentityMatcher {
    fun matchRank(saved: StoredAppleTv, discovered: DiscoveredAppleTv): Int? {
        if (saved.id == discovered.id) return 0
        if (normalized(saved.identityAliases).intersect(normalized(discovered.identityAliases)).isNotEmpty()) {
            return 1
        }
        val savedAddresses = saved.endpoints
            .asSequence()
            .filter { it.kind == ServiceKind.COMPANION }
            .flatMap { it.addresses.asSequence() }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        val discoveredAddresses = discovered.companionEndpoint
            ?.addresses
            .orEmpty()
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        return 2.takeIf {
            savedAddresses.isNotEmpty() &&
                discoveredAddresses.isNotEmpty() &&
                savedAddresses.intersect(discoveredAddresses).isNotEmpty()
        }
    }

    fun bestMatch(
        savedDevices: List<StoredAppleTv>,
        discovered: DiscoveredAppleTv,
    ): StoredAppleTv? {
        val ranked = savedDevices.mapNotNull { saved ->
            matchRank(saved, discovered)?.let { rank -> Triple(rank, saved.id, saved) }
        }
        val bestRank = ranked.minOfOrNull { it.first } ?: return null
        val best = ranked.filter { it.first == bestRank }
        return best.singleOrNull()?.third
    }

    fun bestDiscovery(
        saved: StoredAppleTv,
        discoveredDevices: List<DiscoveredAppleTv>,
    ): DiscoveredAppleTv? {
        val ranked = discoveredDevices.mapNotNull { discovered ->
            matchRank(saved, discovered)?.let { rank -> Triple(rank, discovered.id, discovered) }
        }
        val candidates = ranked.filter { it.third.online }.ifEmpty { ranked }
        val bestRank = candidates.minOfOrNull { it.first } ?: return null
        val best = candidates.filter { it.first == bestRank }
        return best.singleOrNull()?.third
    }

    private fun normalized(values: Set<String>): Set<String> = values
        .asSequence()
        .mapNotNull(::normalizeIdentityAlias)
        .toSet()
}

internal fun normalizeIdentityAlias(value: String): String? {
    val normalized = value.trim().lowercase()
    val separator = normalized.indexOf(':')
    if (separator <= 0 || separator == normalized.lastIndex) return null
    val namespace = normalized.substring(0, separator).filter(Char::isLetterOrDigit)
    val identifier = normalized.substring(separator + 1).filter(Char::isLetterOrDigit)
    return if (namespace.isNotEmpty() && identifier.isNotEmpty()) {
        "$namespace:$identifier"
    } else {
        null
    }
}
