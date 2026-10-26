package io.archintel.research;

import io.archintel.auth.AuthenticatedUser;
import io.archintel.project.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/research")
public class ResearchController {

    private final ProjectService projects;
    private final ResearchRunService research;

    public ResearchController(ProjectService projects, ResearchRunService research) {
        this.projects = projects;
        this.research = research;
    }

    @PostMapping
    public ResearchRunResult run(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody ResearchRequest request) {

        // Return 404 for projects the authenticated user does not own.
        projects.get(user.id(), projectId);

        ResearchQuery query = new ResearchQuery(
                request.keywords(),
                request.createdAfter(),
                request.maxResults());

        return research.run(query);
    }

    public record ResearchRequest(
            @NotEmpty
            @Size(max = ResearchQuery.MAX_KEYWORDS)
            List<@Size(min = 1, max = ResearchQuery.MAX_KEYWORD_LENGTH) String> keywords,
            Instant createdAfter,
            @Min(1) @Max(ResearchQuery.MAX_RESULTS_CAP) int maxResults) {
    }
}
