package io.archintel.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, unique = true, length = 255) private String email;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 64) private String timezone;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected User() {}

    public User(String name, String email, String passwordHash, String timezone) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.timezone = timezone;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getTimezone() { return timezone; }
    public Instant getCreatedAt() { return createdAt; }
}
