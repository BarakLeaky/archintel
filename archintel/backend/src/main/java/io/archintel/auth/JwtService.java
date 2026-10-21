package io.archintel.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        String secret = props.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set to a random value of at least 32 bytes");
        }
        if (secret.startsWith("change-me")) {
            throw new IllegalStateException("JWT_SECRET is still the .env.example placeholder; set a real random secret");
        }
        if (props.ttlMinutes() <= 0) throw new IllegalStateException("archintel.jwt.ttl-minutes must be positive");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(props.ttlMinutes());
        this.clock = clock;
    }

    public Duration ttl() { return ttl; }

    public String issue(UUID userId, String email) {
        Instant now = clock.instant();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact();
    }

    /** Empty for any invalid, tampered or expired token; never throws on bad input. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant()))
                .build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedUser(UUID.fromString(c.getSubject()), c.get("email", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
