package io.archintel.project;

import io.archintel.common.error.NotFoundException;
import io.archintel.project.ProjectDtos.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private final ResearchProjectRepository repo;

    public ProjectService(ResearchProjectRepository repo) { this.repo = repo; }

    @Transactional
    public ProjectResponse create(UUID userId, CreateProjectRequest req) {
        var p = new ResearchProject(userId, req.title().trim(), req.description().trim(),
            blankToNull(req.targetUsers()), blankToNull(req.requirements()));
        return ProjectResponse.from(repo.save(p));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(ProjectResponse::from).toList();
    }

    /** Someone else's project and a nonexistent project are indistinguishable: both are 404. */
    @Transactional(readOnly = true)
    public ProjectResponse get(UUID userId, UUID id) {
        return repo.findByIdAndUserId(id, userId).map(ProjectResponse::from)
            .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repo.delete(repo.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Project not found")));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
