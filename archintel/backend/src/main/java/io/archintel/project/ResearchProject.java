package io.archintel.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_projects")
public class ResearchProject {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(name = "target_users", columnDefinition = "text") private String targetUsers;
    @Column(columnDefinition = "text") private String requirements;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private Visibility visibility = Visibility.PRIVATE;
    @Column(name = "share_token", length = 64, unique = true) private String shareToken;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ResearchProject() {}

    public ResearchProject(UUID userId, String title, String description, String targetUsers, String requirements) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.targetUsers = targetUsers;
        this.requirements = requirements;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTargetUsers() { return targetUsers; }
    public String getRequirements() { return requirements; }
    public Visibility getVisibility() { return visibility; }
    public Instant getCreatedAt() { return createdAt; }
}
