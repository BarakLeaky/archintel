package io.archintel.discovery;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

class NewnessClassifierTest {
    private final Instant now = Instant.parse("2026-09-29T08:00:00Z");
    private final NewnessClassifier c =
        new NewnessClassifier(Clock.fixed(now, ZoneOffset.UTC), 7, 14);

    @Test void repoCreatedYesterdayIsNewlyCreated() {
        var n = c.classify(Instant.parse("2026-09-28T10:00:00Z"), null, null, now);
        assertTrue(n.contains(Newness.NEWLY_CREATED));
        assertTrue(c.isGenuinelyNew(n));
    }

    @Test void oldRepoDiscoveredTodayIsNotNew() {
        var n = c.classify(Instant.parse("2021-01-01T00:00:00Z"), null,
                           Instant.parse("2026-09-27T00:00:00Z"), now);
        assertTrue(n.contains(Newness.RECENTLY_UPDATED));
        assertTrue(n.contains(Newness.NEWLY_DISCOVERED));
        assertFalse(n.contains(Newness.NEWLY_CREATED));
        assertFalse(c.isGenuinelyNew(n));
    }

    @Test void missingDatesNeverClaimNewness() {
        assertTrue(c.classify(null, null, null, null).isEmpty());
    }

    @Test void futureDatesAreRejected() {
        assertTrue(c.classify(Instant.parse("2027-01-01T00:00:00Z"), null, null, null).isEmpty());
    }
}
