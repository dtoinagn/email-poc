package ca.iiroc.halt.health;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

/** A real HTTP server standing in for a monitored service, so tests exercise actual timeouts and parsing. */
final class StubActuator implements AutoCloseable {

    private final HttpServer server;
    private volatile int httpStatus = 200;
    private volatile String body = "{\"status\":\"UP\"}";
    private volatile String contentType = "application/vnd.spring-boot.actuator.v3+json";
    private volatile Duration delay = Duration.ZERO;

    StubActuator() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/actuator/health", exchange -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(httpStatus, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    URI url() {
        return URI.create("http://localhost:" + server.getAddress().getPort() + "/actuator/health");
    }

    StubActuator respond(int httpStatus, String body) {
        this.httpStatus = httpStatus;
        this.body = body;
        return this;
    }

    StubActuator contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    StubActuator delay(Duration delay) {
        this.delay = delay;
        return this;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
