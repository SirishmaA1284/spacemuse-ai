package com.spacemuse.ai.core.network

import com.spacemuse.ai.core.model.ProductSearchResponse
import com.spacemuse.ai.core.model.RoomAnalysis
import com.spacemuse.ai.core.model.RoomAnalyzeRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// Mirrors backend/src/api/v1/routes/router.ts — see docs/api/api-specification.md.
interface SpaceMuseApi {
    @POST("rooms/analyze")
    suspend fun analyzeRoom(@Body request: RoomAnalyzeRequest): RoomAnalysis

    @GET("products/search")
    suspend fun searchProducts(@Query("q") query: String): ProductSearchResponse
}
