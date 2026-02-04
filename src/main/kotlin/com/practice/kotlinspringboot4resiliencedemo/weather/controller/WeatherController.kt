package com.practice.kotlinspringboot4resiliencedemo.weather.controller

import com.practice.kotlinspringboot4resiliencedemo.weather.controller.dto.CallResult
import com.practice.kotlinspringboot4resiliencedemo.weather.controller.dto.RapidFireResult
import com.practice.kotlinspringboot4resiliencedemo.weather.dto.*
import com.practice.kotlinspringboot4resiliencedemo.weather.model.RetryStatistics
import com.practice.kotlinspringboot4resiliencedemo.weather.service.WeatherService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller providing proxy endpoints for Hong Kong Observatory Weather API.
 * 
 * This controller demonstrates Spring Framework 7's built-in resilience features:
 * 1. @Retryable automatic retry with exponential backoff
 * 2. Proxy pattern for third-party APIs
 * 3. Retry statistics for monitoring
 * 
 * Endpoints:
 * - GET /api/weather/current - Current weather readings
 * - GET /api/weather/forecast - Local weather forecast
 * - GET /api/weather/9day - 9-day weather forecast
 * - GET /api/weather/retry/stats - Retry statistics
 * - POST /api/weather/retry/reset - Reset statistics
 * - GET /api/weather/demo/rapid-fire - Rapid fire demo to trigger retries
 */
@RestController
@RequestMapping("/api/weather")
class WeatherController(
    private val weatherService: WeatherService
) {
    
    /**
     * Get current weather readings from HK Observatory.
     * 
     * @param lang Language code: en (English), tc (Traditional Chinese), sc (Simplified Chinese)
     * @return Current weather data with circuit breaker metadata
     */
    @GetMapping("/current")
    fun getCurrentWeather(
        @RequestParam(defaultValue = "en") lang: String
    ): ResponseEntity<WeatherApiResponse<CurrentWeatherResponse>> {
        val weatherLang = parseLanguage(lang)
        val response = weatherService.getCurrentWeather(weatherLang)
        return buildResponse(response)
    }
    
    /**
     * Get local weather forecast from HK Observatory.
     * 
     * @param lang Language code: en, tc, sc
     * @return Local forecast data with circuit breaker metadata
     */
    @GetMapping("/forecast")
    fun getLocalForecast(
        @RequestParam(defaultValue = "en") lang: String
    ): ResponseEntity<WeatherApiResponse<LocalForecastResponse>> {
        val weatherLang = parseLanguage(lang)
        val response = weatherService.getLocalForecast(weatherLang)
        return buildResponse(response)
    }
    
    /**
     * Get 9-day weather forecast from HK Observatory.
     * 
     * @param lang Language code: en, tc, sc
     * @return 9-day forecast data with circuit breaker metadata
     */
    @GetMapping("/9day")
    fun getNineDayForecast(
        @RequestParam(defaultValue = "en") lang: String
    ): ResponseEntity<WeatherApiResponse<NineDayForecastResponse>> {
        val weatherLang = parseLanguage(lang)
        val response = weatherService.getNineDayForecast(weatherLang)
        return buildResponse(response)
    }
    
    /**
     * Get retry statistics for monitoring.
     * Shows total attempts, successes, failures, and retry counts.
     */
    @GetMapping("/retry/stats")
    fun getRetryStatistics(): ResponseEntity<RetryStatistics> {
        return ResponseEntity.ok(weatherService.getRetryStatistics())
    }
    
    /**
     * Reset retry statistics.
     * For demo purposes - allows resetting counters.
     */
    @PostMapping("/retry/reset")
    fun resetRetryStatistics(): ResponseEntity<Map<String, String>> {
        weatherService.resetStatistics()
        return ResponseEntity.ok(
            mapOf(
                "message" to "Retry statistics reset successfully"
            )
        )
    }
    
    /**
     * Demo endpoint that makes multiple rapid calls to trigger @Retryable behavior.
     * Use this to easily demonstrate retry behavior with exponential backoff.
     * 
     * @param count Number of calls to make (default 10)
     */
    @GetMapping("/demo/rapid-fire")
    fun rapidFireDemo(
        @RequestParam(defaultValue = "10") count: Int
    ): ResponseEntity<RapidFireResult> {
        val results = mutableListOf<CallResult>()
        val maxCount = count.coerceIn(1, 50) // Limit between 1-50 calls
        
        repeat(maxCount) { index ->
            val startTime = System.currentTimeMillis()
            try {
                val response = weatherService.getCurrentWeather()
                val duration = System.currentTimeMillis() - startTime
                
                results.add(
                    CallResult(
                        callNumber = index + 1,
                        success = !response.fromFallback,
                        error = response.error,
                        durationMs = duration
                    )
                )
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                results.add(
                    CallResult(
                        callNumber = index + 1,
                        success = false,
                        error = e.message,
                        durationMs = duration
                    )
                )
            }
        }
        
        val finalStats = weatherService.getRetryStatistics()
        
        return ResponseEntity.ok(
            RapidFireResult(
                totalCalls = results.size,
                successCount = results.count { it.success },
                failureCount = results.count { !it.success },
                calls = results,
                retryStatistics = finalStats
            )
        )
    }
    
    /**
     * Parse language parameter to enum
     */
    private fun parseLanguage(lang: String): WeatherLang {
        return when (lang.lowercase()) {
            "tc" -> WeatherLang.TRADITIONAL_CHINESE
            "sc" -> WeatherLang.SIMPLIFIED_CHINESE
            else -> WeatherLang.ENGLISH
        }
    }
    
    /**
     * Build response with appropriate HTTP status based on fallback state
     */
    private fun <T> buildResponse(response: WeatherApiResponse<T>): ResponseEntity<WeatherApiResponse<T>> {
        return if (response.fromFallback) {
            ResponseEntity.status(503).body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }
}
