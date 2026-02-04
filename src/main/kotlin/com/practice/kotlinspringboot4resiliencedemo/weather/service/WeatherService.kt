package com.practice.kotlinspringboot4resiliencedemo.weather.service

import com.practice.kotlinspringboot4resiliencedemo.weather.dto.*
import com.practice.kotlinspringboot4resiliencedemo.weather.exception.SimulatedFailureException
import com.practice.kotlinspringboot4resiliencedemo.weather.exception.WeatherApiException
import com.practice.kotlinspringboot4resiliencedemo.weather.model.RetryInfo
import com.practice.kotlinspringboot4resiliencedemo.weather.model.RetryStatistics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Service to interact with Hong Kong Observatory Open Data API.
 * 
 * This service demonstrates Spring Framework 7's built-in resilience features:
 * 1. @Retryable annotation for automatic retry with exponential backoff
 * 2. RestClient usage for HTTP calls
 * 3. Simulated random failures to visualize retry behavior
 * 
 * Spring Framework 7 native resilience (since 7.0):
 * - Replaces the need for spring-retry or resilience4j
 * - Supports exponential backoff with multiplier and jitter
 * - Native reactive support (Mono/Flux)
 * - MethodRetryEvent for monitoring
 * 
 * Future enhancements ready:
 * - Short TTL cache support (structure prepared for caching layer)
 */
@Service
class WeatherService(
    private val restClientBuilder: RestClient.Builder,
    @Value("\${weather.api.base-url:https://data.weather.gov.hk/weatherAPI/opendata}")
    private val weatherApiBaseUrl: String,
    @Value("\${weather.api.resource:weather.php}")
    private val weatherApiResource: String,
    @Value("\${weather.api.failure-rate:0.3}")
    private val simulatedFailureRate: Double,
    @Value("\${weather.api.simulate-failures:true}")
    private val simulateFailures: Boolean
) {
    
    private val logger = LoggerFactory.getLogger(WeatherService::class.java)
    
    // Metrics tracking for demo purposes
    private val totalAttempts = AtomicInteger(0)
    private val successfulCalls = AtomicInteger(0)
    private val failedCalls = AtomicInteger(0)
    private val retriedCalls = AtomicInteger(0)
    private val lastCallTimestamp = AtomicLong(0)
    
    private val restClient: RestClient = restClientBuilder
        .baseUrl(weatherApiBaseUrl)
        .build()
    
    /**
     * Fetch current weather data with automatic retry on failure.
     * 
     * Spring Framework 7 @Retryable configuration:
     * - maxRetries: 3 (total 4 attempts including initial)
     * - delay: 500ms initial delay
     * - multiplier: 2.0 for exponential backoff (500ms -> 1s -> 2s)
     * - jitter: 100ms random variation to prevent thundering herd
     * - maxDelay: 5000ms cap
     */
    @Retryable(
        maxRetries = 3,
        delay = 500,
        multiplier = 2.0,
        jitter = 100,
        maxDelay = 5000,
        timeUnit = TimeUnit.MILLISECONDS
    )
    fun getCurrentWeather(lang: WeatherLang = WeatherLang.ENGLISH): WeatherApiResponse<CurrentWeatherResponse> {
        return fetchWeatherData(
            dataType = WeatherDataType.CURRENT,
            lang = lang,
            responseType = CurrentWeatherResponse::class.java
        )
    }
    
    /**
     * Fetch local weather forecast with automatic retry on failure.
     */
    @Retryable(
        maxRetries = 3,
        delay = 500,
        multiplier = 2.0,
        jitter = 100,
        maxDelay = 5000,
        timeUnit = TimeUnit.MILLISECONDS
    )
    fun getLocalForecast(lang: WeatherLang = WeatherLang.ENGLISH): WeatherApiResponse<LocalForecastResponse> {
        return fetchWeatherData(
            dataType = WeatherDataType.LOCAL_FORECAST,
            lang = lang,
            responseType = LocalForecastResponse::class.java
        )
    }
    
    /**
     * Fetch 9-day forecast with automatic retry on failure.
     */
    @Retryable(
        maxRetries = 3,
        delay = 500,
        multiplier = 2.0,
        jitter = 100,
        maxDelay = 5000,
        timeUnit = TimeUnit.MILLISECONDS
    )
    fun getNineDayForecast(lang: WeatherLang = WeatherLang.ENGLISH): WeatherApiResponse<NineDayForecastResponse> {
        return fetchWeatherData(
            dataType = WeatherDataType.NINE_DAY_FORECAST,
            lang = lang,
            responseType = NineDayForecastResponse::class.java
        )
    }
    
    /**
     * Get retry statistics for monitoring
     */
    fun getRetryStatistics(): RetryStatistics {
        return RetryStatistics(
            totalAttempts = totalAttempts.get(),
            successfulCalls = successfulCalls.get(),
            failedCalls = failedCalls.get(),
            retriedCalls = retriedCalls.get(),
            simulatedFailureRate = simulatedFailureRate,
            simulateFailuresEnabled = simulateFailures,
            lastCallTimestamp = if (lastCallTimestamp.get() > 0) 
                Instant.ofEpochMilli(lastCallTimestamp.get()).toString() 
            else null
        )
    }
    
    /**
     * Reset statistics (for demo purposes)
     */
    fun resetStatistics() {
        totalAttempts.set(0)
        successfulCalls.set(0)
        failedCalls.set(0)
        retriedCalls.set(0)
        logger.info("Statistics reset")
    }
    
    /**
     * Core method that fetches weather data from HK Observatory API.
     * Includes simulated failure for demonstrating retry behavior.
     */
    private fun <T : Any> fetchWeatherData(
        dataType: WeatherDataType,
        lang: WeatherLang,
        responseType: Class<T>
    ): WeatherApiResponse<T> {
        val fetchedAt = Instant.now().toString()
        lastCallTimestamp.set(System.currentTimeMillis())
        totalAttempts.incrementAndGet()
        
        // Simulate random failure to demonstrate retry behavior
        if (simulateFailures && Random.nextDouble() < simulatedFailureRate) {
            failedCalls.incrementAndGet()
            retriedCalls.incrementAndGet()
            logger.warn("⚠️ Simulating random failure for demo (rate: $simulatedFailureRate)")
            throw SimulatedFailureException("Simulated failure to demonstrate @Retryable behavior")
        }
        
        logger.info("📡 Fetching weather data: dataType=${dataType.value}, lang=${lang.value}")
        
        try {
            val response = restClient.get()
                .uri(weatherApiResource) { uriBuilder ->
                    uriBuilder
                        .queryParam("dataType", dataType.value)
                        .queryParam("lang", lang.value)
                        .build()
                }
                .retrieve()
                .body(responseType)
            
            if (response == null) {
                failedCalls.incrementAndGet()
                throw WeatherApiException("Empty response from weather API")
            }
            
            successfulCalls.incrementAndGet()
            logger.info("✅ Successfully fetched weather data: dataType=${dataType.value}")
            
            return WeatherApiResponse(
                data = response,
                dataType = dataType.value,
                lang = lang.value,
                fetchedAt = fetchedAt,
                fromFallback = false,
                retryInfo = RetryInfo(
                    totalAttempts = totalAttempts.get(),
                    retryEnabled = true,
                    maxRetries = 3
                )
            )
        } catch (e: SimulatedFailureException) {
            throw e // Re-throw to trigger retry
        } catch (e: Exception) {
            failedCalls.incrementAndGet()
            logger.error("❌ Error fetching weather data: ${e.message}")
            throw WeatherApiException("Failed to fetch weather data: ${e.message}")
        }
    }
}
