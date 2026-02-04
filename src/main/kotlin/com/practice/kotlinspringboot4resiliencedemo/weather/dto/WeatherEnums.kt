package com.practice.kotlinspringboot4resiliencedemo.weather.dto

/**
 * Enums for Weather API parameters.
 */

/**
 * Supported data types for the HK Observatory Weather API.
 */
enum class WeatherDataType(val value: String, val description: String) {
    CURRENT("rhrread", "Current Weather Report"),
    LOCAL_FORECAST("flw", "Local Weather Forecast"),
    NINE_DAY_FORECAST("fnd", "9-Day Weather Forecast")
}

/**
 * Supported languages for the Weather API.
 */
enum class WeatherLang(val value: String) {
    ENGLISH("en"),
    TRADITIONAL_CHINESE("tc"),
    SIMPLIFIED_CHINESE("sc")
}
