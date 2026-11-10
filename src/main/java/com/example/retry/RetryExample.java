package com.example.retry;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Demonstrates the Retry pattern with Resilience4j against a remote HTTP resource.
 *
 * <p>With no arguments the program starts a tiny local server that fails the first
 * two requests with HTTP 503 and then succeeds, so the retries are visible without
 * needing any external service. Pass a URL to call a real endpoint instead:</p>
 *
 * <pre>{@code ./gradlew run --args="https://example.com/"}</pre>
 */
public final class RetryExample {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public static void main(String[] args) throws Exception {
        URI target;
        FlakyServer server = null;

        if (args.length > 0) {
            target = URI.create(args[0]);
        } else {
            // Fail the first two calls so the retry behaviour is easy to observe.
            server = FlakyServer.start(2);
            target = server.uri();
        }

        try {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(5)                                  // 1 initial call + 4 retries
                    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                            Duration.ofMillis(100),                 // ~100 ms, then 200, 400, 800
                            2.0,                                    // multiplier
                            0.5))                                   // +/- 50% jitter
                    .retryExceptions(IOException.class)             // transient failures only
                    .ignoreExceptions(InterruptedException.class)   // ignore wins over retry
                    .build();

            Retry retry = Retry.of("remoteResource", config);

            retry.getEventPublisher()
                    .onRetry(e -> System.out.printf(
                            "retry %d after failure (%s), waiting %d ms%n",
                            e.getNumberOfRetryAttempts(),
                            e.getLastThrowable(),
                            e.getWaitInterval().toMillis()))
                    .onSuccess(e -> System.out.printf(
                            "succeeded after %d retries%n", e.getNumberOfRetryAttempts()))
                    .onError(e -> System.out.printf(
                            "giving up after %d attempts%n", e.getNumberOfRetryAttempts()));

            // Decorate the remote call. The last exception is rethrown if all attempts fail.
            Callable<String> call = Retry.decorateCallable(retry, () -> fetch(target));

            System.out.println("GET " + target);
            String body = call.call();
            System.out.println("response: " + body);

            if (server != null) {
                System.out.println("server received " + server.callCount() + " request(s)");
            }
        } finally {
            if (server != null) {
                server.close();
            }
        }
    }

    /**
     * Performs a GET request and classifies failures:
     * 429/5xx become retryable IOExceptions, other 4xx errors are permanent and
     * therefore not retried by the configured Retry instance.
     */
    static String fetch(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(2))      // per-attempt timeout
                .GET()
                .build();

        HttpResponse<String> response =
                CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429 || response.statusCode() >= 500) {
            throw new IOException("transient HTTP " + response.statusCode());
        }
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("permanent HTTP " + response.statusCode());
        }
        return response.body();
    }

    private RetryExample() {
    }
}
