package io.archintel.connector.rss;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("archintel.rss")
public record RssProperties(
        @DefaultValue List<String> feeds) {

    public RssProperties {
        feeds = feeds == null
                ? List.of()
                : feeds.stream()
                    .filter(feed -> feed != null && !feed.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
    }

    public boolean configured() {
        return !feeds.isEmpty();
    }
}
