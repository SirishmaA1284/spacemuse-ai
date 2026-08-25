package com.spacemuse.ai.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

// Single Retrofit instance for the whole app — the backend is the only
// network peer this app talks to (see docs/security/security.md: the app
// never holds Gemini/product-provider keys directly).
//
// baseUrl is mutable at runtime (see BackendUrlStore / the Settings
// screen): a real device can't reach ApiConfig.BASE_URL's emulator-only
// default, so this needs to change without a rebuild. Retrofit itself is
// built once against a fixed placeholder host; baseUrlInterceptor rewrites
// every outgoing request's scheme/host/port/path-prefix to the current
// value instead.
object ApiClient {
    @Volatile
    var baseUrl: String = ApiConfig.BASE_URL

    private val json = Json { ignoreUnknownKeys = true }

    private val baseUrlInterceptor = Interceptor { chain ->
        val target = baseUrl.toHttpUrl()
        val original = chain.request().url
        val redirected = original.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .encodedPath(target.encodedPath.trimEnd('/') + original.encodedPath)
            .build()
        chain.proceed(chain.request().newBuilder().url(redirected).build())
    }

    // OkHttp's 10s defaults are too tight for /rooms/analyze: uploading a
    // full-size JPEG plus the backend's own Gemini vision round-trip can
    // legitimately take longer than that, especially over a home Wi-Fi
    // link — was surfacing as a plain "timeout" on a real device.
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: SpaceMuseApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://backend.invalid/") // placeholder; baseUrlInterceptor swaps in the real target
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SpaceMuseApi::class.java)
    }
}
