package com.example.myapp.api

data class WeatherData(
    val list: List<ForecastItem>,
    val city: City
)

data class ForecastItem(
    val main: Main,
    val weather: List<Weather>,
    val dt_txt: String
)

data class Main(
    val temp: Float,
    val feels_like: Float,
    val humidity: Int
)

data class Weather(
    val description: String,
    val icon: String
)

data class City(
    val name: String,
    val country: String
) 