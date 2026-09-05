package io.coreback.relayremote.data

import io.coreback.relayremote.protocol.discovery.DiscoveredAppleTv
import io.coreback.relayremote.protocol.discovery.ResolvedEndpoint
import io.coreback.relayremote.protocol.discovery.ServiceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityMatcherTest {
    @Test
    fun `matching rank prefers exact id then alias then companion endpoint`() {
        val discovered = discoveredDevice()

        assertEquals(0, DeviceIdentityMatcher.matchRank(savedDevice(id = discovered.id), discovered))
        assertEquals(
            1,
            DeviceIdentityMatcher.matchRank(
                savedDevice(identityAliases = setOf("companion:stable")),
                discovered,
            ),
        )
        assertEquals(2, DeviceIdentityMatcher.matchRank(savedDevice(), discovered))
    }

    @Test
    fun `display name alone never matches`() {
        val discovered = discoveredDevice().copy(
            identityAliases = emptySet(),
            services = mapOf(
                ServiceKind.COMPANION to ResolvedEndpoint(
                    ServiceKind.COMPANION,
                    setOf("10.0.0.99"),
                    49152,
                ),
            ),
        )

        assertNull(DeviceIdentityMatcher.matchRank(savedDevice(), discovered))
    }

    @Test
    fun `alias identifier punctuation does not prevent a stable match`() {
        val saved = savedDevice(identityAliases = setOf("companion:{stable-id}"))
        val discovered = discoveredDevice().copy(
            identityAliases = setOf("companion:stableid"),
            services = mapOf(
                ServiceKind.COMPANION to ResolvedEndpoint(
                    ServiceKind.COMPANION,
                    setOf("10.0.0.99"),
                    49152,
                ),
            ),
        )

        assertEquals(1, DeviceIdentityMatcher.matchRank(saved, discovered))
    }

    @Test
    fun `online fallback wins over an exact offline tombstone`() {
        val saved = savedDevice()
        val offlineExact = discoveredDevice().copy(
            id = saved.id,
            online = false,
            services = emptyMap(),
            identityAliases = emptySet(),
        )
        val onlineByEndpoint = discoveredDevice().copy(
            id = "mrp:new-identity",
            online = true,
            identityAliases = emptySet(),
        )

        val resolved = DeviceIdentityMatcher.bestDiscovery(
            saved,
            listOf(offlineExact, onlineByEndpoint),
        )

        assertTrue(resolved?.online == true)
        assertEquals("mrp:new-identity", resolved?.id)
    }

    private fun savedDevice(
        id: String = "endpoint:living@10.0.0.4",
        identityAliases: Set<String> = emptySet(),
    ) = StoredAppleTv(
        id = id,
        discoveredName = "Living Room",
        alias = null,
        model = "AppleTV14,1",
        identityAliases = identityAliases,
        endpoints = listOf(
            StoredEndpoint(
                ServiceKind.COMPANION,
                setOf("10.0.0.4"),
                49152,
            ),
        ),
        favoriteBundleIds = emptySet(),
        lastSeenEpochMillis = 0,
        paired = false,
    )

    private fun discoveredDevice() = DiscoveredAppleTv(
        id = "mrp:living",
        name = "Living Room",
        model = "AppleTV14,1",
        online = true,
        services = mapOf(
            ServiceKind.COMPANION to ResolvedEndpoint(
                ServiceKind.COMPANION,
                setOf("10.0.0.4"),
                49152,
            ),
        ),
        properties = emptyMap(),
        identityAliases = setOf("companion:stable", "mrp:living"),
    )
}
