package com.joblens.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * A throwaway HTTP server on loopback, used to exercise the fetcher against real sockets.
 *
 * <p>Built on the JDK's own server rather than a mock library: the behaviour under test is timeouts,
 * redirects, oversized bodies and content types, all of which are properties of a real connection
 * rather than of a stubbed client.
 */
public final class LocalTestServer implements AutoCloseable {

    private final HttpServer server;

    private LocalTestServer(HttpServer server) {
        this.server = server;
    }

    public static LocalTestServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return new LocalTestServer(server);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    public LocalTestServer serve(String path, String contentType, String body) {
        return respond(path, 200, contentType, body, 0);
    }

    public LocalTestServer serveAfter(String path, String contentType, String body, long delayMillis) {
        return respond(path, 200, contentType, body, delayMillis);
    }

    public LocalTestServer status(String path, int statusCode) {
        return respond(path, statusCode, "text/html; charset=utf-8", "<html><body>no</body></html>", 0);
    }

    public LocalTestServer redirect(String path, int statusCode, String location) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
        });
        return this;
    }

    /** Declares a Content-Length far below what it actually sends, to test the streaming cap. */
    public LocalTestServer serveUndeclaredOversize(String path, int bytes) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                byte[] chunk = "x".repeat(1024).getBytes(StandardCharsets.UTF_8);
                for (int written = 0; written < bytes; written += chunk.length) {
                    out.write(chunk);
                }
            } catch (IOException e) {
                // The fetcher hangs up once the cap is hit; that is the behaviour under test.
            }
            exchange.close();
        });
        return this;
    }

    private LocalTestServer respond(String path, int statusCode, String contentType, String body, long delay) {
        server.createContext(path, exchange -> {
            sleep(delay);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            write(exchange, bytes);
        });
        return this;
    }

    private static void write(HttpExchange exchange, byte[] bytes) throws IOException {
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
