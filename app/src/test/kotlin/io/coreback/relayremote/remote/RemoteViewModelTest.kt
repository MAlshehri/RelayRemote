package io.coreback.relayremote.remote

import io.coreback.relayremote.data.CredentialCipher
import io.coreback.relayremote.data.DeviceRepository
import io.coreback.relayremote.data.DeviceStoreDataSource
import io.coreback.relayremote.data.SecretKeyProvider
import io.coreback.relayremote.data.proto.DeviceStore
import io.coreback.relayremote.devices.AppleTvDiscovery
import io.coreback.relayremote.protocol.AppleTvAccount
import io.coreback.relayremote.protocol.AppleTvApp
import io.coreback.relayremote.protocol.AppleTvClient
import io.coreback.relayremote.protocol.AppleTvCommand
import io.coreback.relayremote.protocol.Capability
import io.coreback.relayremote.protocol.CapabilitySet
import io.coreback.relayremote.protocol.ConnectionState
import io.coreback.relayremote.protocol.KeyboardOperation
import io.coreback.relayremote.protocol.NowPlaying
import io.coreback.relayremote.protocol.PairingCredentials
import io.coreback.relayremote.protocol.PowerState
import io.coreback.relayremote.protocol.ScanDirection
import io.coreback.relayremote.protocol.TouchGesture
import io.coreback.relayremote.protocol.UnsupportedCapabilityException
import io.coreback.relayremote.protocol.discovery.DiscoveredAppleTv
import io.coreback.relayremote.protocol.discovery.ResolvedEndpoint
import io.coreback.relayremote.protocol.discovery.ServiceKind
import javax.crypto.KeyGenerator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selected paired device resolves live alias and reconnects after discovery update`() =
        runTest(dispatcher) {
            val source = FakeDeviceStoreDataSource()
            val repository = repository(source)
            val saved = discoveredDevice(
                id = "endpoint:living@10.0.0.4",
                address = "10.0.0.4",
            )
            repository.upsert(saved)
            repository.select(saved.id)
            repository.saveCredentials(saved.id, "credential".toByteArray())
            val live = discoveredDevice(
                id = "companion:stable",
                address = "10.0.0.44",
            )
            val discovery = FakeAppleTvDiscovery(listOf(live))
            val client = FakeAppleTvClient()
            val requestedIds = mutableListOf<String>()

            val viewModel = RemoteViewModel(discovery, repository) { id ->
                requestedIds += id
                client
            }
            advanceUntilIdle()

            assertEquals(listOf(saved.id), requestedIds)
            assertEquals(1, client.connectCalls)
            assertTrue(viewModel.uiState.value.devices.single().online)
            assertTrue(viewModel.uiState.value.devices.single().paired)
            assertEquals(0, client.pairCalls)

            client.mutableConnectionState.value = ConnectionState.Offline
            discovery.mutableDevices.value = listOf(live.copy(name = "Living Room TV"))
            advanceUntilIdle()

            assertEquals(2, client.connectCalls)
        }

    @Test
    fun `pairing closes only after credentials are saved and shows confirmation`() =
        runTest(dispatcher) {
            val source = FakeDeviceStoreDataSource()
            val repository = repository(source)
            val device = discoveredDevice()
            repository.upsert(device)
            repository.select(device.id)
            val discovery = FakeAppleTvDiscovery(listOf(device))
            val client = FakeAppleTvClient(
                onPair = {
                    repository.saveCredentials(device.id, "credential".toByteArray())
                },
            )
            val viewModel = RemoteViewModel(discovery, repository) { client }
            advanceUntilIdle()

            viewModel.startPairing(device.id)
            advanceUntilIdle()
            viewModel.updatePin("1234")
            viewModel.submitPin()
            runCurrent()

            assertEquals(RemoteSheet.NONE, viewModel.uiState.value.sheet)
            assertEquals("Paired and saved", viewModel.uiState.value.confirmation)
            assertTrue(repository.settings.value().devices.single().paired)

            advanceTimeBy(3_001)
            runCurrent()
            assertEquals(null, viewModel.uiState.value.confirmation)
        }

    @Test
    fun `pair submission is guarded while credential exchange is in progress`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        val discovery = FakeAppleTvDiscovery(listOf(device))
        val client = FakeAppleTvClient(onPair = { kotlinx.coroutines.delay(100) })
        val viewModel = RemoteViewModel(discovery, repository) { client }
        advanceUntilIdle()

        viewModel.startPairing(device.id)
        advanceUntilIdle()
        viewModel.updatePin("1234")
        viewModel.submitPin()
        viewModel.submitPin()
        assertTrue(viewModel.uiState.value.pairingInProgress)
        advanceUntilIdle()

        assertEquals(1, client.pairCalls)
    }

    @Test
    fun `refresh restarts discovery and exposes settled progress`() = runTest(dispatcher) {
        val discovery = FakeAppleTvDiscovery(emptyList())
        val viewModel = RemoteViewModel(discovery, repository(FakeDeviceStoreDataSource())) { FakeAppleTvClient() }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.discoveryRefreshing)
        viewModel.refreshDiscovery()
        assertTrue(viewModel.uiState.value.discoveryRefreshing)
        runCurrent()

        assertEquals(1, discovery.stopCalls)
        assertEquals(1, discovery.startCalls)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.discoveryRefreshing)
    }

    @Test
    fun `commands without an active selection are surfaced instead of dropped`() = runTest(dispatcher) {
        val viewModel = RemoteViewModel(
            FakeAppleTvDiscovery(emptyList()),
            repository(FakeDeviceStoreDataSource()),
        ) { FakeAppleTvClient() }
        advanceUntilIdle()

        viewModel.send(AppleTvCommand.HOME)
        advanceUntilIdle()

        assertEquals("Choose an Apple TV before using the remote.", viewModel.uiState.value.error)
    }

    @Test
    fun `typing coalesces rapid edits and replaces the Apple TV field live`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply { keyboardText = "Existing" }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()

        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()
        assertEquals("Existing", viewModel.uiState.value.keyboardText)

        viewModel.updateKeyboardText("R")
        viewModel.updateKeyboardText("Re")
        viewModel.updateKeyboardText("Relay")
        advanceUntilIdle()

        assertEquals(listOf(KeyboardOperation.Replace("Relay")), client.keyboardOperations)
        assertEquals("Relay", viewModel.uiState.value.keyboardText)
        assertEquals(KeyboardSyncState.SYNCED, viewModel.uiState.value.keyboardSyncState)
        assertFalse(viewModel.uiState.value.keyboardDirty)
    }

    @Test
    fun `opening keyboard loads existing TV text before editing`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply { keyboardText = "Already on TV" }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()

        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()

        assertEquals("Already on TV", viewModel.uiState.value.keyboardText)
        assertTrue(viewModel.uiState.value.keyboardSnapshotLoaded)
        assertEquals(KeyboardSyncState.SYNCED, viewModel.uiState.value.keyboardSyncState)
        assertEquals(1, client.keyboardGetCalls)
    }

    @Test
    fun `deletion paste unicode and RTL replace the complete TV value`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply { keyboardText = "Relay Remote" }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()

        viewModel.updateKeyboardText("Relay")
        advanceUntilIdle()
        viewModel.updateKeyboardText("Relay 📺 مرحبا")
        advanceUntilIdle()
        viewModel.updateKeyboardText("")
        advanceUntilIdle()

        assertEquals(
            listOf(
                KeyboardOperation.Replace("Relay"),
                KeyboardOperation.Replace("Relay 📺 مرحبا"),
                KeyboardOperation.Replace(""),
            ),
            client.keyboardOperations,
        )
        assertEquals("", client.keyboardText)
    }

    @Test
    fun `failed live update keeps local text dirty and retry sends it again`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply {
            keyboardText = "Old"
            mutableConnectionState.value = ConnectionState.Connected
            mutableCapabilities.value = CapabilitySet(
                implemented = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
                available = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
            )
        }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()

        client.keyboardSetError = IllegalStateException("TV stopped accepting text")
        viewModel.updateKeyboardText("Keep me")
        advanceUntilIdle()

        assertEquals("Keep me", viewModel.uiState.value.keyboardText)
        assertTrue(viewModel.uiState.value.keyboardDirty)
        assertEquals(KeyboardSyncState.ERROR, viewModel.uiState.value.keyboardSyncState)

        client.keyboardSetError = null
        viewModel.retryKeyboardSync()
        advanceUntilIdle()

        assertEquals("Keep me", client.keyboardText)
        assertFalse(viewModel.uiState.value.keyboardDirty)
        assertEquals(2, client.keyboardOperations.size)
    }

    @Test
    fun `keyboard cannot close with unsent text unless user explicitly closes anyway`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply {
            keyboardText = "TV"
            keyboardSetError = IllegalStateException("offline")
            mutableConnectionState.value = ConnectionState.Connected
            mutableCapabilities.value = CapabilitySet(
                implemented = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
                available = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
            )
        }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()
        viewModel.updateKeyboardText("Unsent")
        advanceUntilIdle()

        viewModel.closeSheet()
        assertEquals(RemoteSheet.KEYBOARD, viewModel.uiState.value.sheet)
        assertEquals("Unsent", viewModel.uiState.value.keyboardText)

        viewModel.closeKeyboardAnyway()
        assertEquals(RemoteSheet.NONE, viewModel.uiState.value.sheet)
        assertEquals("", viewModel.uiState.value.keyboardText)
        assertFalse(viewModel.uiState.value.keyboardDirty)
    }

    @Test
    fun `missing TV text session disables editing without overwriting the field`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply { keyboardText = null }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()

        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()
        viewModel.updateKeyboardText("must not send")
        advanceUntilIdle()

        assertEquals(KeyboardSyncState.UNAVAILABLE, viewModel.uiState.value.keyboardSyncState)
        assertFalse(viewModel.uiState.value.keyboardSnapshotLoaded)
        assertTrue(client.keyboardOperations.isEmpty())
    }

    @Test
    fun `keyboard focus returning on TV automatically reloads the current field`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply { keyboardText = null }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()
        assertEquals(KeyboardSyncState.UNAVAILABLE, viewModel.uiState.value.keyboardSyncState)

        client.keyboardText = "Focus returned"
        client.mutableConnectionState.value = ConnectionState.Connected
        client.mutableCapabilities.value = CapabilitySet(
            implemented = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
            available = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
        )
        advanceUntilIdle()

        assertEquals("Focus returned", viewModel.uiState.value.keyboardText)
        assertEquals(KeyboardSyncState.SYNCED, viewModel.uiState.value.keyboardSyncState)
        assertTrue(viewModel.uiState.value.keyboardSnapshotLoaded)
    }

    @Test
    fun `focus loss disables editing and focus return reloads external TV changes`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply {
            keyboardText = "Before focus loss"
            mutableConnectionState.value = ConnectionState.Connected
            mutableCapabilities.value = CapabilitySet(
                implemented = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
                available = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
            )
        }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()

        client.mutableCapabilities.value = CapabilitySet.None
        advanceUntilIdle()
        assertEquals(KeyboardSyncState.UNAVAILABLE, viewModel.uiState.value.keyboardSyncState)
        assertEquals(
            "Apple TV left the text field. Return to it to continue.",
            viewModel.uiState.value.keyboardError,
        )

        client.keyboardText = "Changed while away"
        client.mutableCapabilities.value = CapabilitySet(
            implemented = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
            available = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
        )
        advanceUntilIdle()

        assertEquals("Changed while away", viewModel.uiState.value.keyboardText)
        assertEquals(KeyboardSyncState.SYNCED, viewModel.uiState.value.keyboardSyncState)
    }

    @Test
    fun `resume reloads clean TV text but preserves unsent local text`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply { keyboardText = "Initial" }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()

        client.keyboardText = "Changed on TV"
        viewModel.onAppResumed()
        advanceUntilIdle()
        assertEquals("Changed on TV", viewModel.uiState.value.keyboardText)

        client.keyboardSetError = IllegalStateException("offline")
        viewModel.updateKeyboardText("Local draft")
        advanceUntilIdle()
        client.keyboardText = "Another TV value"
        viewModel.onAppResumed()
        advanceUntilIdle()

        assertEquals("Local draft", viewModel.uiState.value.keyboardText)
        assertEquals(2, client.keyboardGetCalls)
    }

    @Test
    fun `stale TV reload cannot overwrite a newer local edit`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply { keyboardText = "Initial" }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()

        client.keyboardText = "Stale TV response"
        client.keyboardGetGate = CompletableDeferred()
        viewModel.refreshKeyboardText()
        runCurrent()
        viewModel.updateKeyboardText("New local edit")
        runCurrent()
        client.keyboardGetGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals("New local edit", viewModel.uiState.value.keyboardText)
        assertEquals("New local edit", client.keyboardText)
        assertEquals(KeyboardSyncState.SYNCED, viewModel.uiState.value.keyboardSyncState)
    }

    @Test
    fun `slow sending preserves order and collapses queued typing to the latest value`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply { keyboardText = "" }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()

        client.keyboardSetDelayMillis = 100
        viewModel.updateKeyboardText("R")
        runCurrent()
        viewModel.updateKeyboardText("Re")
        viewModel.updateKeyboardText("Relay")
        advanceUntilIdle()

        assertEquals(
            listOf(KeyboardOperation.Replace("R"), KeyboardOperation.Replace("Relay")),
            client.keyboardOperations,
        )
        assertEquals("Relay", client.keyboardText)
    }

    @Test
    fun `initial read failure requires explicit start fresh before clearing TV`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient().apply {
            keyboardText = "Unknown TV value"
            keyboardGetError = IllegalStateException("read failed")
            mutableConnectionState.value = ConnectionState.Connected
            mutableCapabilities.value = CapabilitySet(
                implemented = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
                available = setOf(io.coreback.relayremote.protocol.Capability.KEYBOARD),
            )
        }
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()

        viewModel.openSheet(RemoteSheet.KEYBOARD)
        advanceUntilIdle()
        viewModel.updateKeyboardText("ignored")
        advanceUntilIdle()
        assertTrue(client.keyboardOperations.isEmpty())

        client.keyboardGetError = null
        viewModel.startFreshKeyboardText()
        advanceUntilIdle()

        assertEquals(listOf(KeyboardOperation.Replace("")), client.keyboardOperations)
        assertEquals("", client.keyboardText)
        assertTrue(viewModel.uiState.value.keyboardSnapshotLoaded)
    }

    @Test
    fun `switching Apple TVs closes keyboard and never sends pending text to the new device`() =
        runTest(dispatcher) {
            val repository = repository(FakeDeviceStoreDataSource())
            val first = discoveredDevice(
                id = "companion:first",
                address = "10.0.0.4",
                identityAliases = setOf("companion:first"),
            )
            val second = discoveredDevice(
                id = "companion:second",
                address = "10.0.0.5",
                identityAliases = setOf("companion:second"),
            )
            repository.upsert(first)
            repository.upsert(second)
            repository.select(first.id)
            val firstClient = FakeAppleTvClient().apply {
                keyboardText = ""
                keyboardSetDelayMillis = 100
            }
            val secondClient = FakeAppleTvClient().apply { keyboardText = "second" }
            val viewModel = RemoteViewModel(
                FakeAppleTvDiscovery(listOf(first, second)),
                repository,
            ) { id -> if (id == first.id) firstClient else secondClient }
            advanceUntilIdle()
            viewModel.openSheet(RemoteSheet.KEYBOARD)
            advanceUntilIdle()

            viewModel.updateKeyboardText("for first only")
            runCurrent()
            viewModel.selectDevice(second.id)
            advanceUntilIdle()

            assertEquals(RemoteSheet.NONE, viewModel.uiState.value.sheet)
            assertTrue(secondClient.keyboardOperations.isEmpty())
            assertEquals(listOf(KeyboardOperation.Replace("for first only")), firstClient.keyboardOperations)
        }

    @Test
    fun `manual reconnect ignores overlapping retry taps`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val client = FakeAppleTvClient()
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()

        viewModel.retryConnection()
        viewModel.retryConnection()
        advanceUntilIdle()

        assertEquals(1, client.connectCalls)
    }

    @Test
    fun `discovery failure is contextual state rather than a global command error`() = runTest(dispatcher) {
        val discovery = FakeAppleTvDiscovery(emptyList())
        val viewModel = RemoteViewModel(discovery, repository(FakeDeviceStoreDataSource())) { FakeAppleTvClient() }
        advanceUntilIdle()

        discovery.mutableFailure.value = "Local network permission is unavailable."
        advanceUntilIdle()

        assertEquals("Local network permission is unavailable.", viewModel.uiState.value.discoveryFailure)
        assertEquals(null, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.discoveryRefreshing)
    }

    @Test
    fun `pairing persistence failure keeps pin sheet open`() = runTest(dispatcher) {
        val source = FakeDeviceStoreDataSource()
        val repository = repository(source)
        val device = discoveredDevice()
        repository.upsert(device)
        repository.select(device.id)
        val discovery = FakeAppleTvDiscovery(listOf(device))
        val client = FakeAppleTvClient(onPair = { error("Credential storage failed") })
        val viewModel = RemoteViewModel(discovery, repository) { client }
        advanceUntilIdle()

        viewModel.startPairing(device.id)
        advanceUntilIdle()
        viewModel.updatePin("1234")
        viewModel.submitPin()
        advanceUntilIdle()

        assertEquals(RemoteSheet.PAIRING, viewModel.uiState.value.sheet)
        assertEquals("1234", viewModel.uiState.value.pin)
        assertEquals("Credential storage failed", viewModel.uiState.value.error)
        assertFalse(repository.settings.value().devices.single().paired)
    }

    private fun connectedDevice(
        client: FakeAppleTvClient,
    ): Pair<DeviceRepository, DiscoveredAppleTv> {
        client.mutableConnectionState.value = ConnectionState.Connected
        client.mutableCapabilities.value = CapabilitySet(
            implemented = Capability.entries.toSet(),
            available = Capability.entries.toSet(),
        )
        return repository(FakeDeviceStoreDataSource()) to discoveredDevice()
    }

    @Test
    fun `touch gestures reach the client in the order they happened`() = runTest(dispatcher) {
        val client = FakeAppleTvClient()
        val (repository, device) = connectedDevice(client)
        repository.upsert(device)
        repository.select(device.id)
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()

        viewModel.touch(TouchGesture.Began(0.5f, 0.5f))
        viewModel.touch(TouchGesture.Moved(0.6f, 0.5f))
        viewModel.touch(TouchGesture.Ended(0.7f, 0.5f))
        viewModel.touch(TouchGesture.HoldStarted)
        viewModel.touch(TouchGesture.HoldEnded)
        advanceUntilIdle()

        assertEquals(
            listOf(
                TouchGesture.Began(0.5f, 0.5f),
                TouchGesture.Moved(0.6f, 0.5f),
                TouchGesture.Ended(0.7f, 0.5f),
                TouchGesture.HoldStarted,
                TouchGesture.HoldEnded,
            ),
            client.touchGestures,
        )
    }

    @Test
    fun `failed hold start is reported but its release stays quiet`() = runTest(dispatcher) {
        val client = FakeAppleTvClient()
        val (repository, device) = connectedDevice(client)
        repository.upsert(device)
        repository.select(device.id)
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        client.touchError = UnsupportedCapabilityException(Capability.SELECT)

        viewModel.touch(TouchGesture.HoldEnded)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.error)

        viewModel.touch(TouchGesture.HoldStarted)
        advanceUntilIdle()
        assertEquals("That control is not available right now.", viewModel.uiState.value.error)
    }

    @Test
    fun `failed scan stop stays quiet and failed scan start is reported`() = runTest(dispatcher) {
        val client = FakeAppleTvClient()
        val (repository, device) = connectedDevice(client)
        repository.upsert(device)
        repository.select(device.id)
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        client.scanError = IllegalStateException("boom")

        viewModel.scan(ScanDirection.REWIND, active = false)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.error)

        viewModel.scan(ScanDirection.REWIND, active = true)
        advanceUntilIdle()
        assertEquals("boom", viewModel.uiState.value.error)
        assertEquals(
            listOf(ScanDirection.REWIND to false, ScanDirection.REWIND to true),
            client.scans,
        )
    }

    @Test
    fun `apps load when connected and favorites follow the installed list`() = runTest(dispatcher) {
        val client = FakeAppleTvClient()
        client.apps = listOf(AppleTvApp("com.b", "Beta"), AppleTvApp("com.a", "Alpha"))
        val (repository, device) = connectedDevice(client)
        repository.upsert(device)
        repository.select(device.id)
        repository.setFavorite(device.id, "com.a", true)
        repository.setFavorite(device.id, "com.removed", true)

        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()

        assertEquals(listOf(AppleTvApp("com.a", "Alpha")), viewModel.uiState.value.favoriteApps)
    }

    @Test
    fun `switching profile uses the chosen account and closes the sheet`() = runTest(dispatcher) {
        val client = FakeAppleTvClient()
        client.accounts = listOf(AppleTvAccount("1", "Sam"), AppleTvAccount("2", "Noor"))
        val (repository, device) = connectedDevice(client)
        repository.upsert(device)
        repository.select(device.id)
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()

        viewModel.openSheet(RemoteSheet.MORE)
        advanceUntilIdle()
        assertEquals(client.accounts, viewModel.uiState.value.accounts)

        viewModel.switchAccount("2")
        advanceUntilIdle()

        assertEquals(listOf("2"), client.switchedAccounts)
        assertEquals(RemoteSheet.NONE, viewModel.uiState.value.sheet)
    }

    @Test
    fun `muted state follows the client`() = runTest(dispatcher) {
        val client = FakeAppleTvClient()
        val (repository, device) = connectedDevice(client)
        repository.upsert(device)
        repository.select(device.id)
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()

        client.mutableMuted.value = true
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.muted)
    }

    @Test
    fun `directional buttons setting persists`() = runTest(dispatcher) {
        val repository = repository(FakeDeviceStoreDataSource())
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(emptyList()), repository) { FakeAppleTvClient() }
        advanceUntilIdle()

        viewModel.setDirectionalButtons(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.directionalButtons)
        assertTrue(repository.settings.value().directionalButtons)
    }

    @Test
    fun `reconnecting clears a stale command error`() = runTest(dispatcher) {
        val client = FakeAppleTvClient()
        val (repository, device) = connectedDevice(client)
        repository.upsert(device)
        repository.select(device.id)
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(listOf(device)), repository) { client }
        advanceUntilIdle()
        client.mutableConnectionState.value = ConnectionState.Reconnecting(1)
        advanceUntilIdle()
        client.scanError = IllegalStateException("boom")
        viewModel.scan(ScanDirection.REWIND, active = true)
        advanceUntilIdle()
        assertEquals("boom", viewModel.uiState.value.error)

        client.mutableConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `paired device missing from discovery remains paired and offline`() = runTest(dispatcher) {
        val source = FakeDeviceStoreDataSource()
        val repository = repository(source)
        val saved = discoveredDevice()
        repository.upsert(saved)
        repository.select(saved.id)
        repository.saveCredentials(saved.id, "credential".toByteArray())
        val client = FakeAppleTvClient()
        val viewModel = RemoteViewModel(FakeAppleTvDiscovery(emptyList()), repository) { client }

        advanceUntilIdle()

        val item = viewModel.uiState.value.devices.single()
        assertTrue(item.paired)
        assertFalse(item.online)
        assertEquals(ConnectionState.Offline, viewModel.uiState.value.connectionState)
        assertEquals(null, viewModel.uiState.value.error)
    }

    private fun repository(source: FakeDeviceStoreDataSource): DeviceRepository {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return DeviceRepository(source, CredentialCipher(SecretKeyProvider { key }))
    }

    private fun discoveredDevice(
        id: String = "companion:stable",
        address: String = "10.0.0.4",
        identityAliases: Set<String> = setOf("companion:stable"),
    ) = DiscoveredAppleTv(
        id = id,
        name = "Living Room",
        model = "AppleTV14,1",
        online = true,
        services = mapOf(
            ServiceKind.COMPANION to ResolvedEndpoint(
                ServiceKind.COMPANION,
                setOf(address),
                49152,
            ),
        ),
        properties = emptyMap(),
        identityAliases = identityAliases,
    )
}

private class FakeAppleTvDiscovery(
    initial: List<DiscoveredAppleTv>,
) : AppleTvDiscovery {
    val mutableDevices = MutableStateFlow(initial)
    override val devices: StateFlow<List<DiscoveredAppleTv>> = mutableDevices
    val mutableFailure = MutableStateFlow<String?>(null)
    override val failure: StateFlow<String?> = mutableFailure
    var startCalls = 0
    var stopCalls = 0
    override fun start() { startCalls += 1 }
    override fun stop() { stopCalls += 1 }
}

private class FakeAppleTvClient(
    private val onPair: suspend (String) -> Unit = {},
) : AppleTvClient {
    val mutableConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Offline)
    override val connectionState: StateFlow<ConnectionState> = mutableConnectionState
    val mutableCapabilities = MutableStateFlow(CapabilitySet.None)
    override val capabilities: StateFlow<CapabilitySet> = mutableCapabilities
    override val nowPlaying: StateFlow<NowPlaying?> = MutableStateFlow(null)
    override val powerState: StateFlow<PowerState> = MutableStateFlow(PowerState.UNKNOWN)
    val mutableMuted = MutableStateFlow(false)
    override val muted: StateFlow<Boolean> = mutableMuted
    val touchGestures = mutableListOf<TouchGesture>()
    val scans = mutableListOf<Pair<ScanDirection, Boolean>>()
    val switchedAccounts = mutableListOf<String>()
    var touchError: Throwable? = null
    var scanError: Throwable? = null
    var apps: List<AppleTvApp> = emptyList()
    var accounts: List<AppleTvAccount> = emptyList()
    var connectCalls = 0
    var pairCalls = 0
    val keyboardOperations = mutableListOf<KeyboardOperation>()
    var keyboardText: String? = ""
    var keyboardGetCalls = 0
    var keyboardGetError: Throwable? = null
    var keyboardSetError: Throwable? = null
    var keyboardGetGate: CompletableDeferred<Unit>? = null
    var keyboardSetDelayMillis: Long = 0

    override suspend fun connect() {
        connectCalls += 1
    }

    override suspend fun disconnect() = Unit
    override suspend fun beginPairing() = Unit

    override suspend fun pair(pin: String): PairingCredentials {
        pairCalls += 1
        onPair(pin)
        return PairingCredentials(
            clientIdentifier = byteArrayOf(1),
            clientPrivateKey = ByteArray(32),
            clientPublicKey = ByteArray(32),
            accessoryIdentifier = byteArrayOf(2),
            accessoryPublicKey = ByteArray(32),
        )
    }

    override suspend fun sendCommand(command: AppleTvCommand) = Unit
    override suspend fun skipBy(seconds: Int) = Unit
    override suspend fun sendTouchGesture(gesture: TouchGesture) {
        touchGestures += gesture
        touchError?.let { throw it }
    }
    override suspend fun setScan(direction: ScanDirection, active: Boolean) {
        scans += direction to active
        scanError?.let { throw it }
    }
    override suspend fun listApps(): List<AppleTvApp> = apps
    override suspend fun launchApp(bundleId: String) = Unit
    override suspend fun listAccounts(): List<AppleTvAccount> = accounts
    override suspend fun switchAccount(accountId: String) {
        switchedAccounts += accountId
    }
    override suspend fun getKeyboardText(): String? {
        keyboardGetCalls += 1
        keyboardGetError?.let { throw it }
        val snapshot = keyboardText
        keyboardGetGate?.await()
        return snapshot
    }
    override suspend fun setKeyboardText(operation: KeyboardOperation) {
        keyboardOperations += operation
        if (keyboardSetDelayMillis > 0) kotlinx.coroutines.delay(keyboardSetDelayMillis)
        keyboardSetError?.let { throw it }
        keyboardText = when (operation) {
            is KeyboardOperation.Replace -> operation.text
            is KeyboardOperation.Append -> keyboardText.orEmpty() + operation.text
            is KeyboardOperation.Paste -> keyboardText.orEmpty() + operation.text
            KeyboardOperation.Clear -> ""
        }
    }
    override suspend fun wake() = Unit
    override suspend fun sleep() = Unit
    override suspend fun volumeUp() = Unit
    override suspend fun volumeDown() = Unit
    override suspend fun mute() = Unit
}

private class FakeDeviceStoreDataSource(
    initial: DeviceStore = DeviceStore.getDefaultInstance(),
) : DeviceStoreDataSource {
    private val state = MutableStateFlow(initial)
    override val data: Flow<DeviceStore> = state

    override suspend fun update(transform: (DeviceStore) -> DeviceStore): DeviceStore {
        state.value = transform(state.value)
        return state.value
    }
}

private suspend fun <T> Flow<T>.value(): T = first()
