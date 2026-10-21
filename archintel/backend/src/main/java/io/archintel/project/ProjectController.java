package io.archintel.project;

import io.archintel.auth.AuthenticatedUser;
import io.archintel.project.ProjectDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projects;

    public ProjectController(ProjectService projects) { this.projects = projects; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateProjectRequest req) {
        return projects.create(user.id(), req);
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal AuthenticatedUser user) { return projects.list(user.id()); }

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return projects.get(user.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) { projects.delete(user.id(), id); }
}
