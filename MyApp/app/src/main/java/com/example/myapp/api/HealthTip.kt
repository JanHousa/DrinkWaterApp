package com.example.myapp.api

data class HealthTip(
    val food_name: String,
    val nf_calories: Double,
    val nf_water_grams: Double,
    val serving_weight_grams: Double,
    val serving_qty: Double,
    val serving_unit: String
) {
    val title: String
        get() = "$serving_qty $serving_unit $food_name"
    
    val description: String
        get() = "Obsah vody: ${nf_water_grams.toInt()}g, Kalorie: ${nf_calories.toInt()}kcal"
} 