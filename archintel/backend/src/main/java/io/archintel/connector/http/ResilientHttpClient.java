package io.archintel.connector.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * GET-only HTTP client for external APIs: timeouts, retries with exponential backoff and jitter,
 * rate-limit waiting (Retry-After / x-ratelimit-*), ETag conditional requests with a bounded cache,
 * and same-origin-only redirects (so Authorization headers never follow a redirect to another host).
 */
public final class ResilientHttpClient {
    public interface Sleeper { void sleep(Duration d) throws InterruptedException; }

    public record Settings(Duration connectTimeout, Duration requestTimeout, int maxRetries,
                           Duration baseBackoff, Duration maxBackoff, Duration maxRateLimitWait,
                           int maxCacheEntries) {
        public static Settings defaults() {
            return new Settings(Duration.ofSeconds(5), Duration.ofSeconds(15), 3,
                Duration.ofMillis(500), Duration.ofSeconds(8), Duration.ofSeconds(60), 500);
        }
    }

    /** headers has lower-cased names. fromCache is true when the server answered 304 and we replayed the cached body. */
    public record Response(int status, String body, Map<String, String> headers, boolean fromCache) {
        public String header(String name) { return headers.get(name.toLowerCase(Locale.ROOT)); }
    }

    private record CacheEntry(String etag, String body) {}

    private static final int MAX_REDIRECTS = 3;

    private final HttpClient http;
    private final Settings settings;
    private final Clock clock;
    private final Sleeper sleeper;
    private final DoubleSupplier random;
    private final Map<String, CacheEntry> cache;

    public ResilientHttpClient(Settings settings, Clock clock, Sleeper sleeper, DoubleSupplier random) {
        this.settings = Objects.requireNonNull(settings);
        this.clock = Objects.requireNonNull(clock);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.random = Objects.requireNonNull(random);
        this.http = HttpClient.newBuilder()
            .connectTimeout(settings.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        final int max = Math.max(1, settings.maxCacheEntries());
        this.cache = java.util.Collections.synchronizedMap(new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) { return size() > max; }
        });
    }

    public ResilientHttpClient(Settings settings) {
        this(settings, Clock.systemUTC(), d -> Thread.sleep(d.toMillis()), () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Returns the final response. 2xx and non-retryable 4xx (e.g. 404) are returned for the caller to interpret. */
    public Response get(URI uri, Map<String, String> headers) {
        Objects.requireNonNull(uri);
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only http(s) URLs are allowed");
        }
        URI current = uri;
        int attempt = 0;
        int hops = 0;
        while (true) {
            Response r;
            try {
                r = send(current, headers);
            } catch (IOException e) {
                if (attempt >= settings.maxRetries()) {
                    throw new ExternalApiException("Request failed: " + e.getClass().getSimpleName(), -1, e);
                }
                backoff(attempt++);
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExternalApiException("Interrupted", -1, e);
            }

            int status = r.status();
            if (status == 301 || status == 302 || status == 307 || status == 308) {
                URI next = sameOriginRedirect(current, r);
                if (next != null && hops++ < MAX_REDIRECTS) { current = next; continue; }
                return r;
            }
            if (isRateLimited(r)) {
                Duration wait = rateLimitWait(r);
                Instant resetAt = clock.instant().plus(wait);
                if (wait.compareTo(settings.maxRateLimitWait()) > 0 || attempt >= settings.maxRetries()) {
                    throw new RateLimitExceededException("Upstream rate limit exceeded", resetAt);
                }
                sleep(wait.plusSeconds(1));
                attempt++;
                continue;
            }
            if (status == 408 || status == 500 || status == 502 || status == 503 || status == 504) {
                if (attempt >= settings.maxRetries()) {
                    throw new ExternalApiException("Upstream returned HTTP " + status, status);
                }
                backoff(attempt++);
                continue;
            }
            return r;
        }
    }

    private Response send(URI uri, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(settings.requestTimeout()).GET();
        if (headers != null) headers.forEach(b::header);
        String key = uri.toString();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.etag() != null) b.header("If-None-Match", cached.etag());

        HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        Map<String, String> h = new HashMap<>();
        resp.headers().map().forEach((k, v) -> { if (!v.isEmpty()) h.put(k.toLowerCase(Locale.ROOT), v.get(0)); });

        if (resp.statusCode() == 304 && cached != null) return new Response(200, cached.body(), h, true);
        if (resp.statusCode() == 200 && h.get("etag") != null) cache.put(key, new CacheEntry(h.get("etag"), resp.body()));
        return new Response(resp.statusCode(), resp.body(), h, false);
    }

    private static URI sameOriginRedirect(URI current, Response r) {
        String loc = r.header("location");
        if (loc == null) return null;
        try {
            URI next = current.resolve(loc);
            boolean same = next.getScheme() != null && next.getScheme().equalsIgnoreCase(current.getScheme())
                && next.getHost() != null && next.getHost().equalsIgnoreCase(current.getHost())
                && next.getPort() == current.getPort();
            return same ? next : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isRateLimited(Response r) {
        if (r.status() == 429) return true;
        return r.status() == 403 && (r.header("retry-after") != null || "0".equals(r.header("x-ratelimit-remaining")));
    }

    private Duration rateLimitWait(Response r) {
        String retryAfter = r.header("retry-after");
        if (retryAfter != null) {
            try { return Duration.ofSeconds(Math.max(0, Long.parseLong(retryAfter.trim()))); }
            catch (NumberFormatException ignored) { /* fall through */ }
        }
        String reset = r.header("x-ratelimit-reset");
        if (reset != null) {
            try {
                Duration d = Duration.between(clock.instant(), Instant.ofEpochSecond(Long.parseLong(reset.trim())));
                return d.isNegative() ? Duration.ZERO : d;
            } catch (NumberFormatException ignored) { /* fall through */ }
        }
        return Duration.ofSeconds(60); // no usable hint: wait a minute (GitHub's guidance for secondary limits)
    }

    private void backoff(int attempt) {
        long base = settings.baseBackoff().toMillis() << Math.min(attempt, 10);
        long capped = Math.min(base, settings.maxBackoff().toMillis());
        long jitter = (long) (random.getAsDouble() * 250);
        sleep(Duration.ofMillis(capped + jitter));
    }

    private void sleep(Duration d) {
        try {
            sleeper.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("Interrupted while waiting", -1, e);
        }
    }
}
