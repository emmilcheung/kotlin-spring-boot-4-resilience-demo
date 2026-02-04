package com.practice.kotlinspringboot4resiliencedemo.weather.exception

/**
 * Exception thrown when simulating failures for demo purposes.
 * Used to demonstrate @Retryable behavior.
 */
class SimulatedFailureException(message: String) : RuntimeException(message)

/**
 * Exception thrown when there's an error communicating with the Weather API.
 */
class WeatherApiException(message: String) : RuntimeException(message)
