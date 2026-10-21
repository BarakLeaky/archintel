package io.archintel.connector.http;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientHttpClientTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC);
    private final List<Duration> sleeps = new ArrayList<>();

    private interface Body { void run(String baseUrl) throws Exception; }

    private static void withServer(HttpHandler handler, Body body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", handler);
        server.start();
        try { body.run("http://127.0.0.1:" + server.getAddress().getPort()); } finally { server.stop(0); }
    }

    private static void reply(HttpExchange ex, int status, String body, String... headerPairs) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < headerPairs.length; i += 2) ex.getResponseHeaders().add(headerPairs[i], headerPairs[i + 1]);
        if (bytes.length == 0) { ex.sendResponseHeaders(status, -1); ex.close(); return; }
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private ResilientHttpClient client(int maxRetries) {
        var s = new ResilientHttpClient.Settings(Duration.ofSeconds(2), Duration.ofSeconds(5), maxRetries,
            Duration.ofMillis(500), Duration.ofSeconds(8), Duration.ofSeconds(60), 50);
        return new ResilientHttpClient(s, CLOCK, sleeps::add, () -> 0.0);
    }

    @Test void returnsBodyOnSuccessAndSendsHeaders() throws Exception {
        withServer(ex -> reply(ex, 200, "hello", "x-echo", String.valueOf(ex.getRequestHeaders().getFirst("X-Test"))), base -> {
            var r = client(2).get(URI.create(base + "/a"), Map.of("X-Test", "yes"));
            assertEquals(200, r.status());
            assertEquals("hello", r.body());
            assertEquals("yes", r.header("X-Echo"));
        });
    }

    @Test void notFoundIsReturnedNotThrown() throws Exception {
        withServer(ex -> reply(ex, 404, "{}"), base -> {
            assertEquals(404, client(2).get(URI.create(base + "/missing"), Map.of()).status());
            assertTrue(sleeps.isEmpty());
        });
    }

    @Test void retriesTransientFailureWithBackoffThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        withServer(ex -> {
            if (calls.incrementAndGet() == 1) reply(ex, 503, "busy"); else reply(ex, 200, "ok");
        }, base -> {
            var r = client(3).get(URI.create(base + "/x"), Map.of());
            assertEquals("ok", r.body());
            assertEquals(2, calls.get());
            assertEquals(List.of(Duration.ofMillis(500)), sleeps);
        });
    }

    @Test void givesUpAfterMaxRetriesWithExponentialBackoff() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        withServer(ex -> { calls.incrementAndGet(); reply(ex, 503, "busy"); }, base -> {
            var e = assertThrows(ExternalApiException.class, () -> client(2).get(URI.create(base + "/x"), Map.of()));
            assertEquals(503, e.status());
            assertEquals(3, calls.get());
            assertEquals(List.of(Duration.ofMillis(500), Duration.ofMillis(1000)), sleeps);
        });
    }

    @Test void waitsForPrimaryRateLimitResetThenRetries() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String reset = String.valueOf(CLOCK.instant().getEpochSecond() + 5);
        withServer(ex -> {
            if (calls.incrementAndGet() == 1) reply(ex, 403, "limit", "x-ratelimit-remaining", "0", "x-ratelimit-reset", reset);
            else reply(ex, 200, "ok");
        }, base -> {
            assertEquals("ok", client(3).get(URI.create(base + "/x"), Map.of()).body());
            assertEquals(List.of(Duration.ofSeconds(6)), sleeps); // 5s until reset + 1s buffer
        });
    }

    @Test void honoursRetryAfterOn429() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        withServer(ex -> {
            if (calls.incrementAndGet() == 1) reply(ex, 429, "slow down", "retry-after", "2"); else reply(ex, 200, "ok");
        }, base -> {
            assertEquals("ok", client(3).get(URI.create(base + "/x"), Map.of()).body());
            assertEquals(List.of(Duration.ofSeconds(3)), sleeps);
        });
    }

    @Test void refusesToBlockForLongRateLimitResets() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        long resetEpoch = CLOCK.instant().getEpochSecond() + 3600;
        withServer(ex -> {
            calls.incrementAndGet();
            reply(ex, 403, "limit", "x-ratelimit-remaining", "0", "x-ratelimit-reset", String.valueOf(resetEpoch));
        }, base -> {
            var e = assertThrows(RateLimitExceededException.class, () -> client(3).get(URI.create(base + "/x"), Map.of()));
            assertEquals(Instant.ofEpochSecond(resetEpoch), e.resetAt());
            assertEquals(1, calls.get());
            assertTrue(sleeps.isEmpty());
        });
    }

    @Test void plain403WithoutRateLimitHeadersIsNotRetried() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        withServer(ex -> { calls.incrementAndGet(); reply(ex, 403, "forbidden"); }, base -> {
            assertEquals(403, client(3).get(URI.create(base + "/x"), Map.of()).status());
            assertEquals(1, calls.get());
        });
    }

    @Test void usesEtagForConditionalRequestsAndReplaysCachedBody() throws Exception {
        List<String> seenIfNoneMatch = new ArrayList<>();
        withServer(ex -> {
            String inm = ex.getRequestHeaders().getFirst("If-None-Match");
            seenIfNoneMatch.add(inm);
            if ("\"abc\"".equals(inm)) reply(ex, 304, ""); else reply(ex, 200, "v1", "ETag", "\"abc\"");
        }, base -> {
            var c = client(2);
            var first = c.get(URI.create(base + "/doc"), Map.of());
            var second = c.get(URI.create(base + "/doc"), Map.of());
            assertFalse(first.fromCache());
            assertTrue(second.fromCache());
            assertEquals("v1", second.body());
            assertEquals(200, second.status());
            assertNull(seenIfNoneMatch.get(0));
            assertEquals("\"abc\"", seenIfNoneMatch.get(1));
        });
    }

    @Test void followsSameOriginRedirects() throws Exception {
        withServer(ex -> {
            if (ex.getRequestURI().getPath().equals("/old")) reply(ex, 301, "", "Location", "/new");
            else reply(ex, 200, "moved");
        }, base -> assertEquals("moved", client(2).get(URI.create(base + "/old"), Map.of()).body()));
    }

    @Test void doesNotFollowCrossOriginRedirects() throws Exception {
        withServer(ex -> reply(ex, 302, "", "Location", "http://other.invalid/steal"), base -> {
            var r = client(2).get(URI.create(base + "/x"), Map.of("Authorization", "Bearer secret"));
            assertEquals(302, r.status());
        });
    }

    @Test void connectionFailuresAreRetriedThenReported() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { port = s.getLocalPort(); }
        var e = assertThrows(ExternalApiException.class,
            () -> client(2).get(URI.create("http://127.0.0.1:" + port + "/x"), Map.of()));
        assertEquals(-1, e.status());
        assertEquals(2, sleeps.size());
    }

    @Test void rejectsNonHttpSchemes() {
        assertThrows(IllegalArgumentException.class, () -> client(1).get(URI.create("file:///etc/passwd"), Map.of()));
    }
}
