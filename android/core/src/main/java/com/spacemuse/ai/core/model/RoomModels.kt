package com.spacemuse.ai.core.model

import kotlinx.serialization.Serializable

// Mirrors backend/src/ai/schemas/intentResult.schema.ts — keep both in
// sync manually until a shared schema-generation step exists (tracked in
// docs/development/technical-debt.md).
enum class Intent {
    REARRANGE, ORGANIZE, ADD_OBJECT, REMOVE_OBJECT, REPLACE_OBJECT,
    CHANGE_COLOR, CHANGE_STYLE, IMPROVE_LIGHTING, IMPROVE_STORAGE,
    SHOP_FOR_PRODUCT, TRY_PRODUCT, COMPARE_PRODUCTS, OPTIMIZE_BUDGET,
    VISUALIZE_CHANGE, DESIGN_ROOM, DESIGN_MULTIPLE_ROOMS, FULL_REDESIGN,
    ASK_QUESTION
}

@Serializable
data class IntentResult(
    val intent: String, // kept as String for lenient decoding; validate against Intent.values() at the call site
    val confidence: Float,
    val entities: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val source: String
)

// MeasurementSource distinguishes sensor-verified data from AI estimates —
// spec section 9. Never render an ESTIMATED value as if it were MEASURED.
enum class MeasurementSource { MEASURED, ESTIMATED }

@Serializable
data class RoomObjectModel(
    val id: String,
    val type: String,
    val classification: String? = null, // KEEP | MOVE | REMOVE | REPLACE | MODIFY
    val widthCm: Float? = null,
    val heightCm: Float? = null,
    val depthCm: Float? = null,
    val confidence: Float? = null,
    val measurementSource: String
)

@Serializable
data class RoomMeasurementModel(
    val label: String,
    val valueCm: Float,
    val measurementSource: String,
    val confidence: Float? = null
)

// Mirrors backend/src/ai/schemas/roomAnalysis.schema.ts — keep both in sync
// manually until a shared schema-generation step exists (tracked in
// docs/development/technical-debt.md).
@Serializable
data class RoomAnalyzeRequest(
    val imageBase64: String? = null,
    val note: String? = null
)

@Serializable
data class RoomAnalysis(
    val roomType: String,
    val objects: List<RoomObjectModel>,
    val measurements: List<RoomMeasurementModel>,
    val summary: String,
    val source: String // "gemini" | "demo" — demo must be shown as such in the UI, never as a real scan
)

// Mirrors RoomCreateSchema in backend/src/api/v1/routes/router.ts (Milestone
// 3 of Phase 7 — AR-measured room persistence). measuredMeasurements must
// all carry measurementSource == "MEASURED"; the backend rejects anything
// else with a 400.
@Serializable
data class RoomCreateRequest(
    val imageBase64: String,
    val roomType: String? = null,
    val measuredMeasurements: List<RoomMeasurementModel> = emptyList()
)

@Serializable
data class CreateRoomResponse(
    val roomId: String,
    val analysis: RoomAnalysis
)
