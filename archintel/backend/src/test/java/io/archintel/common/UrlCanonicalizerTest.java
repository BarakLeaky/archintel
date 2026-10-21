package io.archintel.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UrlCanonicalizerTest {
    @Test void normalisesCaseWwwPortFragmentTrackingAndQueryOrder() {
        assertEquals("https://example.com/a/b?a=1&b=2",
            UrlCanonicalizer.canonicalize("HTTPS://WWW.Example.com:443/a/b/?utm_source=x&b=2&a=1#frag"));
    }

    @Test void githubUrlsAreCaseInsensitiveAndDropDotGit() {
        String expected = "https://github.com/owner/repo";
        assertEquals(expected, UrlCanonicalizer.canonicalize("https://github.com/Owner/Repo.git"));
        assertEquals(expected, UrlCanonicalizer.canonicalize("https://www.github.com/Owner/Repo/"));
    }

    @Test void nonDefaultPortIsKept() {
        assertEquals("http://example.com:8080/x", UrlCanonicalizer.canonicalize("http://example.com:8080/x"));
    }

    @Test void credentialsAreDropped() {
        assertEquals("https://example.com/p", UrlCanonicalizer.canonicalize("https://user:secret@example.com/p"));
    }

    @Test void rootPathIsSlash() {
        assertEquals("https://example.com/", UrlCanonicalizer.canonicalize("https://example.com"));
    }

    @Test void rejectsNonHttpAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> UrlCanonicalizer.canonicalize("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> UrlCanonicalizer.canonicalize("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> UrlCanonicalizer.canonicalize("  "));
        assertThrows(IllegalArgumentException.class, () -> UrlCanonicalizer.canonicalize(null));
    }
}
