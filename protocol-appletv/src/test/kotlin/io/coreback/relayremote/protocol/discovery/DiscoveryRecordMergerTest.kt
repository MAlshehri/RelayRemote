package io.coreback.relayremote.protocol.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryRecordMergerTest {
    @Test
    fun `companion mrp and airplay records merge by address and stable identifier`() {
        val merger = DiscoveryRecordMerger()
        merger.upsert(record(ServiceKind.COMPANION, "c", "Living Room", "10.0.0.4", 49152))
        merger.upsert(
            record(
                ServiceKind.MEDIA_REMOTE,
                "m",
                "Living Room",
                "10.0.0.4",
                49153,
                mapOf("UniqueIdentifier" to "MRP-ABC"),
            ),
        )
        val devices = merger.upsert(
            record(
                ServiceKind.AIRPLAY,
                "a",
                "Living Room",
                "10.0.0.4",
                7000,
                mapOf("deviceid" to "AA:BB:CC:DD:EE:FF", "model" to "AppleTV6,2"),
            ),
        )

        assertEquals(1, devices.size)
        assertEquals("mrp:mrpabc", devices.single().id)
        assertEquals("AppleTV6,2", devices.single().model)
        assertEquals(3, devices.single().services.size)
        assertEquals(
            setOf(
                "mrp:mrpabc",
                "airplay:aabbccddeeff",
            ),
            devices.single().identityAliases,
        )
        assertTrue(devices.single().online)
    }

    @Test
    fun `different protocol service names merge when endpoint address is shared`() {
        val merger = DiscoveryRecordMerger()
        merger.upsert(
            record(
                ServiceKind.COMPANION,
                "c",
                "Living Room Companion",
                "10.0.0.4",
                49152,
            ),
        )
        val devices = merger.upsert(
            record(
                ServiceKind.MEDIA_REMOTE,
                "m",
                "Living Room MRP Service",
                "10.0.0.4",
                49153,
                mapOf("UniqueIdentifier" to "MRP-ABC"),
            ),
        )

        assertEquals(1, devices.size)
        assertEquals(setOf(ServiceKind.COMPANION, ServiceKind.MEDIA_REMOTE), devices.single().services.keys)
        assertEquals(setOf("mrp:mrpabc"), devices.single().identityAliases)
    }

    @Test
    fun `same name at different addresses remains separate`() {
        val merger = DiscoveryRecordMerger()
        merger.upsert(
            record(
                ServiceKind.COMPANION,
                "one",
                "Apple TV",
                "10.0.0.5",
                1,
                mapOf("rpmrtid" to "ONE"),
            ),
        )
        val devices = merger.upsert(
            record(
                ServiceKind.COMPANION,
                "two",
                "Apple TV",
                "10.0.0.6",
                2,
                mapOf("rpmrtid" to "TWO"),
            ),
        )

        assertEquals(2, devices.size)
    }

    @Test
    fun `removing final live service retains an offline saved identity`() {
        val merger = DiscoveryRecordMerger()
        merger.upsert(
            record(
                ServiceKind.COMPANION,
                "c",
                "Bedroom",
                "fe80::1",
                49152,
                mapOf("rpmrtid" to "BEDROOM"),
            ),
        )

        val devices = merger.remove("c")

        assertEquals(1, devices.size)
        assertFalse(devices.single().online)
        assertEquals("companion:bedroom", devices.single().id)
    }

    @Test
    fun `identity change after supplementary service removal does not create offline duplicate`() {
        val merger = DiscoveryRecordMerger()
        merger.upsert(
            record(
                ServiceKind.COMPANION,
                "c",
                "Cinema",
                "10.0.0.12",
                49152,
                mapOf("rpmd" to "AppleTV14,1"),
            ),
        )
        merger.upsert(
            record(
                ServiceKind.MEDIA_REMOTE,
                "m",
                "Cinema",
                "10.0.0.12",
                49153,
                mapOf("UniqueIdentifier" to "MRP-CINEMA"),
            ),
        )

        val devices = merger.remove("m")

        assertEquals(1, devices.size)
        assertTrue(devices.single().online)
        assertTrue(devices.single().id.startsWith("endpoint:"))
    }

    @Test
    fun `companion stable identifier qualifies a device and is preferred as local id`() {
        val devices = DiscoveryRecordMerger().upsert(
            record(
                ServiceKind.COMPANION,
                "c",
                "Family Room",
                "10.0.0.4",
                49152,
                mapOf("RpMrTiD" to " 12:34:AB "),
            ),
        )

        assertEquals(1, devices.size)
        assertEquals("companion:1234ab", devices.single().id)
        assertEquals(setOf("companion:1234ab"), devices.single().identityAliases)
    }

    @Test
    fun `identity punctuation variants normalize to the same alias`() {
        fun discover(identifier: String) = DiscoveryRecordMerger().upsert(
            record(
                ServiceKind.COMPANION,
                "c",
                "Family Room",
                "10.0.0.4",
                49152,
                mapOf("rpmrtid" to identifier),
            ),
        ).single()

        val colonAndHyphen = discover("AA:BB-CC")
        val braces = discover("{aabbcc}")

        assertEquals(colonAndHyphen.identityAliases, braces.identityAliases)
        assertEquals(setOf("companion:aabbcc"), colonAndHyphen.identityAliases)
    }

    @Test
    fun `companion with Apple TV model qualifies without a stable identifier`() {
        val devices = DiscoveryRecordMerger().upsert(
            record(
                ServiceKind.COMPANION,
                "c",
                "Family Room",
                "10.0.0.4",
                49152,
                mapOf("rpmd" to "AppleTV14,1"),
            ),
        )

        assertEquals(1, devices.size)
        assertEquals("AppleTV14,1", devices.single().model)
        assertTrue(devices.single().identityAliases.isEmpty())
    }

    @Test
    fun `companion and media remote qualify regardless of arrival order`() {
        val companion = record(
            ServiceKind.COMPANION,
            "c",
            "Cinema",
            "10.0.0.9",
            49152,
            mapOf("rpmrtid" to "COMPANION-ID"),
        )
        val mediaRemote = record(
            ServiceKind.MEDIA_REMOTE,
            "m",
            "Cinema",
            "10.0.0.9",
            49153,
            mapOf("UniqueIdentifier" to "MRP-ID"),
        )

        val companionFirst = DiscoveryRecordMerger().run {
            upsert(companion)
            upsert(mediaRemote).single()
        }
        val mediaRemoteFirst = DiscoveryRecordMerger().run {
            upsert(mediaRemote)
            upsert(companion).single()
        }

        assertEquals(companionFirst.id, mediaRemoteFirst.id)
        assertEquals(companionFirst.identityAliases, mediaRemoteFirst.identityAliases)
        assertEquals(
            setOf("companion:companionid", "mrp:mrpid"),
            companionFirst.identityAliases,
        )
    }

    @Test
    fun `airplay alone and unqualified companion records are excluded`() {
        val merger = DiscoveryRecordMerger()

        assertTrue(
            merger.upsert(
                record(
                    ServiceKind.AIRPLAY,
                    "a",
                    "Phone",
                    "10.0.0.10",
                    7000,
                    mapOf("deviceid" to "AA:BB", "model" to "iPhone17,2"),
                ),
            ).isEmpty(),
        )
        assertTrue(
            merger.upsert(
                record(
                    ServiceKind.COMPANION,
                    "c",
                    "Unknown Apple Device",
                    "10.0.0.11",
                    49152,
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun `non TV Apple models are excluded even with companion`() {
        listOf("iPhone17,2", "iPad16,6", "Mac15,7", "AudioAccessory6,1").forEachIndexed {
                index,
                model,
            ->
            val devices = DiscoveryRecordMerger().upsert(
                record(
                    ServiceKind.COMPANION,
                    "c-$index",
                    model,
                    "10.0.1.$index",
                    49152,
                    mapOf("rpmd" to model, "rpmrtid" to "stable-$index"),
                ),
            )

            assertTrue("Expected $model to be excluded", devices.isEmpty())
        }
    }

    private fun record(
        kind: ServiceKind,
        recordId: String,
        name: String,
        address: String,
        port: Int,
        properties: Map<String, String> = emptyMap(),
    ) = ServiceRecord(
        recordId = recordId,
        kind = kind,
        serviceName = name,
        addresses = setOf(address),
        port = port,
        properties = properties,
    )
}
