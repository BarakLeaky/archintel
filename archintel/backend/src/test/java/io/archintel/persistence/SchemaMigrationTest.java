package io.archintel.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.archintel.auth.User;
import io.archintel.auth.UserRepository;
import io.archintel.project.ResearchProject;
import io.archintel.project.ResearchProjectRepository;
import io.archintel.project.Visibility;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs Flyway V1+V2 against a real PostgreSQL and lets Hibernate validate the entities against it
 * (spring.jpa.hibernate.ddl-auto=validate). Skipped automatically when Docker is not available.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class SchemaMigrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired UserRepository users;
    @Autowired ResearchProjectRepository projects;

    @Test void migrationsApplyEntitiesMatchSchemaAndOwnershipQueryIsolatesUsers() {
        User ada = users.saveAndFlush(new User("Ada", "ada@example.com", "x".repeat(60), "Africa/Nairobi"));
        ResearchProject p = projects.saveAndFlush(new ResearchProject(ada.getId(), "Swahili ASR", "desc", null, null));

        assertTrue(users.findByEmail("ada@example.com").isPresent());
        assertTrue(users.existsByEmail("ada@example.com"));
        assertEquals(Visibility.PRIVATE, projects.findByIdAndUserId(p.getId(), ada.getId()).orElseThrow().getVisibility());
        assertTrue(projects.findByIdAndUserId(p.getId(), UUID.randomUUID()).isEmpty());
    }
}
