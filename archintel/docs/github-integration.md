# GitHub integration

Uses the GitHub REST API with `Accept: application/vnd.github+json` and `X-GitHub-Api-Version: 2022-11-28`. A token is optional but strongly recommended: unauthenticated search is limited to a small number of requests per minute.

## Search
One search per keyword (predictable against the search rate limit), `in:name,description,topics,readme`, optional `created:>=YYYY-MM-DD`. Results are paginated (`archintel.github.per-page`, `max-pages`), de-duplicated by repository id, and capped by `ResearchQuery.maxResults` (max 100).

## Dates
Repositories have no publication date, so `publishedAt` is always `null`. `createdAt` is GitHub's `created_at`; `updatedAt` is `pushed_at` (falling back to `updated_at`). Nothing is inferred. "New" is decided later by `NewnessClassifier`, which never calls an item new without a real date.

## Inspection (no cloning)
`GitHubConnector.inspect(owner, repo)` reads the metadata, README, the root directory listing, and only those manifests the listing shows exist: `package.json, pom.xml, build.gradle, build.gradle.kts, requirements.txt, pyproject.toml, go.mod, Cargo.toml, Dockerfile, docker-compose.yml`, plus the language breakdown. Missing files are normal (404 handled). Files too large for the Contents API come back empty and are reported in `skippedFiles`. All text is truncated to `archintel.github.max-file-chars`.

## Resilience (`ResilientHttpClient`)
- Connect/request timeouts; retries with exponential backoff and jitter on I/O errors, 408, 500, 502, 503, 504.
- Rate limits: honours `Retry-After` and `x-ratelimit-reset` when `x-ratelimit-remaining` is 0. Waits if the wait is short (default up to 60 s); otherwise throws `RateLimitExceededException` carrying the reset time.
- Conditional requests: stores `ETag`s (bounded LRU) and sends `If-None-Match`; a 304 replays the cached body.
- Redirects: same origin only.
