package io.coreback.relayremote

import android.app.PendingIntent
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.widget.Toast

/** Shared wiring for the ways to open the remote from outside the app. */
object RemoteShortcuts {
    fun openRemotePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Asks the launcher to place the widget, or explains how to add it by hand. */
    fun requestHomeScreenWidget(context: Context) {
        val provider = ComponentName(context, RemoteWidgetProvider::class.java)
        val requested = context.getSystemService(AppWidgetManager::class.java)
            .requestPinAppWidget(provider, null, null)
        if (!requested) toast(context, R.string.widget_add_manually)
    }

    /** Asks System UI to add the tile, which needs the app to be in the foreground. */
    fun requestQuickSettingsTile(context: Context) {
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(context, RemoteTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tile_remote),
            context.mainExecutor,
        ) { result ->
            toast(
                context,
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added
                    else -> R.string.tile_add_manually
                },
            )
        }
    }

    private fun toast(context: Context, message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
