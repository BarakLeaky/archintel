package io.archintel.research;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** What to look for. Bounds are enforced here so no connector can be asked for unbounded work. */
public record ResearchQuery(List<String> keywords, Instant createdAfter, int maxResults) {
    public static final int MAX_KEYWORDS = 6;
    public static final int MAX_KEYWORD_LENGTH = 100;
    public static final int MAX_RESULTS_CAP = 100;

    public ResearchQuery {
        if (keywords == null) throw new IllegalArgumentException("keywords required");
        List<String> cleaned = keywords.stream().filter(Objects::nonNull).map(String::trim)
            .filter(s -> !s.isEmpty()).distinct().toList();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("At least one keyword is required");
        if (cleaned.size() > MAX_KEYWORDS) throw new IllegalArgumentException("At most " + MAX_KEYWORDS + " keywords");
        if (cleaned.stream().anyMatch(k -> k.length() > MAX_KEYWORD_LENGTH)) {
            throw new IllegalArgumentException("Keywords are limited to " + MAX_KEYWORD_LENGTH + " characters");
        }
        if (maxResults < 1 || maxResults > MAX_RESULTS_CAP) {
            throw new IllegalArgumentException("maxResults must be between 1 and " + MAX_RESULTS_CAP);
        }
        keywords = cleaned;
    }
}
