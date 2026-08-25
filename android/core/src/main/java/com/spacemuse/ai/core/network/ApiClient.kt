package com.spacemuse.ai.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

// Single Retrofit instance for the whole app — the backend is the only
// network peer this app talks to (see docs/security/security.md: the app
// never holds Gemini/product-provider keys directly).
object ApiClient {
    private val json = Json { ignoreUnknownKeys = true }

    val api: SpaceMuseApi by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SpaceMuseApi::class.java)
    }
}
