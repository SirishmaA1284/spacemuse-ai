package com.spacemuse.ai.ai.gemma

// Interface for on-device Gemma inference (spec section 39, ADR-002).
// No implementation exists yet — this defines the boundary the rest of the
// app should code against so swapping in the real MediaPipe LLM Inference
// implementation later doesn't require touching call sites. Returning a
// result here must never claim on-device reasoning happened until the real
// implementation lands.
interface GemmaLocalReasoner {
    val isModelLoaded: Boolean

    suspend fun loadModel(): Result<Unit>

    suspend fun reasonAboutPreferences(context: String): Result<String>
}

class NotYetImplementedGemmaLocalReasoner : GemmaLocalReasoner {
    override val isModelLoaded: Boolean = false

    override suspend fun loadModel(): Result<Unit> =
        Result.failure(NotImplementedError("Gemma on-device inference is not implemented yet (Phase 6)."))

    override suspend fun reasonAboutPreferences(context: String): Result<String> =
        Result.failure(NotImplementedError("Gemma on-device inference is not implemented yet (Phase 6)."))
}
