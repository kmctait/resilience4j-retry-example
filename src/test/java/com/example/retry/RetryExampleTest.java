package com.example.retry;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryExampleTest {

    private static RetryConfig fastRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(4)
                .intervalFunction(IntervalFunction.of(Duration.ofMillis(1)))
                .retryExceptions(IOException.class)
                .build();
    }

    @Test
    void retriesTransientFailuresUntilSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Retry retry = Retry.of("test-transient", fastRetryConfig());

        Callable<String> call = Retry.decorateCallable(retry, () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new IOException("transient failure " + attempt);
            }
            return "ok";
        });

        assertEquals("ok", call.call());
        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryPermanentFailures() {
        AtomicInteger attempts = new AtomicInteger();
        Retry retry = Retry.of("test-permanent", fastRetryConfig());

        Callable<String> call = Retry.decorateCallable(retry, () -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("permanent");
        });

        assertThrows(IllegalArgumentException.class, call::call);
        assertEquals(1, attempts.get());
    }

    @Test
    void retriesAgainstFlakyHttpServer() throws Exception {
        try (FlakyServer server = FlakyServer.start(2)) {
            Retry retry = Retry.of("test-http", fastRetryConfig());
            Callable<String> call = Retry.decorateCallable(
                    retry, () -> RetryExample.fetch(server.uri()));

            String body = call.call();

            assertTrue(body.contains("hello from the flaky server"));
            assertEquals(3, server.callCount());
        }
    }
}
