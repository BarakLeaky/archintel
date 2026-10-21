package io.archintel.auth;
import java.util.UUID;
/** The principal placed in the SecurityContext after a valid JWT. */
public record AuthenticatedUser(UUID id, String email) {}
