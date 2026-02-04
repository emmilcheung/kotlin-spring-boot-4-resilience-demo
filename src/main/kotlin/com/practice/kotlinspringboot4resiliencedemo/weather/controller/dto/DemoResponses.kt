package com.practice.kotlinspringboot4resiliencedemo.weather.controller.dto

import com.practice.kotlinspringboot4resiliencedemo.weather.model.RetryStatistics

/**
 * DTOs specific to controller responses for the demo endpoints.
 */

/**
 * Result of a single API call in rapid-fire demo.
 */
data class CallResult(
    val callNumber: Int,
    val success: Boolean,
    val error: String?,
    val durationMs: Long
)

/**
 * Aggregated result of rapid-fire demo.
 */
data class RapidFireResult(
    val totalCalls: Int,
    val successCount: Int,
    val failureCount: Int,
    val calls: List<CallResult>,
    val retryStatistics: RetryStatistics
)
