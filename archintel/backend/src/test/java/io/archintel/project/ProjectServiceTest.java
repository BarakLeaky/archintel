package io.archintel.project;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.archintel.common.error.NotFoundException;
import io.archintel.project.ProjectDtos.CreateProjectRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectServiceTest {
    private final ResearchProjectRepository repo = mock(ResearchProjectRepository.class);
    private final ProjectService service = new ProjectService(repo);
    private final UUID owner = UUID.randomUUID();
    private final UUID intruder = UUID.randomUUID();

    @Test void createTrimsAndStoresOwner() {
        when(repo.save(any(ResearchProject.class))).thenAnswer(i -> i.getArgument(0));
        var res = service.create(owner, new CreateProjectRequest("  Swahili ASR ", " Build it ", "  ", null));
        assertEquals("Swahili ASR", res.title());
        assertEquals("Build it", res.description());
        assertNull(res.targetUsers());
        assertEquals(Visibility.PRIVATE, res.visibility());
    }

    @Test void ownerCanReadOwnProject() {
        var p = new ResearchProject(owner, "t", "d", null, null);
        when(repo.findByIdAndUserId(p.getId(), owner)).thenReturn(Optional.of(p));
        assertEquals(p.getId(), service.get(owner, p.getId()).id());
    }

    @Test void otherUsersGetNotFoundNotForbidden() {
        var p = new ResearchProject(owner, "t", "d", null, null);
        when(repo.findByIdAndUserId(p.getId(), owner)).thenReturn(Optional.of(p));
        when(repo.findByIdAndUserId(p.getId(), intruder)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.get(intruder, p.getId()));
    }

    @Test void nonOwnerCannotDelete() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndUserId(id, intruder)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.delete(intruder, id));
        verify(repo, never()).delete(any(ResearchProject.class));
    }
}
