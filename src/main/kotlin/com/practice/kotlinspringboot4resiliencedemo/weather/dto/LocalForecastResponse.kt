package com.practice.kotlinspringboot4resiliencedemo.weather.dto

/**
 * Local Weather Forecast (flw) response from HK Observatory API.
 * Contains local weather forecast information.
 */
data class LocalForecastResponse(
    val generalSituation: String? = null,
    val tcInfo: String? = null,
    val fireDangerWarning: String? = null,
    val forecastPeriod: String? = null,
    val forecastDesc: String? = null,
    val outlook: String? = null,
    val updateTime: String? = null
)
