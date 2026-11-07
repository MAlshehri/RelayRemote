package io.coreback.relayremote

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.TileService
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteShortcutsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun widgetIsOfferedToLaunchers() {
        val provider = context.getSystemService(AppWidgetManager::class.java)
            .getInstalledProvidersForPackage(context.packageName, null)
            .single()

        assertEquals(ComponentName(context, RemoteWidgetProvider::class.java), provider.provider)
        assertEquals(R.layout.widget_remote_wide, provider.previewLayout)
    }

    @Test
    fun widgetLayoutsRenderAsRemoteViews() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            listOf(R.layout.widget_remote_icon, R.layout.widget_remote_wide).forEach { layout ->
                val parent = FrameLayout(context)
                val view = RemoteViews(context.packageName, layout).apply(context, parent)
                assertNotNull(view.findViewById(android.R.id.background))
            }
        }
    }

    @Test
    fun quickSettingsTileIsOffered() {
        val tiles = context.packageManager.queryIntentServices(
            Intent(TileService.ACTION_QS_TILE).setPackage(context.packageName),
            PackageManager.ResolveInfoFlags.of(0),
        )

        assertTrue(tiles.any { it.serviceInfo.name == RemoteTileService::class.java.name })
    }

    @Test
    fun shortcutsTargetTheRemote() {
        val pendingIntent = RemoteShortcuts.openRemotePendingIntent(context)

        assertNotNull(pendingIntent)
        assertTrue(pendingIntent.isImmutable)
        assertTrue(pendingIntent.isActivity)
    }
}
