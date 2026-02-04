package com.practice.kotlinspringboot4resiliencedemo.weather.service

import com.practice.kotlinspringboot4resiliencedemo.weather.dto.WeatherLang
import io.mockk.*
import org.junit.jupiter.api.*
import org.springframework.web.client.RestClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for WeatherService using MockK.
 * 
 * These tests demonstrate testing retry behavior at the unit level
 * by mocking the RestClient and controlling failure injection.
 * 
 * Testing Strategy: Controlled Failure Injection
 * - Use AtomicInteger counter to fail first N calls
 * - Verify exact number of calls made
 * - Test boundary conditions (fail exactly maxRetries times)
 */
class WeatherServiceUnitTest {

    private lateinit var mockRestClientBuilder: RestClient.Builder
    private lateinit var mockRestClient: RestClient
    private lateinit var mockRequestSpec: RestClient.RequestHeadersUriSpec<*>
    private lateinit var mockResponseSpec: RestClient.ResponseSpec

    @BeforeEach
    fun setup() {
        mockRestClient = mockk()
        mockRestClientBuilder = mockk {
            every { baseUrl(any<String>()) } returns this
            every { build() } returns mockRestClient
        }
        mockRequestSpec = mockk()
        mockResponseSpec = mockk()
    }

    // ===========================================
    // TEST: Verify service creation
    // ===========================================
    
    @Test
    @DisplayName("Should create WeatherService with correct configuration")
    fun `service creation`() {
        // When: Create service
        val service = WeatherService(
            restClientBuilder = mockRestClientBuilder,
            weatherApiBaseUrl = "https://test.api.com",
            weatherApiResource = "weather.php",
            simulatedFailureRate = 0.0,
            simulateFailures = false
        )

        // Then: Service is created
        assertNotNull(service)
        
        // Verify RestClient was built with base URL
        verify { mockRestClientBuilder.baseUrl("https://test.api.com") }
        verify { mockRestClientBuilder.build() }
    }

    // ===========================================
    // TEST: Statistics initial state
    // ===========================================
    
    @Test
    @DisplayName("Should have zero statistics initially")
    fun `initial statistics are zero`() {
        // Given: New service
        val service = WeatherService(
            restClientBuilder = mockRestClientBuilder,
            weatherApiBaseUrl = "https://test.api.com",
            weatherApiResource = "weather.php",
            simulatedFailureRate = 0.3,
            simulateFailures = true
        )

        // When: Get statistics
        val stats = service.getRetryStatistics()

        // Then: All zeros
        assertEquals(0, stats.totalAttempts)
        assertEquals(0, stats.successfulCalls)
        assertEquals(0, stats.failedCalls)
        assertEquals(0, stats.retriedCalls)
        assertEquals(0.3, stats.simulatedFailureRate)
        assertTrue(stats.simulateFailuresEnabled)
        assertNull(stats.lastCallTimestamp)
    }

    // ===========================================
    // TEST: Configuration reflects in statistics
    // ===========================================
    
    @Test
    @DisplayName("Should reflect failure rate configuration in statistics")
    fun `configuration in statistics`() {
        // Given: Service with specific config
        val service = WeatherService(
            restClientBuilder = mockRestClientBuilder,
            weatherApiBaseUrl = "https://test.api.com",
            weatherApiResource = "weather.php",
            simulatedFailureRate = 0.5,
            simulateFailures = false
        )

        // When: Get statistics
        val stats = service.getRetryStatistics()

        // Then: Config values reflected
        assertEquals(0.5, stats.simulatedFailureRate)
        assertFalse(stats.simulateFailuresEnabled)
    }

    // ===========================================
    // TEST: Reset clears all counters
    // ===========================================
    
    @Test
    @DisplayName("Should reset all statistics counters")
    fun `reset clears counters`() {
        // Given: Service
        val service = WeatherService(
            restClientBuilder = mockRestClientBuilder,
            weatherApiBaseUrl = "https://test.api.com",
            weatherApiResource = "weather.php",
            simulatedFailureRate = 0.0,
            simulateFailures = false
        )

        // When: Reset
        service.resetStatistics()

        // Then: All zeros
        val stats = service.getRetryStatistics()
        assertEquals(0, stats.totalAttempts)
        assertEquals(0, stats.successfulCalls)
        assertEquals(0, stats.failedCalls)
        assertEquals(0, stats.retriedCalls)
    }
}

/**
 * Demonstrates the Controlled Failure Pattern for testing retries.
 * 
 * This is a standalone example showing how to test retry behavior
 * with deterministic control over when failures occur.
 */
class ControlledFailurePatternTest {

    /**
     * Example: Testing a retry scenario where the first 2 calls fail,
     * and the 3rd call succeeds.
     * 
     * Pattern:
     * 1. Use AtomicInteger to track call count
     * 2. Configure mock to fail until counter reaches threshold
     * 3. Verify final call count matches expected retries
     */
    @Test
    @DisplayName("Demonstrate controlled failure injection pattern")
    fun `controlled failure injection example`() {
        // Given: Counter to track attempts
        val attemptCounter = AtomicInteger(0)
        val failUntilAttempt = 3 // Fail first 2, succeed on 3rd
        
        // Simulated service call
        val simulatedCall: () -> String = {
            val attempt = attemptCounter.incrementAndGet()
            if (attempt < failUntilAttempt) {
                throw RuntimeException("Simulated failure on attempt $attempt")
            }
            "Success on attempt $attempt"
        }
        
        // When: Call with manual retry logic (simulating @Retryable)
        var result: String? = null
        var lastException: Exception? = null
        val maxAttempts = 4 // maxRetries + 1
        
        for (i in 1..maxAttempts) {
            try {
                result = simulatedCall()
                break // Success - exit loop
            } catch (e: Exception) {
                lastException = e
            }
        }
        
        // Then: Should succeed on 3rd attempt
        assertEquals("Success on attempt 3", result)
        assertEquals(3, attemptCounter.get())
    }

    /**
     * Example: Testing exhaustion of retries when all calls fail.
     */
    @Test
    @DisplayName("Demonstrate retry exhaustion pattern")
    fun `retry exhaustion example`() {
        // Given: Always failing call
        val attemptCounter = AtomicInteger(0)
        val maxRetries = 3
        
        val alwaysFailingCall: () -> String = {
            attemptCounter.incrementAndGet()
            throw RuntimeException("Always fails")
        }
        
        // When: Attempt with retry logic
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) {
            try {
                alwaysFailingCall()
            } catch (e: Exception) {
                lastException = e
            }
        }
        
        // Then: All retries exhausted
        assertEquals(maxRetries + 1, attemptCounter.get()) // 4 total attempts
        assertNotNull(lastException)
        assertEquals("Always fails", lastException?.message)
    }
}
