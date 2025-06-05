package com.example.myapp.api

import retrofit2.http.GET
import retrofit2.http.Query

interface HealthApi {
    @GET("natural/nutrients")
    suspend fun getHealthTips(
        @Query("query") query: String = "1 cup water"
    ): SearchResponse
}

data class SearchResponse(
    val foods: List<HealthTip>
) 