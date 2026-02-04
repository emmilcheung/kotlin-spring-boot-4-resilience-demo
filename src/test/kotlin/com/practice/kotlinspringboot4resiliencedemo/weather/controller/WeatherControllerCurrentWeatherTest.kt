package com.practice.kotlinspringboot4resiliencedemo.weather.controller

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Integration tests for WeatherController /api/weather/current endpoint.
 * 
 * Tests the full request-response cycle including:
 * - HTTP request handling
 * - Service layer with @Retryable
 * - Response serialization
 * - Error handling
 * 
 * Uses WireMock to simulate the HK Observatory API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WeatherControllerCurrentWeatherTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("weather.api.base-url") { wireMockServer.baseUrl() }
            registry.add("weather.api.resource") { "weather.php" }
            registry.add("weather.api.simulate-failures") { "false" }
        }
    }

    @BeforeEach
    fun setup() {
        wireMockServer.resetAll()
        // Reset stats before each test
        mockMvc.perform(post("/api/weather/retry/reset"))
    }

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    private val sampleCurrentWeatherResponse = """
        {
            "rainfall": {
                "data": [
                    {"unit": "mm", "place": "Central & Western District", "max": 0, "main": "FALSE"},
                    {"unit": "mm", "place": "Wan Chai", "max": 0, "main": "FALSE"}
                ],
                "startTime": "2026-02-04T08:00:00+08:00",
                "endTime": "2026-02-04T09:00:00+08:00"
            },
            "temperature": {
                "data": [
                    {"place": "Hong Kong Observatory", "value": 22, "unit": "C"},
                    {"place": "King's Park", "value": 21, "unit": "C"}
                ],
                "recordTime": "2026-02-04T09:00:00+08:00"
            },
            "humidity": {
                "data": [
                    {"place": "Hong Kong Observatory", "value": 75, "unit": "percent"}
                ],
                "recordTime": "2026-02-04T09:00:00+08:00"
            },
            "uvindex": {
                "data": [
                    {"place": "King's Park", "value": 5, "desc": "moderate"}
                ],
                "recordTime": "2026-02-04T09:00:00+08:00"
            }
        }
    """.trimIndent()

    // ===========================================
    // TEST: Successful GET /api/weather/current
    // ===========================================
    
    @Test
    @DisplayName("GET /api/weather/current should return current weather data")
    fun `get current weather success`() {
        // Given: External API returns success
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

        // When/Then: Call endpoint and verify response
        mockMvc.perform(
            get("/api/weather/current")
                .param("lang", "en")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.data").exists())
            .andExpect(jsonPath("$.dataType").value("rhrread"))
            .andExpect(jsonPath("$.lang").value("en"))
            .andExpect(jsonPath("$.fromFallback").value(false))
            .andExpect(jsonPath("$.retryInfo").exists())
            .andExpect(jsonPath("$.retryInfo.retryEnabled").value(true))
            .andExpect(jsonPath("$.retryInfo.maxRetries").value(3))
    }

    // ===========================================
    // TEST: Default language parameter
    // ===========================================
    
    @Test
    @DisplayName("GET /api/weather/current without lang param should default to English")
    fun `get current weather default language`() {
        // Given: External API configured for English
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

        // When/Then: Call without lang param
        mockMvc.perform(get("/api/weather/current"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lang").value("en"))
    }

    // ===========================================
    // TEST: Traditional Chinese language
    // ===========================================
    
    @Test
    @DisplayName("GET /api/weather/current?lang=tc should use Traditional Chinese")
    fun `get current weather traditional chinese`() {
        // Given: External API configured for TC
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

        // When/Then: Call with tc lang
        mockMvc.perform(
            get("/api/weather/current")
                .param("lang", "tc")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lang").value("tc"))
        
        // Verify correct lang sent to external API
        wireMockServer.verify(
            getRequestedFor(urlPathEqualTo("/weather.php"))
                .withQueryParam("lang", equalTo("tc"))
        )
    }

    // ===========================================
    // TEST: Retry behavior visible in stats
    // ===========================================
    
    @Test
    @DisplayName("Retry behavior should be reflected in /api/weather/retry/stats")
    fun `retry stats after successful call`() {
        // Given: API succeeds
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(sampleCurrentWeatherResponse)
                )
        )

        // When: Make a call
        mockMvc.perform(get("/api/weather/current"))
            .andExpect(status().isOk)

        // Then: Check stats
        mockMvc.perform(get("/api/weather/retry/stats"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalAttempts").value(1))
            .andExpect(jsonPath("$.successfulCalls").value(1))
            .andExpect(jsonPath("$.failedCalls").value(0))
    }

    // ===========================================
    // TEST: Retry on transient failure
    // ===========================================
    
    @Test
    @DisplayName("Should retry on transient 500 error and succeed")
    fun `retry on transient failure`() {
        // Given: First call fails, second succeeds (WireMock scenarios)
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .inScenario("Transient Failure")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("Recovered")
        )
        
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .withQueryParam("dataType", equalTo("rhrread"))
                .inScenario("Transient Failure")
                .whenScenarioStateIs("Recovered")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(sampleCurrentWeatherResponse)
                )
        )

        // When/Then: Should eventually succeed
        mockMvc.perform(get("/api/weather/current"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").exists())
        
        // Verify 2 requests made (1 failed + 1 success)
        wireMockServer.verify(2, getRequestedFor(urlPathEqualTo("/weather.php")))
    }

    // ===========================================
    // TEST: Permanent failure returns error
    // ===========================================
    
    @Test
    @DisplayName("Should exhaust all retries when API always fails")
    fun `permanent failure after retries exhausted`() {
        // Given: API always fails
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .willReturn(aResponse().withStatus(500).withBody("Server Error"))
        )

        // When/Then: Should throw exception after retries exhausted
        // The exception propagates through the controller
        try {
            mockMvc.perform(get("/api/weather/current"))
        } catch (e: Exception) {
            // Expected - exception thrown after retries exhausted
        }
        
        // Verify 4 attempts (1 initial + 3 retries)
        wireMockServer.verify(4, getRequestedFor(urlPathEqualTo("/weather.php")))
    }

    // ===========================================
    // TEST: Response structure validation
    // ===========================================
    
    @Test
    @DisplayName("Response should contain all expected weather data fields")
    fun `response structure validation`() {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(sampleCurrentWeatherResponse)
                )
        )

        // When/Then: Validate nested structure
        mockMvc.perform(get("/api/weather/current"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.temperature").exists())
            .andExpect(jsonPath("$.data.temperature.data").isArray)
            .andExpect(jsonPath("$.data.humidity").exists())
            .andExpect(jsonPath("$.data.rainfall").exists())
            .andExpect(jsonPath("$.fetchedAt").exists())
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    // ===========================================
    // TEST: Reset statistics endpoint
    // ===========================================
    
    @Test
    @DisplayName("POST /api/weather/retry/reset should clear all statistics")
    fun `reset statistics`() {
        // Given: Make some calls first
        wireMockServer.stubFor(
            get(urlPathEqualTo("/weather.php"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(sampleCurrentWeatherResponse)
                )
        )
        mockMvc.perform(get("/api/weather/current"))

        // When: Reset
        mockMvc.perform(post("/api/weather/retry/reset"))
            .andExpect(status().isOk)

        // Then: Stats are cleared
        mockMvc.perform(get("/api/weather/retry/stats"))
            .andExpect(jsonPath("$.totalAttempts").value(0))
            .andExpect(jsonPath("$.successfulCalls").value(0))
    }
}
