package com.spacemuse.ai

import android.app.Application
import com.spacemuse.ai.core.network.ApiClient
import com.spacemuse.ai.core.network.BackendUrlStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SpaceMuseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // One-time synchronous read of a single small local preferences
        // file so ApiClient.baseUrl reflects any Settings-screen override
        // from a previous session before the first network call can
        // happen — negligible added cold-start cost.
        ApiClient.baseUrl = runBlocking { BackendUrlStore.getBaseUrl(this@SpaceMuseApp) }
    }
}
