package com.practice.kotlinspringboot4resiliencedemo.weather.dto

/**
 * 9-Day Weather Forecast (fnd) response from HK Observatory API.
 * Contains extended weather forecast for the next 9 days.
 */
data class NineDayForecastResponse(
    val generalSituation: String? = null,
    val weatherForecast: List<DayForecast> = emptyList(),
    val updateTime: String? = null
)

/**
 * Individual day forecast within the 9-day forecast.
 */
data class DayForecast(
    val forecastDate: String? = null,
    val week: String? = null,
    val forecastWind: String? = null,
    val forecastWeather: String? = null,
    val forecastMaxtemp: TempValue? = null,
    val forecastMintemp: TempValue? = null,
    val forecastMaxrh: RhValue? = null,
    val forecastMinrh: RhValue? = null,
    val ForecastIcon: Int? = null,
    val PSR: String? = null
)
