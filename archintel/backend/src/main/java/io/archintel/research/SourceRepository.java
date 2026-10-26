package io.archintel.research;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, UUID> {

    Optional<Source> findByCanonicalUrl(String canonicalUrl);
}
