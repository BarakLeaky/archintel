package io.archintel.connector.github;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.archintel.connector.http.ExternalApiException;
import io.archintel.connector.http.RateLimitExceededException;
import io.archintel.connector.http.ResilientHttpClient;
import io.archintel.research.ResearchQuery;
import io.archintel.research.SourceType;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Runs the real connector and real HTTP client against a local fake GitHub. No network, no real credentials. */
class GitHubConnectorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());
    private volatile Map<String, String> lastHeaders = Map.of();

    private record Reply(int status, String body, Map<String, String> headers) {
        static Reply ok(String body) { return new Reply(200, body, Map.of()); }
        static Reply notFound() { return new Reply(404, "{\"message\":\"Not Found\"}", Map.of()); }
    }

    private interface Router { Reply route(String path, String rawQuery); }
    private interface Body { void run(GitHubConnector connector) throws Exception; }

    private void withConnector(Router router, int perPage, int maxFileChars, Body body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            String rawQuery = ex.getRequestURI().getRawQuery();
            requests.add(rawQuery == null ? path : path + "?" + rawQuery);
            var h = new java.util.HashMap<String, String>();
            ex.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.get(0)));
            lastHeaders = h;
            Reply r = router.route(path, rawQuery);
            r.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(r.status(), bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        });
        server.start();
        try {
            var props = new GitHubProperties("tok", "http://127.0.0.1:" + server.getAddress().getPort(),
                "2022-11-28", perPage, 3, maxFileChars);
            var settings = new ResilientHttpClient.Settings(Duration.ofSeconds(2), Duration.ofSeconds(5), 2,
                Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofSeconds(60), 50);
            var client = new ResilientHttpClient(settings, Clock.systemUTC(), d -> { }, () -> 0.0);
            body.run(new GitHubConnector(props, client, mapper));
        } finally {
            server.stop(0);
        }
    }

    private static String repo(long id, String fullName) {
        return """
            {"id":%d,"full_name":"%s","html_url":"https://github.com/%s","description":"Swahili speech",
             "created_at":"2026-09-28T10:00:00Z","pushed_at":"2026-09-29T07:00:00Z","updated_at":"2026-09-29T07:30:00Z",
             "stargazers_count":12,"forks_count":3,"language":"Python","topics":["swahili","asr"],
             "archived":false,"fork":false,"owner":{"login":"octo"},"license":null}""".formatted(id, fullName, fullName);
    }

    private static String searchResult(String... repos) { return "{\"total_count\":" + repos.length + ",\"items\":[" + String.join(",", repos) + "]}"; }

    private static String param(String rawQuery, String name) {
        for (String p : rawQuery.split("&")) if (p.startsWith(name + "=")) return URLDecoder.decode(p.substring(name.length() + 1), StandardCharsets.UTF_8);
        return null;
    }

    private String file(String content) {
        String enc = Base64.getMimeEncoder(8, "\n".getBytes()).encodeToString(content.getBytes(StandardCharsets.UTF_8));
        return mapper.createObjectNode().put("content", enc).put("encoding", "base64").toString();
    }

    // ------------------------------------------------------------------ search

    @Test void searchMapsFieldsKeepsDatesExactAndSendsAuthAndApiVersion() throws Exception {
        withConnector((p, q) -> Reply.ok(searchResult(repo(101, "octo/swahili-asr"))), 2, 20000, c -> {
            var items = c.search(new ResearchQuery(List.of("swahili asr"), Instant.parse("2026-09-22T00:00:00Z"), 10));
            assertEquals(1, items.size());
            var item = items.get(0);
            assertEquals(SourceType.GITHUB, item.sourceType());
            assertEquals("octo/swahili-asr", item.title());
            assertEquals("101", item.externalId());
            assertEquals("octo", item.author());
            assertEquals(Instant.parse("2026-09-28T10:00:00Z"), item.createdAt());
            assertEquals(Instant.parse("2026-09-29T07:00:00Z"), item.updatedAt());
            assertNull(item.publishedAt());
            assertEquals(12, item.metadata().get("stars"));
            assertEquals("Bearer tok", lastHeaders.get("authorization"));
            assertEquals("2022-11-28", lastHeaders.get("x-github-api-version"));
            String q = param(requests.get(0).substring(requests.get(0).indexOf('?') + 1), "q");
            assertEquals("\"swahili asr\" in:name,description,topics,readme created:>=2026-09-22", q);
        });
    }

    @Test void searchPaginatesUntilAShortPage() throws Exception {
        withConnector((p, q) -> "1".equals(param(q, "page"))
                ? Reply.ok(searchResult(repo(1, "o/a"), repo(2, "o/b")))
                : Reply.ok(searchResult(repo(3, "o/c"))), 2, 20000, c -> {
            var items = c.search(new ResearchQuery(List.of("swahili"), null, 50));
            assertEquals(3, items.size());
            assertEquals(2, requests.size());
        });
    }

    @Test void searchDeduplicatesAcrossKeywords() throws Exception {
        withConnector((p, q) -> Reply.ok(searchResult(repo(7, "o/same"))), 2, 20000, c -> {
            var items = c.search(new ResearchQuery(List.of("swahili", "kiswahili"), null, 50));
            assertEquals(1, items.size());
            assertEquals(2, requests.size());
        });
    }

    @Test void searchStopsAtMaxResults() throws Exception {
        withConnector((p, q) -> Reply.ok(searchResult(repo(1, "o/a"), repo(2, "o/b"))), 2, 20000, c ->
            assertEquals(1, c.search(new ResearchQuery(List.of("swahili"), null, 1)).size()));
    }

    @Test void searchQuerySanitisingBlocksQualifierInjection() {
        String q = GitHubConnector.buildSearchQuery("user:evil stars:>1", null);
        assertFalse(q.contains("user:"));
        assertFalse(q.contains("stars:"));
        assertTrue(q.startsWith("\"user evil stars >1\""));
        assertThrows(IllegalArgumentException.class, () -> GitHubConnector.buildSearchQuery(":::", null));
    }

    @Test void primaryRateLimitSurfacesAsRateLimitExceeded() throws Exception {
        long reset = Instant.now().getEpochSecond() + 3600;
        withConnector((p, q) -> new Reply(403, "{}", Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", String.valueOf(reset))),
            2, 20000, c -> assertThrows(RateLimitExceededException.class,
                () -> c.search(new ResearchQuery(List.of("swahili"), null, 5))));
    }

    @Test void rejectedCredentialsFailWithoutLeakingTheToken() throws Exception {
        withConnector((p, q) -> new Reply(401, "{\"message\":\"Bad credentials\"}", Map.of()), 2, 20000, c -> {
            var e = assertThrows(ExternalApiException.class, () -> c.search(new ResearchQuery(List.of("swahili"), null, 5)));
            assertEquals(401, e.status());
            assertFalse(e.getMessage().contains("tok"));
        });
    }

    // -------------------------------------------------------------- inspection

    @Test void inspectFetchesOnlyManifestsThatExistAndDecodesWrappedBase64() throws Exception {
        String listing = "[{\"name\":\"pom.xml\",\"type\":\"file\"},{\"name\":\"README.md\",\"type\":\"file\"},{\"name\":\"src\",\"type\":\"dir\"}]";
        withConnector((p, q) -> switch (p) {
            case "/repos/octo/swahili-asr" -> Reply.ok(repo(101, "octo/swahili-asr"));
            case "/repos/octo/swahili-asr/readme" -> Reply.ok(file("# Hello\n"));
            case "/repos/octo/swahili-asr/contents/" -> Reply.ok(listing);
            case "/repos/octo/swahili-asr/contents/pom.xml" -> Reply.ok(file("<project>one two three</project>"));
            case "/repos/octo/swahili-asr/languages" -> Reply.ok("{\"Java\":1000,\"Python\":50}");
            default -> Reply.notFound();
        }, 30, 20000, c -> {
            var insp = c.inspect("octo", "swahili-asr").orElseThrow();
            assertEquals("# Hello\n", insp.readme());
            assertEquals(List.of("pom.xml"), List.copyOf(insp.manifests().keySet()));
            assertEquals("<project>one two three</project>", insp.manifests().get("pom.xml"));
            assertEquals(1000L, insp.languages().get("Java").longValue());
            assertTrue(requests.stream().noneMatch(r -> r.contains("package.json")));
        });
    }

    @Test void inspectOfMissingRepositoryIsEmptyAndMissingReadmeIsNull() throws Exception {
        withConnector((p, q) -> Reply.notFound(), 30, 20000, c -> assertEquals(Optional.empty(), c.inspect("octo", "ghost")));
        withConnector((p, q) -> p.equals("/repos/octo/bare") ? Reply.ok(repo(5, "octo/bare")) : Reply.notFound(), 30, 20000, c -> {
            var insp = c.inspect("octo", "bare").orElseThrow();
            assertNull(insp.readme());
            assertTrue(insp.manifests().isEmpty());
        });
    }

    @Test void oversizedManifestIsReportedAsSkippedNotSilentlyDropped() throws Exception {
        withConnector((p, q) -> switch (p) {
            case "/repos/octo/big" -> Reply.ok(repo(9, "octo/big"));
            case "/repos/octo/big/contents/" -> Reply.ok("[{\"name\":\"package.json\",\"type\":\"file\"}]");
            case "/repos/octo/big/contents/package.json" -> Reply.ok("{\"content\":\"\",\"encoding\":\"none\"}");
            default -> Reply.notFound();
        }, 30, 20000, c -> assertEquals(List.of("package.json"), c.inspect("octo", "big").orElseThrow().skippedFiles()));
    }

    @Test void longReadmeIsTruncatedToTheConfiguredLimit() throws Exception {
        withConnector((p, q) -> switch (p) {
            case "/repos/octo/long" -> Reply.ok(repo(3, "octo/long"));
            case "/repos/octo/long/readme" -> Reply.ok(file("0123456789ABCDEFGHIJ"));
            default -> Reply.notFound();
        }, 30, 10, c -> {
            String readme = c.inspect("octo", "long").orElseThrow().readme();
            assertTrue(readme.startsWith("0123456789"));
            assertFalse(readme.contains("ABCDEFGHIJ"));
            assertTrue(readme.contains("[truncated"));
        });
    }

    @Test void inspectRejectsNamesThatCouldEscapeTheUrlPath() throws Exception {
        withConnector((p, q) -> Reply.notFound(), 30, 20000, c -> {
            assertThrows(IllegalArgumentException.class, () -> c.inspect("../etc", "passwd"));
            assertThrows(IllegalArgumentException.class, () -> c.inspect("octo", "repo/../../x"));
            assertThrows(IllegalArgumentException.class, () -> c.inspect(null, "repo"));
            assertTrue(requests.isEmpty());
        });
    }
}
