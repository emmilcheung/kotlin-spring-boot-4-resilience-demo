package com.practice.kotlinspringboot4resiliencedemo.weather.dto

import com.practice.kotlinspringboot4resiliencedemo.weather.model.RetryInfo

/**
 * Generic wrapper for Weather API responses.
 * Adds metadata about the API call and retry status.
 */
data class WeatherApiResponse<T>(
    val data: T?,
    val dataType: String,
    val lang: String,
    val fetchedAt: String,
    val fromFallback: Boolean = false,
    val error: String? = null,
    val retryInfo: RetryInfo? = null
)
