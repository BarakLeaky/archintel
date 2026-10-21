package io.archintel.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Hashing and text normalisation used for duplicate detection. */
public final class ContentHasher {
    private ContentHasher() {}

    /** Lower-case hex SHA-256 (64 chars, fits sources.content_hash CHAR(64)). */
    public static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Hash of text with whitespace collapsed, so reformatting alone does not change the hash. */
    public static String hashText(String text) {
        return sha256Hex(text == null ? "" : text.trim().replaceAll("\\s+", " "));
    }

    /** Lower-case, punctuation-free title for fuzzy duplicate matching ("Swahili: ASR!" == "swahili asr"). */
    public static String normalizeTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
