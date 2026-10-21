package io.archintel.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ContentHasherTest {
    @Test void sha256MatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ContentHasher.sha256Hex("abc"));
        assertEquals(64, ContentHasher.sha256Hex("anything").length());
    }

    @Test void whitespaceOnlyChangesDoNotChangeTextHash() {
        assertEquals(ContentHasher.hashText("a b"), ContentHasher.hashText("  a \n\t b\n"));
        assertFalse(ContentHasher.hashText("a b").equals(ContentHasher.hashText("a c")));
    }

    @Test void titlesNormaliseForDuplicateMatching() {
        assertEquals("swahili asr toolkit", ContentHasher.normalizeTitle("  Swahili: ASR!! Toolkit "));
        assertEquals("", ContentHasher.normalizeTitle(null));
    }
}
