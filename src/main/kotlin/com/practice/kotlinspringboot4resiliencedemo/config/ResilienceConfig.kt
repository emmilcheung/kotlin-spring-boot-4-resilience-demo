package com.practice.kotlinspringboot4resiliencedemo.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.resilience.retry.MethodRetryEvent

/**
 * Configuration for Spring Framework 7's built-in resilience support.
 * 
 * Spring Framework 7 introduced native resilience annotations:
 * - @Retryable: Automatically retry method invocations on failure
 * - @ConcurrencyLimit: Limit concurrent method invocations
 * 
 * This replaces the need for third-party libraries like Resilience4j or Spring Retry.
 * 
 * Key features:
 * - Built-in retry with configurable attempts, delay, backoff
 * - Supports exponential backoff with multiplier
 * - Jitter support to prevent thundering herd
 * - Exception filtering (includes/excludes)
 * - Native reactive support (Mono/Flux)
 * 
 * @see org.springframework.resilience.annotation.Retryable
 * @see org.springframework.resilience.annotation.ConcurrencyLimit
 */
@Configuration
@EnableResilientMethods(proxyTargetClass = true)
class ResilienceConfig {
    
    private val logger = LoggerFactory.getLogger(ResilienceConfig::class.java)
    
    /**
     * Listen for retry events to track method retry behavior.
     * MethodRetryEvent is published when a @Retryable method encounters a failure.
     */
    @EventListener
    fun onRetryEvent(event: MethodRetryEvent) {
        val status = if (event.isRetryAborted) "ABORTED" else "RETRYING"
        logger.info(
            "🔄 Retry Event: method={}, status={}, exception={}",
            event.method.name,
            status,
            event.failure?.message ?: "unknown"
        )
    }
}

