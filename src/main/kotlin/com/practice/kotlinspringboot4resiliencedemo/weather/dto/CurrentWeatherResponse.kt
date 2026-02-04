package com.practice.kotlinspringboot4resiliencedemo.weather.dto

/**
 * Current Weather Report (rhrread) response from HK Observatory API.
 * Contains real-time weather readings from various stations.
 */
data class CurrentWeatherResponse(
    val temperature: TemperatureInfo? = null,
    val humidity: HumidityInfo? = null,
    val rainfall: RainfallInfo? = null,
    val uvindex: UvIndexInfo? = null,
    val icon: List<Int> = emptyList(),
    val iconUpdateTime: String? = null,
    val warningMessage: String? = null,
    val updateTime: String? = null
)
