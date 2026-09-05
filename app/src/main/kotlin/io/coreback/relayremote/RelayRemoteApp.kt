package io.coreback.relayremote

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class RelayRemoteApp : Application(), DefaultLifecycleObserver {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super<Application>.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        container.enterForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        container.enterBackground()
    }
}
