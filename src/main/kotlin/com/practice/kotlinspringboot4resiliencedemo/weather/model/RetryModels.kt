package com.practice.kotlinspringboot4resiliencedemo.weather.model

/**
 * Retry statistics for monitoring and demo visualization.
 * Tracks the retry behavior of @Retryable annotated methods.
 */
data class RetryStatistics(
    val totalAttempts: Int,
    val successfulCalls: Int,
    val failedCalls: Int,
    val retriedCalls: Int,
    val simulatedFailureRate: Double,
    val simulateFailuresEnabled: Boolean,
    val lastCallTimestamp: String?
)

/**
 * Retry information included in API responses.
 * Provides visibility into the retry configuration.
 */
data class RetryInfo(
    val totalAttempts: Int,
    val retryEnabled: Boolean,
    val maxRetries: Int
)
