package io.coreback.relayremote

import android.service.quicksettings.TileService

/** Opens the remote from Quick Settings, the Android counterpart of the iPhone's Control Center remote. */
class RemoteTileService : TileService() {
    override fun onClick() {
        val pendingIntent = RemoteShortcuts.openRemotePendingIntent(this)
        unlockAndRun { startActivityAndCollapse(pendingIntent) }
    }
}
