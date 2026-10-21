package io.archintel.research;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One discovered item. Every date is nullable: a connector must leave a date null rather than guess it,
 * because the newness logic treats "no date" as "no claim of newness".
 * All text fields come from external sources and are untrusted data.
 */
public record ResearchItem(
        SourceType sourceType,
        String title,
        String url,
        String externalId,
        String author,
        String publisher,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        String excerpt,
        Map<String, Object> metadata) {

    public ResearchItem {
        if (sourceType == null) throw new IllegalArgumentException("sourceType required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
        if (url == null || url.isBlank()) throw new IllegalArgumentException("url required");
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
