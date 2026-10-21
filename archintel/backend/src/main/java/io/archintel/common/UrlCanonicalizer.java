package io.archintel.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normalises URLs so the same page reached through trivially different links dedupes to one source.
 * Scheme (http vs https) is intentionally preserved. Credentials, fragments and tracking parameters are dropped.
 */
public final class UrlCanonicalizer {
    private static final Set<String> TRACKING = Set.of(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "gclid", "fbclid", "mc_cid", "mc_eid", "ref", "ref_src");

    private UrlCanonicalizer() {}

    public static String canonicalize(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("URL must not be blank");
        URI uri = URI.create(raw.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http and https URLs are supported");
        }
        if (uri.getHost() == null) throw new IllegalArgumentException("URL has no host");

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);

        int port = uri.getPort();
        boolean defaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);

        String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/{2,}", "/");
        if (path.isEmpty()) path = "/";
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        if (host.equals("github.com")) {
            path = path.toLowerCase(Locale.ROOT); // GitHub owner/repo names are case-insensitive
            if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
        }

        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (port != -1 && !defaultPort) sb.append(':').append(port);
        sb.append(path);

        String query = cleanQuery(uri.getRawQuery());
        if (!query.isEmpty()) sb.append('?').append(query);
        return sb.toString();
    }

    private static String cleanQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        List<String> kept = new ArrayList<>();
        for (String part : rawQuery.split("&")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String key = (eq >= 0 ? part.substring(0, eq) : part).toLowerCase(Locale.ROOT);
            if (!TRACKING.contains(key)) kept.add(part);
        }
        Collections.sort(kept);
        return String.join("&", kept);
    }
}
