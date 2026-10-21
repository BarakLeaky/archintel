package io.archintel.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("archintel.jwt")
public record JwtProperties(String secret, @DefaultValue("120") int ttlMinutes) {}
