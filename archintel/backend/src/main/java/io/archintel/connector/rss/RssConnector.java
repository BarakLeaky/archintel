package io.archintel.connector.rss;

import io.archintel.connector.http.ExternalApiException;
import io.archintel.connector.http.ResilientHttpClient;
import io.archintel.research.ResearchItem;
import io.archintel.research.ResearchQuery;
import io.archintel.research.ResearchSourceConnector;
import io.archintel.research.SourceType;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class RssConnector implements ResearchSourceConnector {
    private static final Logger log = LoggerFactory.getLogger(RssConnector.class);
    private static final int MAX_FEED_CHARS = 2_000_000;
    private static final int MAX_PARSED_ENTRIES = 5_000;

    private final RssProperties props;
    private final ResilientHttpClient http;

    public RssConnector(RssProperties props, ResilientHttpClient http) {
        this.props = props;
        this.http = http;
    }

    @Override
    public SourceType type() {
        return SourceType.RSS;
    }

    @Override
    public String name() {
        return "rss";
    }

    @Override
    public boolean isEnabled() {
        return props.configured();
    }

    @Override
    public List<ResearchItem> search(ResearchQuery query) {
        if (!isEnabled()) return List.of();

        Map<String, ResearchItem> results = new LinkedHashMap<>();

        for (String configuredFeed : props.feeds()) {
            if (results.size() >= query.maxResults()) break;

            try {
                URI feedUri = validatedHttpUri(URI.create(configuredFeed));
                ResilientHttpClient.Response response = http.get(feedUri, Map.of(
                    "Accept", "application/atom+xml, application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.1",
                    "User-Agent", "ArchIntel/1.0 RSS research connector"
                ));

                if (response.status() != 200) {
                    throw new ExternalApiException(
                        "RSS feed returned HTTP " + response.status(), response.status());
                }

                String body = response.body();
                if (body == null || body.isBlank()) continue;
                if (body.length() > MAX_FEED_CHARS) {
                    throw new ExternalApiException("RSS feed exceeds the permitted size", -1);
                }

                for (FeedEntry entry : parseFeed(body, feedUri)) {
                    if (results.size() >= query.maxResults()) break;
                    if (!matchesKeywords(entry, query)) continue;

                    // A requested date filter requires a known publication date.
                    if (query.createdAfter() != null &&
                        (entry.publishedAt() == null ||
                         entry.publishedAt().isBefore(query.createdAfter()))) {
                        continue;
                    }

                    String id = entry.externalId() == null || entry.externalId().isBlank()
                        ? entry.url() : entry.externalId();

                    ResearchItem item = new ResearchItem(
                        SourceType.RSS,
                        entry.title(),
                        entry.url(),
                        id,
                        entry.author(),
                        publisher(feedUri),
                        entry.publishedAt(),
                        null,
                        entry.updatedAt(),
                        limit(entry.description(), 4_000),
                        Map.of("feedUrl", feedUri.toString())
                    );

                    results.putIfAbsent(item.url(), item);
                }
            } catch (RuntimeException e) {
                // One unavailable or malformed feed must not block other configured feeds.
                log.warn("Skipping RSS feed {}: {}", safeFeedLabel(configuredFeed), e.getMessage());
            }
        }

        return List.copyOf(results.values());
    }

    private static List<FeedEntry> parseFeed(String xml, URI feedUri) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicID, systemID, baseURI, namespace) -> {
            throw new XMLStreamException("External XML resources are not allowed");
        });

        List<FeedEntry> entries = new ArrayList<>();
        XMLStreamReader reader = null;

        try {
            reader = factory.createXMLStreamReader(new StringReader(xml));
            EntryBuilder current = null;
            String captureField = null;
            StringBuilder captured = new StringBuilder();
            int entryDepth = -1;
            int depth = 0;

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.DTD) {
                    throw new XMLStreamException("DOCTYPE declarations are not allowed");
                }

                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    String local = reader.getLocalName().toLowerCase(Locale.ROOT);

                    if (current == null && (local.equals("item") || local.equals("entry"))) {
                        current = new EntryBuilder();
                        entryDepth = depth;
                    } else if (current != null) {
                        if (local.equals("link")) {
                            String href = attribute(reader, "href");
                            String rel = attribute(reader, "rel");
                            if (href != null &&
                                (rel == null || rel.isBlank() || rel.equalsIgnoreCase("alternate"))) {
                                current.url = href;
                            } else if (href == null) {
                                captureField = "link";
                                captured.setLength(0);
                            }
                        } else if (Set.of("title", "description", "summary", "content",
                                         "author", "name", "creator", "guid", "id",
                                         "pubdate", "published", "updated", "date").contains(local)) {
                            captureField = local;
                            captured.setLength(0);
                        }
                    }
                } else if (event == XMLStreamConstants.CHARACTERS ||
                           event == XMLStreamConstants.CDATA) {
                    if (current != null && captureField != null &&
                        captured.length() < 20_000) {
                        captured.append(reader.getText(), 0,
                            Math.min(reader.getTextLength(), 20_000 - captured.length()));
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String local = reader.getLocalName().toLowerCase(Locale.ROOT);

                    if (current != null && captureField != null) {
                        String value = captured.toString().trim();
                        if (!value.isEmpty()) current.accept(captureField, value);
                        captureField = null;
                        captured.setLength(0);
                    }

                    if (current != null && depth == entryDepth &&
                        (local.equals("item") || local.equals("entry"))) {
                        FeedEntry entry = current.build(feedUri);
                        if (entry != null) entries.add(entry);
                        current = null;
                        entryDepth = -1;

                        if (entries.size() >= MAX_PARSED_ENTRIES) break;
                    }
                    depth--;
                }
            }
        } catch (XMLStreamException e) {
            throw new ExternalApiException("RSS feed contains invalid or unsafe XML", -1, e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // Nothing more to do when closing a parser.
                }
            }
        }

        return entries;
    }

    private static String attribute(XMLStreamReader reader, String name) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (reader.getAttributeLocalName(i).equalsIgnoreCase(name)) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    private static boolean matchesKeywords(FeedEntry entry, ResearchQuery query) {
        String searchable = (entry.title() + " " +
            Optional.ofNullable(entry.description()).orElse("") + " " +
            Optional.ofNullable(entry.author()).orElse(""))
            .toLowerCase(Locale.ROOT);

        return query.keywords().stream()
            .map(k -> k.toLowerCase(Locale.ROOT))
            .anyMatch(searchable::contains);
    }

    private static URI validatedHttpUri(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null ||
            !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) ||
            uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Feed and article URLs must use valid HTTP(S) URLs");
        }
        return uri;
    }

    private static String publisher(URI feedUri) {
        return feedUri.getHost() == null ? "RSS" : feedUri.getHost();
    }

    private static String safeFeedLabel(String feed) {
        try {
            URI uri = URI.create(feed);
            return uri.getHost() == null ? "(invalid URL)" : uri.getHost();
        } catch (RuntimeException ignored) {
            return "(invalid URL)";
        }
    }

    private static String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record FeedEntry(
        String title, String url, String externalId, String author,
        String description, Instant publishedAt, Instant updatedAt) {}

    private static final class EntryBuilder {
        String title;
        String url;
        String externalId;
        String author;
        String description;
        Instant publishedAt;
        Instant updatedAt;

        void accept(String field, String value) {
            switch (field) {
                case "title" -> { if (title == null) title = value; }
                case "link" -> { if (url == null) url = value; }
                case "guid", "id" -> { if (externalId == null) externalId = value; }
                case "author", "name", "creator" -> { if (author == null) author = value; }
                case "description", "summary", "content" -> {
                    if (description == null) description = value;
                }
                case "pubdate", "published", "date" -> {
                    if (publishedAt == null) publishedAt = parseDate(value);
                }
                case "updated" -> {
                    Instant parsed = parseDate(value);
                    if (parsed != null) updatedAt = parsed;
                    if (publishedAt == null) publishedAt = parsed;
                }
                default -> { }
            }
        }

        FeedEntry build(URI feedUri) {
            if (title == null || title.isBlank() || url == null || url.isBlank()) return null;

            try {
                URI resolved = validatedHttpUri(feedUri.resolve(url).normalize());
                return new FeedEntry(
                    title.trim(), resolved.toString(), externalId, author,
                    description, publishedAt, updatedAt);
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    private static Instant parseDate(String value) {
        try { return Instant.parse(value); } catch (DateTimeParseException ignored) { }
        try { return OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toInstant(); }
        catch (DateTimeParseException ignored) { }
        try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
        catch (DateTimeParseException ignored) { }
        try { return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toInstant(ZoneOffset.UTC); }
        catch (DateTimeParseException ignored) { }
        try { return ZonedDateTime.parse(value).toInstant(); }
        catch (DateTimeParseException ignored) { }
        return null;
    }
}
