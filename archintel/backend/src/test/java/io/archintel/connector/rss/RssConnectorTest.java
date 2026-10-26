package io.archintel.connector.rss;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.archintel.connector.http.ResilientHttpClient;
import io.archintel.research.ResearchQuery;
import io.archintel.research.SourceType;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RssConnectorTest {

    private void withFeeds(Map<String, String> feeds, List<String> configuredFeeds,
                           TestAction action) throws Exception {
        HttpServer server = HttpServer.create(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = feeds.get(path);
            int status = body == null ? 404 : 200;
            byte[] bytes = (body == null ? "Not found" : body)
                .getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                "Content-Type", "application/xml; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            List<String> urls = configuredFeeds.stream()
                .map(path -> base + path)
                .toList();

            var properties = new RssProperties(urls);
            var settings = new ResilientHttpClient.Settings(
                Duration.ofSeconds(2), Duration.ofSeconds(5), 1,
                Duration.ofMillis(10), Duration.ofMillis(50),
                Duration.ofSeconds(60), 50);
            var client = new ResilientHttpClient(
                settings, Clock.systemUTC(), d -> {}, () -> 0.0);

            action.run(new RssConnector(properties, client));
        } finally {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface TestAction {
        void run(RssConnector connector) throws Exception;
    }

    @Test
    void parsesRssEntriesAndMapsTheirFields() throws Exception {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Research News</title>
                <item>
                  <title>Swahili speech research</title>
                  <link>https://example.org/articles/1</link>
                  <guid>article-1</guid>
                  <author>Jane Doe</author>
                  <description>New research on Swahili speech recognition.</description>
                  <pubDate>Mon, 28 Sep 2026 10:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
            """;

        withFeeds(Map.of("/feed", xml), List.of("/feed"), connector -> {
            var items = connector.search(
                new ResearchQuery(List.of("Swahili"), null, 10));

            assertEquals(1, items.size());
            var item = items.get(0);
            assertEquals(SourceType.RSS, item.sourceType());
            assertEquals("Swahili speech research", item.title());
            assertEquals("https://example.org/articles/1", item.url());
            assertEquals("article-1", item.externalId());
            assertEquals("Jane Doe", item.author());
            assertEquals("New research on Swahili speech recognition.", item.excerpt());
            assertEquals(
                Instant.parse("2026-09-28T10:00:00Z"), item.publishedAt());
            assertEquals("127.0.0.1", item.publisher());
        });
    }

    @Test
    void filtersByKeywordsAndPublicationDate() throws Exception {
        String xml = """
            <rss version="2.0"><channel>
              <item>
                <title>Swahili speech research</title>
                <link>https://example.org/old</link>
                <pubDate>Mon, 21 Sep 2026 10:00:00 GMT</pubDate>
              </item>
              <item>
                <title>Swahili speech advances</title>
                <link>https://example.org/new</link>
                <pubDate>Mon, 28 Sep 2026 10:00:00 GMT</pubDate>
              </item>
              <item>
                <title>Marine biology update</title>
                <link>https://example.org/other</link>
                <pubDate>Mon, 29 Sep 2026 10:00:00 GMT</pubDate>
              </item>
            </channel></rss>
            """;

        withFeeds(Map.of("/feed", xml), List.of("/feed"), connector -> {
            var items = connector.search(new ResearchQuery(
                List.of("Swahili"), Instant.parse("2026-09-25T00:00:00Z"), 10));

            assertEquals(1, items.size());
            assertEquals("https://example.org/new", items.get(0).url());
        });
    }

    @Test
    void supportsAtomLinksAndPublishedDates() throws Exception {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Swahili language technology</title>
                <link href="https://example.org/atom-1" rel="alternate"/>
                <id>tag:example.org,2026:atom-1</id>
                <published>2026-10-01T12:30:00Z</published>
                <summary>Language technology research.</summary>
              </entry>
            </feed>
            """;

        withFeeds(Map.of("/atom", xml), List.of("/atom"), connector -> {
            var items = connector.search(
                new ResearchQuery(List.of("language technology"), null, 10));

            assertEquals(1, items.size());
            assertEquals("https://example.org/atom-1", items.get(0).url());
            assertEquals("tag:example.org,2026:atom-1", items.get(0).externalId());
            assertEquals(
                Instant.parse("2026-10-01T12:30:00Z"), items.get(0).publishedAt());
        });
    }

    @Test
    void deduplicatesEntriesAcrossFeedsAndHonorsMaxResults() throws Exception {
        String first = """
            <rss version="2.0"><channel><item>
              <title>Swahili research one</title>
              <link>https://example.org/shared</link>
            </item></channel></rss>
            """;
        String second = """
            <rss version="2.0"><channel>
              <item>
                <title>Swahili research duplicate</title>
                <link>https://example.org/shared</link>
              </item>
              <item>
                <title>Swahili research two</title>
                <link>https://example.org/two</link>
              </item>
            </channel></rss>
            """;

        withFeeds(Map.of("/one", first, "/two", second),
            List.of("/one", "/two"), connector -> {
                var items = connector.search(
                    new ResearchQuery(List.of("Swahili"), null, 1));
                assertEquals(1, items.size());
                assertEquals("https://example.org/shared", items.get(0).url());
            });
    }

    @Test
    void skipsMalformedFeedsWithoutThrowing() throws Exception {
        String malformed = "<rss><channel><item><title>broken";

        String valid = """
            <rss version="2.0"><channel><item>
              <title>Swahili research</title>
              <link>https://example.org/good</link>
            </item></channel></rss>
            """;

        Map<String, String> responses = Map.of(
            "/bad", malformed,
            "/good", valid
        );

        withFeeds(responses, List.of("/bad", "/good"), connector -> {
            var items = connector.search(
                new ResearchQuery(List.of("Swahili"), null, 10)
            );

            assertEquals(1, items.size());
            assertEquals("https://example.org/good", items.get(0).url());
        });
    }
}