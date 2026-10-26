package io.archintel.research;

import io.archintel.common.UrlCanonicalizer;
import io.archintel.discovery.Newness;
import io.archintel.discovery.NewnessClassifier;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResearchRunService {

    private static final Logger log =
            LoggerFactory.getLogger(ResearchRunService.class);

    private final List<ResearchSourceConnector> connectors;
    private final SourceRepository sourceRepository;
    private final NewnessClassifier newnessClassifier;
    private final Clock clock;

    public ResearchRunService(
            List<ResearchSourceConnector> connectors,
            SourceRepository sourceRepository,
            NewnessClassifier newnessClassifier,
            Clock clock) {

        this.connectors = connectors;
        this.sourceRepository = sourceRepository;
        this.newnessClassifier = newnessClassifier;
        this.clock = clock;
    }

    @Transactional
    public ResearchRunResult run(ResearchQuery query) {
        Instant now = clock.instant();

        Map<String, ResearchItem> deduplicated = new LinkedHashMap<>();
        int connectorsUsed = 0;

        for (ResearchSourceConnector connector : connectors) {
            if (!connector.isEnabled()) {
                continue;
            }

            connectorsUsed++;

            try {
                List<ResearchItem> results = connector.search(query);

                if (results == null) {
                    continue;
                }

                for (ResearchItem item : results) {
                    if (item == null || item.url() == null) {
                        continue;
                    }

                    String canonicalUrl;

                    try {
                        canonicalUrl =
                                UrlCanonicalizer.canonicalize(item.url());
                    } catch (IllegalArgumentException ex) {
                        log.warn(
                                "Skipping invalid URL from connector {}: {}",
                                connector.name(),
                                item.url());
                        continue;
                    }

                    deduplicated.putIfAbsent(canonicalUrl, item);
                }
            } catch (RuntimeException ex) {
                log.error(
                        "Research connector '{}' failed",
                        connector.name(),
                        ex);
            }
        }

        List<ResearchRunResult.ResearchResultItem> resultItems =
                new ArrayList<>();

        int newSources = 0;
        int genuinelyNewSources = 0;
        int updatedSources = 0;

        for (Map.Entry<String, ResearchItem> entry :
                deduplicated.entrySet()) {

            String canonicalUrl = entry.getKey();
            ResearchItem item = entry.getValue();

            Source existing =
                    sourceRepository.findByCanonicalUrl(canonicalUrl)
                            .orElse(null);

            boolean firstSeenNow = existing == null;

            Instant firstSeenAt =
                    firstSeenNow ? now : existing.getFirstSeenAt();

            Set<Newness> newness = newnessClassifier.classify(
                    item.createdAt(),
                    item.publishedAt(),
                    item.updatedAt(),
                    firstSeenAt);

            boolean genuinelyNew =
                    newnessClassifier.isGenuinelyNew(newness);

            if (firstSeenNow) {
                Source source = new Source(
                        item.sourceType(),
                        item.title(),
                        item.url(),
                        canonicalUrl,
                        extractDomain(canonicalUrl),
                        item.externalId(),
                        item.author(),
                        item.publisher(),
                        item.publishedAt(),
                        item.updatedAt(),
                        now,
                        null,
                        item.excerpt(),
                        item.metadata());

                sourceRepository.save(source);
                newSources++;
            } else {
                existing.refresh(
                        item.title(),
                        item.url(),
                        extractDomain(canonicalUrl),
                        item.externalId(),
                        item.author(),
                        item.publisher(),
                        item.publishedAt(),
                        item.updatedAt(),
                        existing.getContentHash(),
                        item.excerpt(),
                        item.metadata(),
                        now);

                sourceRepository.save(existing);
                updatedSources++;
            }

            if (genuinelyNew) {
                genuinelyNewSources++;
            }

            resultItems.add(
                    new ResearchRunResult.ResearchResultItem(
                            item,
                            canonicalUrl,
                            newness,
                            genuinelyNew,
                            firstSeenNow));
        }

        return new ResearchRunResult(
                connectorsUsed,
                deduplicated.size(),
                newSources,
                genuinelyNewSources,
                updatedSources,
                List.copyOf(resultItems));
    }

    private static String extractDomain(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
