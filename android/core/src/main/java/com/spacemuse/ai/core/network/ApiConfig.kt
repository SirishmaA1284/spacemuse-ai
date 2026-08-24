package com.spacemuse.ai.core.network

// Base URL for the SpaceMuse AI backend (see backend/README / docs/api).
// The Android app never holds Gemini/product-provider API keys directly —
// it always calls through this backend. See docs/security/security.md.
object ApiConfig {
    // Android emulator loopback to a host machine's localhost.
    // Override via BuildConfig/DI for physical devices or deployed backends.
    const val BASE_URL = "http://10.0.2.2:4000/api/v1/"
}
