package io.archintel.discovery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Date-aware newness detection. Being first seen by ArchIntel today does NOT make an
 * item "new": only real creation/publication dates qualify for notification.
 */
public class NewnessClassifier {
    private final Clock clock;
    private final Duration newWindow;
    private final Duration recentWindow;

    public NewnessClassifier(Clock clock, int newDays, int recentDays) {
        this.clock = clock;
        this.newWindow = Duration.ofDays(newDays);
        this.recentWindow = Duration.ofDays(recentDays);
    }

    public Set<Newness> classify(Instant repositoryCreatedAt, Instant publishedAt,
                                 Instant updatedAt, Instant firstSeenAt) {
        Set<Newness> result = EnumSet.noneOf(Newness.class);
        if (within(repositoryCreatedAt, newWindow)) result.add(Newness.NEWLY_CREATED);
        if (within(publishedAt, newWindow)) result.add(Newness.NEWLY_PUBLISHED);
        if (within(updatedAt, recentWindow)) result.add(Newness.RECENTLY_UPDATED);
        if (within(firstSeenAt, newWindow)) result.add(Newness.NEWLY_DISCOVERED);
        return result;
    }

    /** Notification-grade newness: real creation/publication date evidence only. */
    public boolean isGenuinelyNew(Set<Newness> n) {
        return n.contains(Newness.NEWLY_CREATED) || n.contains(Newness.NEWLY_PUBLISHED);
    }

    public long daysSince(Instant t) {
        return t == null ? -1 : Duration.between(t, clock.instant()).toDays();
    }

    private boolean within(Instant t, Duration window) {
        if (t == null) return false; // no date evidence -> never claim newness
        Duration age = Duration.between(t, clock.instant());
        return !age.isNegative() && age.compareTo(window) <= 0;
    }
}
