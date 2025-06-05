package com.example.myapp.api

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("forecast")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double = 50.7677,  // Liberec
        @Query("lon") lon: Double = 15.0594,  // Liberec
        @Query("units") units: String = "metric"
    ): WeatherData
} 