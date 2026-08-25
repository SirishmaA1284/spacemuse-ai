package com.spacemuse.ai.core.network

// Base URL for the SpaceMuse AI backend (see backend/README / docs/api).
// The Android app never holds Gemini/product-provider API keys directly —
// it always calls through this backend. See docs/security/security.md.
object ApiConfig {
    // Android emulator loopback to a host machine's localhost — only
    // resolves inside the Android Emulator, not on a real device. On a
    // real device, override this at runtime via the Settings screen
    // (BackendUrlStore / ApiClient.baseUrl) with the dev machine's LAN IP,
    // e.g. http://192.168.1.10:4000/api/v1/ — no rebuild required.
    // Plain HTTP only works because AndroidManifest.xml sets
    // usesCleartextTraffic="true" for this dev-only default — switch to a
    // real HTTPS URL (and drop that flag) before shipping.
    const val BASE_URL = "http://10.0.2.2:4000/api/v1/"
}
