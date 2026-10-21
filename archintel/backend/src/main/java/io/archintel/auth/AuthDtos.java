package io.archintel.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 10, max = 72) String password,
        @Size(max = 64) String timezone) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserResponse(UUID id, String name, String email, String timezone) {
        static UserResponse from(User u) { return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getTimezone()); }
    }

    public record AuthResponse(String token, long expiresInSeconds, UserResponse user) {}
}
