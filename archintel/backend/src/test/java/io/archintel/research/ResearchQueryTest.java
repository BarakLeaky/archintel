package io.archintel.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchQueryTest {
    @Test void trimsAndDeduplicatesKeywords() {
        var q = new ResearchQuery(Arrays.asList(" Swahili ", "Swahili", "", null, "Kiswahili"), null, 10);
        assertEquals(List.of("Swahili", "Kiswahili"), q.keywords());
    }

    @Test void rejectsEmptyTooManyAndOversizedKeywords() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchQuery(List.of(" "), null, 10));
        assertThrows(IllegalArgumentException.class, () -> new ResearchQuery(List.of("a", "b", "c", "d", "e", "f", "g"), null, 10));
        assertThrows(IllegalArgumentException.class, () -> new ResearchQuery(List.of("x".repeat(101)), null, 10));
    }

    @Test void rejectsOutOfRangeMaxResults() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchQuery(List.of("a"), null, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResearchQuery(List.of("a"), null, 101));
    }

    @Test void itemRequiresTitleUrlAndType() {
        assertThrows(IllegalArgumentException.class,
            () -> new ResearchItem(SourceType.GITHUB, " ", "https://x", null, null, null, null, null, null, null, null));
        var item = new ResearchItem(SourceType.GITHUB, "t", "https://x", null, null, null, null, null, null, null, null);
        assertTrue(item.metadata().isEmpty());
    }
}
