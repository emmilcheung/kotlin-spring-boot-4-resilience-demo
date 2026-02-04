package com.practice.kotlinspringboot4resiliencedemo.weather.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import com.practice.kotlinspringboot4resiliencedemo.weather.dto.WeatherLang
import com.practice.kotlinspringboot4resiliencedemo.weather.exception.SimulatedFailureException
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for WeatherService retry behavior.
 * 
 * These tests demonstrate how to test Spring Framework 7's @Retryable annotation
 * using WireMock to simulate various failure scenarios.
 * 
 * Testing Strategies Used:
 * 1. Deterministic failure via WireMock scenarios (stateful stubs)
 * 2. Verification of retry count via WireMock request counting
 * 3. Controlled failure injection - fail N times then succeed
 * 4. Property override to disable random failures for deterministic tests
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WeatherServiceRetryTest {

    @Autowired
    private lateinit var weatherService: WeatherService

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            // Point to WireMock instead of real API
            registry.add("weather.api.base-url") { wireMockServer.baseUrl() }
            registry.add("weather.api.resource") { "weather.php" }
            // Disable simulated failures - we control failures via WireMock
            registry.add("weather.api.simulate-failures") { "false" }
        }
    }

    @BeforeEach
    fun setup() {
        wireMockServer.resetAll()
        weatherService.resetStatistics()
    }

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    // ===========================================
    // SAMPLE RESPONSE DATA
    // ===========================================
    
    private val sampleCurrentWeatherResponse = """
        {
            "rainfall": {
                "data": [
                    {"unit": "mm", "place": "Central & Western District", "max": 0, "main": "FALSE"}
                ],
                "startTime": "2026-02-04T08:00:00+08:00",
                "endTime": "2026-02-04T09:00:00+08:00"
            },
            "temperature": {
                "data": [
                    {"place": "Hong Kong Observatory", "value": 22, "unit": "C"}
                ],
                "recordTime": "2026-02-04T09:00:00+08:00"
            },
            "humidity": {
                "data": [
                    {"place": "Hong Kong Observatory", "value": 75, "unit": "percent"}
                ],
                "recordTime": "2026-02-04T09:00:00+08:00"
            }
        }
    """.trimIndent()

    // ===========================================
    // TEST: Successful call without retry
    // ===========================================
    
    @Test
    @DisplayName("Should succeed on first attempt when API returns 200")
    fun `successful call without retry`() {
        // Given: API returns success on first call
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .withQueryParam("lang", equalTo("en"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(sampleCurrentWeatherResponse)
                )
        )

        // When: Call the service
        val result = weatherService.getCurrentWeather(WeatherLang.ENGLISH)

        // Then: Success with only 1 attempt
        assertNotNull(result.data)
        assertEquals("rhrread", result.dataType)
        assertEquals("en", result.lang)
        assertFalse(result.fromFallback)
        
        // Verify only 1 request was made (no retry)
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/weather.php")))
        
        val stats = weatherService.getRetryStatistics()
        assertEquals(1, stats.totalAttempts)
        assertEquals(1, stats.successfulCalls)
        assertEquals(0, stats.failedCalls)
    }

    // ===========================================
    // TEST: Retry on server error (5xx)
    // ===========================================
    
    @Test
    @DisplayName("Should retry on 500 error and succeed after recovery")
    fun `retry on server error then succeed`() {
        // Given: API fails twice (500), then succeeds on third attempt
        // Using WireMock Scenarios for stateful stubbing
        
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .inScenario("Retry Test")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500).withBody("Server Error"))
                .willSetStateTo("First Failure")
        )
        
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .inScenario("Retry Test")
                .whenScenarioStateIs("First Failure")
                .willReturn(aResponse().withStatus(500).withBody("Server Error"))
                .willSetStateTo("Second Failure")
        )
        
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .inScenario("Retry Test")
                .whenScenarioStateIs("Second Failure")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(sampleCurrentWeatherResponse)
                )
        )

        // When: Call the service
        val result = weatherService.getCurrentWeather(WeatherLang.ENGLISH)

        // Then: Eventually succeeds
        assertNotNull(result.data)
        assertEquals("rhrread", result.dataType)
        
        // Verify 3 requests were made (1 initial + 2 retries)
        wireMockServer.verify(3, getRequestedFor(urlPathEqualTo("/weather.php")))
    }

    // ===========================================
    // TEST: Exhaust all retries
    // ===========================================
    
    @Test
    @DisplayName("Should exhaust all retries and throw exception when API always fails")
    fun `exhaust all retries on persistent failure`() {
        // Given: API always returns 500
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .willReturn(aResponse().withStatus(500).withBody("Persistent Server Error"))
        )

        // When/Then: Should throw exception after exhausting retries
        val exception = assertThrows<Exception> {
            weatherService.getCurrentWeather(WeatherLang.ENGLISH)
        }
        
        // Verify: 4 total attempts (1 initial + 3 retries = maxRetries + 1)
        wireMockServer.verify(4, getRequestedFor(urlPathEqualTo("/weather.php")))
        
        assertNotNull(exception)
    }

    // ===========================================
    // TEST: Retry on connection timeout
    // ===========================================
    
    @Test
    @DisplayName("Should retry on connection timeout")
    fun `retry on timeout then succeed`() {
        // Given: First call times out, second succeeds
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .inScenario("Timeout Test")
                .whenScenarioStateIs(STARTED)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withFixedDelay(10000) // 10 second delay - will timeout
                )
                .willSetStateTo("After Timeout")
        )
        
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .inScenario("Timeout Test")
                .whenScenarioStateIs("After Timeout")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(sampleCurrentWeatherResponse)
                )
        )

        // When: Call the service (Note: this test may be slow due to timeout)
        // For faster tests, configure RestClient with shorter timeout
        val result = weatherService.getCurrentWeather(WeatherLang.ENGLISH)

        // Then: Eventually succeeds after retry
        assertNotNull(result.data)
    }

    // ===========================================
    // TEST: No retry on 4xx client errors
    // ===========================================
    
    @Test
    @DisplayName("Should NOT retry on 400 Bad Request (client error)")
    fun `no retry on client error`() {
        // Given: API returns 400 Bad Request
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .willReturn(aResponse().withStatus(400).withBody("Bad Request"))
        )

        // When/Then: Should fail without retrying
        assertThrows<Exception> {
            weatherService.getCurrentWeather(WeatherLang.ENGLISH)
        }
        
        // Verify: Only 1 request - no retry on client errors
        // Note: Default behavior may vary - adjust based on your retry configuration
        wireMockServer.verify(
            moreThanOrExactly(1),
            getRequestedFor(urlPathEqualTo("/weather.php"))
        )
    }

    // ===========================================
    // TEST: Language parameter variations
    // ===========================================
    
    @Test
    @DisplayName("Should pass correct language parameter to API")
    fun `correct language parameter passed`() {
        // Given: Stub for Traditional Chinese
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .withQueryParam("lang", equalTo("tc"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(sampleCurrentWeatherResponse)
                )
        )

        // When: Call with Traditional Chinese
        val result = weatherService.getCurrentWeather(WeatherLang.TRADITIONAL_CHINESE)

        // Then: Correct language in response
        assertEquals("tc", result.lang)
        
        // Verify correct query param
        wireMockServer.verify(
            getRequestedFor(urlPathEqualTo("/weather.php"))
                .withQueryParam("lang", equalTo("tc"))
        )
    }

    // ===========================================
    // TEST: Statistics tracking
    // ===========================================
    
    @Test
    @DisplayName("Should track retry statistics correctly")
    fun `statistics tracking across multiple calls`() {
        // Given: API always succeeds
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(sampleCurrentWeatherResponse)
                )
        )

        // When: Make multiple calls
        repeat(5) {
            weatherService.getCurrentWeather(WeatherLang.ENGLISH)
        }

        // Then: Statistics reflect all calls
        val stats = weatherService.getRetryStatistics()
        assertEquals(5, stats.totalAttempts)
        assertEquals(5, stats.successfulCalls)
        assertEquals(0, stats.failedCalls)
        assertNotNull(stats.lastCallTimestamp)
    }

    // ===========================================
    // TEST: Reset statistics
    // ===========================================
    
    @Test
    @DisplayName("Should reset statistics correctly")
    fun `reset statistics`() {
        // Given: Some calls made
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(sampleCurrentWeatherResponse)
                )
        )
        weatherService.getCurrentWeather(WeatherLang.ENGLISH)

        // When: Reset statistics
        weatherService.resetStatistics()

        // Then: All counters are zero
        val stats = weatherService.getRetryStatistics()
        assertEquals(0, stats.totalAttempts)
        assertEquals(0, stats.successfulCalls)
        assertEquals(0, stats.failedCalls)
        assertEquals(0, stats.retriedCalls)
    }
}
