package io.archintel.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchProjectRepository extends JpaRepository<ResearchProject, UUID> {
    List<ResearchProject> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** The only way controllers load a single project: the owner id is part of the query, so ownership cannot be forgotten. */
    Optional<ResearchProject> findByIdAndUserId(UUID id, UUID userId);
}
