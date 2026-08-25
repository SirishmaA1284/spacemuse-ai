package com.spacemuse.ai.core.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val Context.settingsDataStore by preferencesDataStore(name = "spacemuse_settings")

// Persists a user-overridable backend base URL. ApiConfig.BASE_URL's
// 10.0.2.2 default only resolves inside the Android Emulator — a real
// device needs its own value (typically the dev machine's LAN IP), set via
// the in-app Settings screen without requiring a rebuild.
object BackendUrlStore {
    private val BACKEND_URL_KEY = stringPreferencesKey("backend_base_url")

    // Always resolve through applicationContext: creating this DataStore
    // delegate against two different Context instances (e.g. an Activity
    // and the Application) in the same process throws
    // "multiple DataStores active for the same file".
    private fun store(context: Context) = context.applicationContext.settingsDataStore

    fun baseUrlFlow(context: Context): Flow<String> =
        store(context).data.map { prefs -> prefs[BACKEND_URL_KEY] ?: ApiConfig.BASE_URL }

    suspend fun getBaseUrl(context: Context): String =
        store(context).data.first()[BACKEND_URL_KEY] ?: ApiConfig.BASE_URL

    suspend fun setBaseUrl(context: Context, url: String) {
        store(context).edit { prefs -> prefs[BACKEND_URL_KEY] = url }
        ApiClient.baseUrl = url
    }

    fun isValidBaseUrl(url: String): Boolean = url.toHttpUrlOrNull() != null
}
