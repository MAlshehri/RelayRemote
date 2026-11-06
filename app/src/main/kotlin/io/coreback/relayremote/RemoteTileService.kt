package io.coreback.relayremote

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService

/** Opens the remote from Quick Settings, the Android counterpart of the iPhone's Control Center remote. */
class RemoteTileService : TileService() {
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        unlockAndRun { startActivityAndCollapse(pendingIntent) }
    }
}
