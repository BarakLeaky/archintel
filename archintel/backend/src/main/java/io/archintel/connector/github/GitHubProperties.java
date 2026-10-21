package io.archintel.connector.github;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("archintel.github")
public record GitHubProperties(
        String token,
        @DefaultValue("https://api.github.com") String apiBase,
        @DefaultValue("2022-11-28") String apiVersion,
        @DefaultValue("30") int perPage,
        @DefaultValue("5") int maxPages,
        @DefaultValue("20000") int maxFileChars) {

    public boolean authenticated() { return token != null && !token.isBlank(); }
}
