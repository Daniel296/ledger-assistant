package com.coremaker.ledger.mcp.integration.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class FakeLedger {

    private final HttpServer server;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();

    private FakeLedger(HttpServer server) {

        this.server = server;
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    public static FakeLedger start() {

        try {
            return new FakeLedger(HttpServer.create(new InetSocketAddress("localhost", 0), 0));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public String url() {

        return "http://localhost:%d".formatted(server.getAddress().getPort());
    }

    public void answer(String request, int status, String body) {

        answers.put(request, new Answer(status, body, Duration.ZERO));
    }

    public void answerAfter(Duration delay, String request) {

        answers.put(request, new Answer(200, "{}", delay));
    }

    public List<String> requests() {

        return requests;
    }

    public void forget() {

        answers.clear();
        requests.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {

        var request = exchange.getRequestURI().toString();
        requests.add("%s %s".formatted(exchange.getRequestMethod(), request));
        var answer = answers.getOrDefault(request, new Answer(500, "No answer for %s".formatted(request), Duration.ZERO));
        pause(answer.delay());
        var body = answer.body().getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(answer.status(), body.length);
        try (var response = exchange.getResponseBody()) {
            response.write(body);
        }
    }

    private static void pause(Duration delay) {

        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private record Answer(int status, String body, Duration delay) {

    }
}
