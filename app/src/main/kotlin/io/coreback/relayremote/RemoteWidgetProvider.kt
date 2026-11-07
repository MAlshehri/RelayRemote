package io.coreback.relayremote

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.SizeF
import android.widget.RemoteViews

/** Home screen widget that opens the remote. Shows a label when it is wide enough for one. */
class RemoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = RemoteViews(
            mapOf(
                SizeF(0f, 0f) to views(context, R.layout.widget_remote_icon),
                SizeF(WIDE_MIN_WIDTH_DP, 0f) to views(context, R.layout.widget_remote_wide),
            ),
        )
        manager.updateAppWidget(appWidgetIds, views)
    }

    private fun views(context: Context, layout: Int) = RemoteViews(context.packageName, layout).apply {
        setOnClickPendingIntent(android.R.id.background, RemoteShortcuts.openRemotePendingIntent(context))
    }

    private companion object {
        const val WIDE_MIN_WIDTH_DP = 110f
    }
}
