package io.archintel.project;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.archintel.auth.AuthenticatedUser;
import io.archintel.auth.JwtService;
import io.archintel.auth.SecurityConfig;
import io.archintel.common.error.ApiExceptionHandler;
import io.archintel.common.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class ProjectControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ProjectService projects;
    @MockBean JwtService jwtService;

    private final UUID userId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, "ada@example.com"), null,
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    @Test void listUsesTheAuthenticatedUsersId() throws Exception {
        when(projects.list(userId)).thenReturn(List.of());
        mvc.perform(get("/api/projects").with(authentication(auth()))).andExpect(status().isOk());
        verify(projects).list(userId);
    }

    @Test void createValidatesInput() throws Exception {
        mvc.perform(post("/api/projects").with(authentication(auth())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"description\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test void foreignOrMissingProjectIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(projects.get(userId, id)).thenThrow(new NotFoundException("Project not found"));
        mvc.perform(get("/api/projects/" + id).with(authentication(auth()))).andExpect(status().isNotFound());
    }
}
