package io.archintel.research;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import io.archintel.project.ProjectService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ResearchController.class)
class ResearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectService projects;

    @MockBean
    private ResearchRunService research;

    @Test
    @WithMockUser
    void rejectsEmptyKeywords() throws Exception {
        UUID projectId = UUID.randomUUID();

        String request = """
            {
              "keywords": [],
              "maxResults": 10
            }
            """;

        mockMvc.perform(post("/api/projects/{projectId}/research", projectId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void rejectsMaxResultsOutsideAllowedRange() throws Exception {
        UUID projectId = UUID.randomUUID();

        String request = """
            {
              "keywords": ["climate"],
              "maxResults": 101
            }
            """;

        mockMvc.perform(post("/api/projects/{projectId}/research", projectId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest());
    }
}