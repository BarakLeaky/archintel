package io.archintel.research;

import io.archintel.discovery.Newness;
import java.util.List;
import java.util.Set;

public record ResearchRunResult(
        int connectorsUsed,
        int sourcesFound,
        int newSources,
        int genuinelyNewSources,
        int updatedSources,
        List<ResearchResultItem> items) {

    public record ResearchResultItem(
            ResearchItem item,
            String canonicalUrl,
            Set<Newness> newness,
            boolean genuinelyNew,
            boolean firstSeenNow) {
    }
}
