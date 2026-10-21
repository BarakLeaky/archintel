package io.archintel.connector.http;

import java.time.Instant;

/** The upstream rate limit is exhausted and the wait is longer than we are willing to block for. */
public class RateLimitExceededException extends ExternalApiException {
    private final Instant resetAt;

    public RateLimitExceededException(String message, Instant resetAt) {
        super(message, 429);
        this.resetAt = resetAt;
    }

    public Instant resetAt() { return resetAt; }
}
