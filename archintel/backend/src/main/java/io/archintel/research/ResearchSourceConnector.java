package io.archintel.research;

import java.util.List;

/** A pluggable research source (GitHub, web search, job boards, RSS ...). */
public interface ResearchSourceConnector {
    SourceType type();

    /** Short stable identifier, e.g. "github". */
    String name();

    /** False when the connector is not configured; callers skip it and the rest of ArchIntel keeps working. */
    boolean isEnabled();

    List<ResearchItem> search(ResearchQuery query);
}
