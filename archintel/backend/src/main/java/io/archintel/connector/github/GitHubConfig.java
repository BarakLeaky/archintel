package io.archintel.connector.github;

import io.archintel.connector.http.ResilientHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitHubConfig {
    @Bean
    ResilientHttpClient githubHttpClient() { return new ResilientHttpClient(ResilientHttpClient.Settings.defaults()); }
}
