package com.example.retry;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiny local HTTP server used for the demo. It answers the first
 * {@code failuresBeforeSuccess} requests with HTTP 503, then returns HTTP 200.
 */
final class FlakyServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicInteger calls = new AtomicInteger();
    private final int failuresBeforeSuccess;

    private FlakyServer(HttpServer server, ExecutorService executor, int failuresBeforeSuccess) {
        this.server = server;
        this.executor = executor;
        this.failuresBeforeSuccess = failuresBeforeSuccess;
    }

    static FlakyServer start(int failuresBeforeSuccess) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        FlakyServer flaky = new FlakyServer(
                server,
                Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "flaky-server");
                    thread.setDaemon(true);
                    return thread;
                }),
                failuresBeforeSuccess);

        server.createContext("/api/data", exchange -> {
            int call = flaky.calls.incrementAndGet();
            boolean fail = call <= flaky.failuresBeforeSuccess;

            String json = fail
                    ? "{\"error\":\"temporarily unavailable\"}"
                    : "{\"message\":\"hello from the flaky server\",\"call\":" + call + "}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(fail ? 503 : 200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.setExecutor(flaky.executor);
        server.start();
        return flaky;
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/data");
    }

    int callCount() {
        return calls.get();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
