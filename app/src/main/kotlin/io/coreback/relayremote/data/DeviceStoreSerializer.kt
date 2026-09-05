package io.coreback.relayremote.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import io.coreback.relayremote.data.proto.DeviceStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow

object DeviceStoreSerializer : Serializer<DeviceStore> {
    override val defaultValue: DeviceStore = DeviceStore.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): DeviceStore =
        try {
            DeviceStore.parseFrom(input)
        } catch (error: InvalidProtocolBufferException) {
            throw CorruptionException("Unable to read Relay Remote device metadata", error)
        }

    override suspend fun writeTo(t: DeviceStore, output: OutputStream) {
        t.writeTo(output)
    }
}

interface DeviceStoreDataSource {
    val data: Flow<DeviceStore>
    suspend fun update(transform: (DeviceStore) -> DeviceStore): DeviceStore
}

class ProtoDeviceStoreDataSource(
    private val store: DataStore<DeviceStore>,
) : DeviceStoreDataSource {
    override val data: Flow<DeviceStore> = store.data

    override suspend fun update(transform: (DeviceStore) -> DeviceStore): DeviceStore =
        store.updateData(transform)
}
