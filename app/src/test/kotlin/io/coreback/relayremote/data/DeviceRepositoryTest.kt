package io.coreback.relayremote.data

import io.coreback.relayremote.data.proto.DeviceStore
import io.coreback.relayremote.protocol.discovery.DiscoveredAppleTv
import io.coreback.relayremote.protocol.discovery.ResolvedEndpoint
import io.coreback.relayremote.protocol.discovery.ServiceKind
import javax.crypto.KeyGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRepositoryTest {
    private val source = FakeDeviceStoreDataSource()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = CredentialCipher(SecretKeyProvider { key })
    private val repository = DeviceRepository(source, cipher) { 1234L }

    @Test
    fun `discovered device metadata selection rename and credentials persist`() = runTest {
        repository.upsert(discoveredDevice())
        repository.rename("mrp:living", "Cinema")
        repository.select("mrp:living")
        repository.saveCredentials("mrp:living", "private-credential".toByteArray())

        val settings = repository.settings.first()
        val device = settings.devices.single()

        assertEquals("mrp:living", settings.selectedDeviceId)
        assertEquals("Cinema", device.displayName)
        assertEquals(1234L, device.lastSeenEpochMillis)
        assertEquals(setOf("10.0.0.4"), device.endpoints.single().addresses)
        assertTrue(device.paired)
        assertFalse(
            source.current.devicesList.single().credentialEnvelope
                .toByteArray()
                .contentEquals("private-credential".toByteArray()),
        )
        assertEquals(
            "private-credential",
            repository.loadCredentials("mrp:living")!!.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `forget removes device and clears selection`() = runTest {
        repository.upsert(discoveredDevice())
        repository.select("mrp:living")

        repository.forget("mrp:living")

        val settings = repository.settings.first()
        assertTrue(settings.devices.isEmpty())
        assertEquals(null, settings.selectedDeviceId)
    }

    @Test
    fun `selection and application favorites remain independent per Apple TV`() = runTest {
        repository.upsert(discoveredDevice())
        repository.upsert(
            discoveredDevice().copy(
                id = "mrp:bedroom",
                name = "Bedroom",
                identityAliases = setOf("mrp:bedroom"),
                services = mapOf(
                    ServiceKind.COMPANION to ResolvedEndpoint(
                        ServiceKind.COMPANION,
                        setOf("10.0.0.8"),
                        49153,
                    ),
                ),
            ),
        )
        repository.setFavorite("mrp:living", "com.apple.TV", favorite = true)
        repository.select("mrp:bedroom")

        val settings = repository.settings.first()

        assertEquals("mrp:bedroom", settings.selectedDeviceId)
        assertEquals(
            setOf("com.apple.TV"),
            settings.devices.single { it.id == "mrp:living" }.favoriteBundleIds,
        )
        assertTrue(settings.devices.single { it.id == "mrp:bedroom" }.favoriteBundleIds.isEmpty())
    }

    @Test
    fun `skip duration defaults to 30 when unset or invalid`() = runTest {
        assertEquals(30, repository.settings.first().skipSeconds)

        val invalidSource = FakeDeviceStoreDataSource(
            DeviceStore.newBuilder().setSkipSeconds(17).build(),
        )
        val invalidRepository = DeviceRepository(invalidSource, cipher)

        assertEquals(30, invalidRepository.settings.first().skipSeconds)
    }

    @Test
    fun `supported skip duration persists and unsupported value is rejected`() = runTest {
        repository.setSkipSeconds(60)

        assertEquals(60, repository.settings.first().skipSeconds)
        assertEquals(60, source.current.skipSeconds)

        val failure = runCatching { repository.setSkipSeconds(45) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(60, source.current.skipSeconds)
    }

    @Test
    fun `rediscovery by companion endpoint retains paired saved identity and learns aliases`() =
        runTest {
            val initial = discoveredDevice().copy(
                id = "endpoint:living room@10.0.0.4",
                identityAliases = emptySet(),
            )
            repository.upsert(initial)
            repository.rename(initial.id, "Cinema")
            repository.setFavorite(initial.id, "com.apple.TV", favorite = true)
            repository.select(initial.id)
            repository.saveCredentials(initial.id, "private-credential".toByteArray())

            repository.upsert(
                discoveredDevice().copy(
                    id = "mrp:living-stable",
                    identityAliases = setOf(
                        "companion:companion-stable",
                        "mrp:living-stable",
                    ),
                ),
            )

            val settings = repository.settings.first()
            val saved = settings.devices.single()
            assertEquals(initial.id, saved.id)
            assertEquals(initial.id, settings.selectedDeviceId)
            assertEquals("Cinema", saved.alias)
            assertEquals(setOf("com.apple.TV"), saved.favoriteBundleIds)
            assertEquals(
                setOf("companion:companionstable", "mrp:livingstable"),
                saved.identityAliases,
            )
            assertEquals(
                "private-credential",
                repository.loadCredentials(initial.id)!!.toString(Charsets.UTF_8),
            )
        }

    @Test
    fun `rediscovery by learned alias survives companion address change`() = runTest {
        val initial = discoveredDevice().copy(
            id = "endpoint:living room@10.0.0.4",
            identityAliases = emptySet(),
        )
        repository.upsert(initial)
        repository.upsert(
            discoveredDevice().copy(
                id = "mrp:living-stable",
                identityAliases = setOf("companion:stable"),
            ),
        )

        repository.upsert(
            discoveredDevice().copy(
                id = "companion:stable",
                identityAliases = setOf("companion:stable"),
                services = mapOf(
                    ServiceKind.COMPANION to ResolvedEndpoint(
                        ServiceKind.COMPANION,
                        setOf("10.0.0.44"),
                        49153,
                    ),
                ),
            ),
        )

        val saved = repository.settings.first().devices.single()
        assertEquals(initial.id, saved.id)
        assertEquals(setOf("10.0.0.44"), saved.endpoints.single().addresses)
    }

    @Test
    fun `same display name without identity evidence creates a separate record`() = runTest {
        repository.upsert(discoveredDevice())

        repository.upsert(
            discoveredDevice().copy(
                id = "endpoint:other@10.0.0.99",
                identityAliases = emptySet(),
                services = mapOf(
                    ServiceKind.COMPANION to ResolvedEndpoint(
                        ServiceKind.COMPANION,
                        setOf("10.0.0.99"),
                        49152,
                    ),
                ),
            ),
        )

        assertEquals(2, repository.settings.first().devices.size)
    }

    private fun discoveredDevice() = DiscoveredAppleTv(
        id = "mrp:living",
        name = "Living Room",
        model = "AppleTV6,2",
        online = true,
        services = mapOf(
            ServiceKind.COMPANION to ResolvedEndpoint(
                ServiceKind.COMPANION,
                setOf("10.0.0.4"),
                49152,
            ),
        ),
        properties = emptyMap(),
        identityAliases = setOf("mrp:living"),
    )
}

private class FakeDeviceStoreDataSource(
    initial: DeviceStore = DeviceStore.getDefaultInstance(),
) : DeviceStoreDataSource {
    private val state = MutableStateFlow(initial)
    val current: DeviceStore get() = state.value

    override val data: Flow<DeviceStore> = state

    override suspend fun update(transform: (DeviceStore) -> DeviceStore): DeviceStore {
        state.value = transform(state.value)
        return state.value
    }
}
