# Spring Boot 4 / Spring Framework 7 Native Resilience Demo

A demonstration of **Spring Boot 4** with **Spring Framework 7's built-in resilience features** (`@Retryable`, `@EnableResilientMethods`) using the Hong Kong Observatory Weather API as a third-party service proxy.

> **Inspired by:** [Spring Boot 4's Built-in Resilience Features: Say Goodbye to External Dependencies!](https://www.youtube.com/watch?v=CT1wGTwOfg0) by Josh Long

## Demos

- **Spring Boot 4** with Kotlin and Gradle
- **Spring Framework 7 Native Resilience** - No third-party dependencies!
  - `@Retryable` annotation for automatic retry
  - Exponential backoff with multiplier
  - Jitter to prevent thundering herd
- **HK Observatory Weather API** proxy endpoints
- **Simulated Failures** to demonstrate retry behavior
- **Retry Statistics Monitoring**
- **Rapid-fire Demo Endpoint** for easy visualization

## Quick Start

### Full list what has been used:
- **Spring Boot 4.0.2**
- **Spring Framework 7.0.3** (native resilience)
- **Kotlin 2.2.21**
- **JDK 25** (toolchain) / **JVM Target 24** (Kotlin does not yet support JDK 25 bytecode)
- **Gradle 9.3.0**
- **Tomcat 11**

### Prerequisites
- JDK 25 (bytecode targets JDK 24 due to Kotlin compatibility)
- Gradle 9.3+ (wrapper included)

### Run the Application
```bash
./gradlew bootRun
```

The application starts on `http://localhost:8080`

### Run Tests
```bash
# Run all tests
./gradlew test

# Run tests with detailed output
./gradlew test --info

# Run a specific test class
./gradlew test --tests "WeatherServiceRetryTest"

# Run a specific test method
./gradlew test --tests "WeatherControllerCurrentWeatherTest.get current weather success"
```

## API Endpoints

### Weather Data Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/weather/current?lang=en` | Current weather readings |
| `GET /api/weather/forecast?lang=en` | Local weather forecast |
| `GET /api/weather/9day?lang=en` | 9-day weather forecast |

**Language Options:** `en` (English), `tc` (Traditional Chinese), `sc` (Simplified Chinese)

### Retry Monitoring & Control

| Endpoint | Description |
|----------|-------------|
| `GET /api/weather/retry/stats` | View retry statistics |
| `POST /api/weather/retry/reset` | Reset statistics |
| `GET /api/weather/demo/rapid-fire?count=15` | Demo endpoint to trigger retries |

### Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Application health |

## @Retryable Behavior

Spring Framework 7's `@Retryable` provides automatic retry with:

### Retry Configuration

| Setting | Value | Description |
|---------|-------|-------------|
| `maxRetries` | 3 | Max retry attempts (total 4 calls) |
| `delay` | 500ms | Initial delay between retries |
| `multiplier` | 2.0 | Exponential backoff multiplier |
| `jitter` | 100ms | Random variation to prevent thundering herd |
| `maxDelay` | 5000ms | Maximum delay cap |

### Retry Timeline Example

```
Attempt 1: Immediate call
        ↓ (fail)
   ~500ms delay (±100ms jitter)
Attempt 2: First retry
        ↓ (fail)
   ~1000ms delay (±200ms jitter)
Attempt 3: Second retry
        ↓ (fail)
   ~2000ms delay (±400ms jitter)
Attempt 4: Third retry (final)
```

## 🎮 Demo: Visualize Retry Behavior

### Step 1: Reset statistics

```bash
curl -X POST "http://localhost:8080/api/weather/retry/reset"
```

### Step 2: Make rapid calls to trigger retries

```bash
curl "http://localhost:8080/api/weather/demo/rapid-fire?count=10"
```

This makes 10 rapid API calls with ~30% simulated failure rate. Watch the retry statistics!

### Step 3: Check retry statistics

```bash
curl "http://localhost:8080/api/weather/retry/stats"
```

Example response:
```json
{
  "totalAttempts": 15,
  "successfulCalls": 10,
  "failedCalls": 5,
  "retriedCalls": 5,
  "simulatedFailureRate": 0.3,
  "simulateFailuresEnabled": true,
  "lastCallTimestamp": "2026-02-04T10:30:00Z"
}
```

### Step 4: Test a single call

```bash
curl "http://localhost:8080/api/weather/current"
```

## Configuration

Edit `application.properties`:

```properties
# Enable/disable simulated failures
weather.api.simulate-failures=true

# Failure rate (0.0 to 1.0)
weather.api.failure-rate=0.3
```

## Response Format

Every response includes retry metadata:

```json
{
  "data": { ... },
  "dataType": "rhrread",
  "lang": "en",
  "fetchedAt": "2026-02-04T08:00:00.000Z",
  "fromFallback": false,
  "error": null,
  "retryInfo": {
    "totalAttempts": 12,
    "retryEnabled": true,
    "maxRetries": 3
  }
}
```
