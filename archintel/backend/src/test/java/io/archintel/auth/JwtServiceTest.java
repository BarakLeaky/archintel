package io.archintel.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef-test-secret";
    private final Clock now = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC);

    private JwtService service(String secret, Clock clock) { return new JwtService(new JwtProperties(secret, 60), clock); }

    @Test void issuedTokenRoundTrips() {
        UUID id = UUID.randomUUID();
        String token = service(SECRET, now).issue(id, "ada@example.com");
        var user = service(SECRET, now).parse(token).orElseThrow();
        assertEquals(id, user.id());
        assertEquals("ada@example.com", user.email());
    }

    @Test void tamperedTokenIsRejected() {
        String token = service(SECRET, now).issue(UUID.randomUUID(), "ada@example.com");
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1] + "x." + parts[2];
        assertTrue(service(SECRET, now).parse(forged).isEmpty());
    }

    @Test void tokenSignedWithAnotherSecretIsRejected() {
        String token = service(SECRET + "-other", now).issue(UUID.randomUUID(), "ada@example.com");
        assertTrue(service(SECRET, now).parse(token).isEmpty());
    }

    @Test void expiredTokenIsRejected() {
        String token = service(SECRET, now).issue(UUID.randomUUID(), "ada@example.com");
        Clock later = Clock.offset(now, Duration.ofMinutes(61));
        assertTrue(service(SECRET, later).parse(token).isEmpty());
    }

    @Test void garbageIsRejectedWithoutThrowing() {
        assertTrue(service(SECRET, now).parse("not-a-jwt").isEmpty());
        assertTrue(service(SECRET, now).parse("").isEmpty());
    }

    @Test void refusesToStartWithWeakOrPlaceholderSecret() {
        assertThrows(IllegalStateException.class, () -> service("short", now));
        assertThrows(IllegalStateException.class, () -> service(null, now));
        assertThrows(IllegalStateException.class,
            () -> service("change-me-to-a-long-random-string-of-at-least-32-chars", now));
    }
}
