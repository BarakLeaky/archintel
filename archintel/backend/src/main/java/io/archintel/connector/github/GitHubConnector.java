package io.archintel.connector.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.archintel.connector.http.ExternalApiException;
import io.archintel.connector.http.ResilientHttpClient;
import io.archintel.research.ResearchItem;
import io.archintel.research.ResearchQuery;
import io.archintel.research.ResearchSourceConnector;
import io.archintel.research.SourceType;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * GitHub REST API connector. Search is one query per keyword (predictable against the search rate limit),
 * paginated, de-duplicated by repository id. Repository inspection uses the Contents API, never a clone.
 * Dates are passed through exactly as GitHub reports them; nothing is inferred.
 */
@Component
public class GitHubConnector implements ResearchSourceConnector {
    /** GitHub login/repo names; anything else is rejected before it can reach a URL path. */
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,100}$");

    static final List<String> MANIFESTS = List.of("package.json", "pom.xml", "build.gradle", "build.gradle.kts",
        "requirements.txt", "pyproject.toml", "go.mod", "Cargo.toml", "Dockerfile", "docker-compose.yml");

    private final GitHubProperties props;
    private final ResilientHttpClient http;
    private final ObjectMapper mapper;

    public GitHubConnector(GitHubProperties props, ResilientHttpClient http, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
    }

    @Override public SourceType type() { return SourceType.GITHUB; }
    @Override public String name() { return "github"; }
    /** Works without a token (lower rate limits), so it is always enabled. */
    @Override public boolean isEnabled() { return true; }

    // ---------------------------------------------------------------- search

    @Override
    public List<ResearchItem> search(ResearchQuery query) {
        Map<String, ResearchItem> byRepoId = new LinkedHashMap<>();
        for (String keyword : query.keywords()) {
            if (byRepoId.size() >= query.maxResults()) break;
            String q = buildSearchQuery(keyword, query.createdAfter());
            for (int page = 1; page <= props.maxPages() && byRepoId.size() < query.maxResults(); page++) {
                JsonNode root = fetch(searchUri(q, page))
                    .orElseThrow(() -> new ExternalApiException("GitHub search returned 404", 404));
                JsonNode items = root.path("items");
                if (!items.isArray() || items.isEmpty()) break;
                for (JsonNode n : items) {
                    if (byRepoId.size() >= query.maxResults()) break;
                    ResearchItem item = toItem(n);
                    byRepoId.putIfAbsent(item.externalId(), item);
                }
                if (items.size() < props.perPage()) break; // last page
            }
        }
        return List.copyOf(byRepoId.values());
    }

    /**
     * Quotes multi-word keywords and strips ':' and '"' so user text can never smuggle in search
     * qualifiers (e.g. "user:someone" or "stars:>1").
     */
    static String buildSearchQuery(String keyword, Instant createdAfter) {
        String clean = keyword.replace("\"", " ").replace(":", " ").replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("Keyword is empty after sanitising");
        StringBuilder sb = new StringBuilder(clean.contains(" ") ? "\"" + clean + "\"" : clean);
        sb.append(" in:name,description,topics,readme");
        if (createdAfter != null) {
            sb.append(" created:>=").append(DateTimeFormatter.ISO_LOCAL_DATE.format(createdAfter.atZone(ZoneOffset.UTC)));
        }
        return sb.toString();
    }

    private URI searchUri(String q, int page) {
        return URI.create(props.apiBase() + "/search/repositories?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
            + "&sort=updated&order=desc&per_page=" + props.perPage() + "&page=" + page);
    }

    private ResearchItem toItem(JsonNode n) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fullName", text(n, "full_name"));
        meta.put("stars", n.path("stargazers_count").asInt(0));
        meta.put("forks", n.path("forks_count").asInt(0));
        meta.put("archived", n.path("archived").asBoolean(false));
        meta.put("fork", n.path("fork").asBoolean(false));
        String language = text(n, "language");
        if (language != null) meta.put("language", language);
        List<String> topics = new ArrayList<>();
        n.path("topics").forEach(t -> topics.add(t.asText()));
        meta.put("topics", topics);
        meta.put("pushedAt", text(n, "pushed_at"));
        String license = text(n.path("license"), "spdx_id");
        if (license != null) meta.put("license", license);

        Instant pushed = instant(n, "pushed_at");
        return new ResearchItem(SourceType.GITHUB, text(n, "full_name"), text(n, "html_url"),
            n.path("id").asText(), text(n.path("owner"), "login"), "GitHub",
            null,                                   // repositories have no publication date; never invent one
            instant(n, "created_at"),
            pushed != null ? pushed : instant(n, "updated_at"),
            text(n, "description"), meta);
    }

    // ------------------------------------------------------------ inspection

    /** Empty when the repository does not exist (or is not visible to this token). */
    public Optional<RepositoryInspection> inspect(String owner, String repo) {
        requireName(owner);
        requireName(repo);
        String base = props.apiBase() + "/repos/" + owner + "/" + repo;

        Optional<JsonNode> meta = fetch(URI.create(base));
        if (meta.isEmpty()) return Optional.empty();
        ResearchItem item = toItem(meta.get());

        String readme = fetch(URI.create(base + "/readme")).map(this::decodeContent).orElse(null);

        Set<String> rootFiles = new HashSet<>();
        fetch(URI.create(base + "/contents/")).ifPresent(listing -> {
            if (listing.isArray()) {
                listing.forEach(e -> { if ("file".equals(text(e, "type"))) rootFiles.add(text(e, "name")); });
            }
        });

        Map<String, String> manifests = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        for (String file : MANIFESTS) {
            if (!rootFiles.contains(file)) continue; // do not assume files exist; only fetch what the listing shows
            Optional<JsonNode> node = fetch(URI.create(base + "/contents/" + file));
            if (node.isEmpty()) continue;
            String content = decodeContent(node.get());
            if (content == null) skipped.add(file); else manifests.put(file, content);
        }

        Map<String, Long> languages = new LinkedHashMap<>();
        fetch(URI.create(base + "/languages")).ifPresent(n -> n.fields().forEachRemaining(f -> languages.put(f.getKey(), f.getValue().asLong())));

        return Optional.of(new RepositoryInspection(item, readme, manifests, skipped, languages));
    }

    private String decodeContent(JsonNode file) {
        String content = text(file, "content");
        if (content == null || content.isBlank() || !"base64".equals(text(file, "encoding"))) return null; // >1MB files come back empty
        String decoded = new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
        return decoded.length() <= props.maxFileChars() ? decoded : decoded.substring(0, props.maxFileChars()) + "\n[truncated by ArchIntel]";
    }

    private static void requireName(String name) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid GitHub name");
    }

    // ------------------------------------------------------------------ http

    private Optional<JsonNode> fetch(URI uri) {
        ResilientHttpClient.Response r = http.get(uri, headers());
        return switch (r.status()) {
            case 200 -> Optional.of(parse(r.body()));
            case 404 -> Optional.empty();
            case 401 -> throw new ExternalApiException("GitHub rejected the configured credentials (401)", 401);
            default -> throw new ExternalApiException("GitHub returned HTTP " + r.status(), r.status());
        };
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Accept", "application/vnd.github+json");
        h.put("X-GitHub-Api-Version", props.apiVersion());
        h.put("User-Agent", "ArchIntel/0.2 (+https://github.com/BarakLeaky/archintel)");
        if (props.authenticated()) h.put("Authorization", "Bearer " + props.token());
        return h;
    }

    private JsonNode parse(String body) {
        try { return mapper.readTree(body); }
        catch (JsonProcessingException e) { throw new ExternalApiException("GitHub returned invalid JSON", -1, e); }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Instant instant(JsonNode n, String field) {
        String s = text(n, field);
        if (s == null) return null;
        try { return Instant.parse(s); } catch (DateTimeParseException e) { return null; }
    }
}
