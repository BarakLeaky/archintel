package io.archintel.connector.github;

import io.archintel.research.ResearchItem;
import java.util.List;
import java.util.Map;

/**
 * API-based snapshot of a repository (no clone). Every text value here is UNTRUSTED external content:
 * it may contain prompt-injection attempts and must be passed to the LLM only as quoted data.
 * Text is truncated to the configured maximum.
 *
 * @param readme        README text, or null if the repository has none
 * @param manifests     root-level dependency/config files that exist, keyed by file name
 * @param skippedFiles  manifests that exist but were too large for the Contents API to return
 * @param languages     language name to bytes of code
 */
public record RepositoryInspection(ResearchItem item, String readme, Map<String, String> manifests,
                                   List<String> skippedFiles, Map<String, Long> languages) {}
