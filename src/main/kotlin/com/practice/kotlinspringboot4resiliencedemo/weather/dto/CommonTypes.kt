package com.practice.kotlinspringboot4resiliencedemo.weather.dto

/**
 * Common data types shared across weather API responses.
 */

data class TemperatureData(
    val place: String? = null,
    val value: Int? = null,
    val unit: String? = null
)

data class TemperatureInfo(
    val data: List<TemperatureData> = emptyList(),
    val recordTime: String? = null
)

data class HumidityData(
    val place: String? = null,
    val value: Int? = null,
    val unit: String? = null
)

data class HumidityInfo(
    val data: List<HumidityData> = emptyList(),
    val recordTime: String? = null
)

data class RainfallData(
    val place: String? = null,
    val max: Int? = null,
    val unit: String? = null,
    val main: String? = null
)

data class RainfallInfo(
    val data: List<RainfallData> = emptyList(),
    val startTime: String? = null,
    val endTime: String? = null
)

data class UvIndexData(
    val place: String? = null,
    val value: Double? = null,
    val desc: String? = null
)

data class UvIndexInfo(
    val data: List<UvIndexData> = emptyList(),
    val recordDesc: String? = null
)

data class TempValue(
    val value: Int? = null,
    val unit: String? = null
)

data class RhValue(
    val value: Int? = null,
    val unit: String? = null
)
