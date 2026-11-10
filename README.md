# Resilience4j Retry example

A small, runnable demonstration of the Retry pattern using
[Resilience4j](https://resilience4j.readme.io/docs/retry) against an HTTP
resource.

With no arguments the program starts a local HTTP server that fails the first two
requests with `503 Service Unavailable` and succeeds on the third. This makes the
retry behaviour visible without any external dependency.

## Requirements

- JDK 21+
- Internet access on the first build so Gradle can download the dependencies
  (Gradle itself is provided by the included wrapper)

## Run

```bash
./gradlew run
```

Expected output (wait times vary because jitter is enabled):

```text
GET http://127.0.0.1:PORT/api/data
retry 1 after failure (java.io.IOException: transient HTTP 503), waiting 87 ms
retry 2 after failure (java.io.IOException: transient HTTP 503), waiting 213 ms
succeeded after 2 retries
response: {"message":"hello from the flaky server","call":3}
server received 3 request(s)
```

## Test

```bash
./gradlew test
```

## Build a standalone distribution

```bash
./gradlew installDist
./build/install/resilience4j-retry-example/bin/resilience4j-retry-example
```

## Call your own endpoint

```bash
./gradlew run --args="https://example.com/"
```

`429` and `5xx` responses are treated as transient and retried; other `4xx`
responses are permanent and fail immediately.

## Configuration

The retry policy lives in `RetryExample#main`:

| Setting | Value | Meaning |
| --- | --- | --- |
| `maxAttempts` | 5 | 1 initial call + up to 4 retries |
| initial wait | 100 ms | doubled after each failure |
| multiplier | 2.0 | 100 → 200 → 400 → 800 ms |
| jitter | 0.5 | +/- 50% randomization to avoid thundering herds |
| retried exceptions | `IOException` | connection errors, timeouts, 5xx/429 |
| ignored exceptions | `InterruptedException` | never retry interruption |

## Layout

```text
src/main/java/com/example/retry/RetryExample.java   retry policy + HTTP call
src/main/java/com/example/retry/FlakyServer.java    local demo server
src/test/java/com/example/retry/RetryExampleTest.java
```
