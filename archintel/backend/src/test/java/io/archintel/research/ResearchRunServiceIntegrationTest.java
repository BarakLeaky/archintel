package io.archintel.research;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "archintel.jwt.secret=test-secret-for-integration-tests-32-bytes-minimum"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ResearchRunServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ResearchRunService researchRunService;

    @Autowired
    private SourceRepository sourceRepository;

    @Test
    void researchRunPersistsDiscoveredSources() {
        ResearchQuery query = new ResearchQuery(
                List.of("archintel"),
                Instant.now().minusSeconds(7 * 24 * 60 * 60),
                20
        );

        ResearchRunResult result = researchRunService.run(query);

        assertThat(result).isNotNull();
        assertThat(result.connectorsUsed()).isGreaterThanOrEqualTo(0);
        assertThat(result.sourcesFound()).isGreaterThanOrEqualTo(0);
        assertThat(result.items()).isNotNull();

        if (!result.items().isEmpty()) {
            assertThat(sourceRepository.count()).isGreaterThan(0);

            ResearchRunResult.ResearchResultItem first =
                    result.items().get(0);

            assertThat(first.canonicalUrl()).isNotBlank();
            assertThat(first.newness()).isNotNull();
        }
    }
}