package io.archintel.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record CreateProjectRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 5000) String description,
        @Size(max = 2000) String targetUsers,
        @Size(max = 5000) String requirements) {}

    public record ProjectResponse(UUID id, String title, String description, String targetUsers,
                                  String requirements, Visibility visibility, Instant createdAt) {
        static ProjectResponse from(ResearchProject p) {
            return new ProjectResponse(p.getId(), p.getTitle(), p.getDescription(), p.getTargetUsers(),
                p.getRequirements(), p.getVisibility(), p.getCreatedAt());
        }
    }
}
