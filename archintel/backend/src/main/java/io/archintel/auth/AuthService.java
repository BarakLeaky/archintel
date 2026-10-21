package io.archintel.auth;

import io.archintel.auth.AuthDtos.*;
import io.archintel.common.error.BadRequestException;
import io.archintel.common.error.ConflictException;
import io.archintel.common.error.InvalidCredentialsException;
import io.archintel.common.error.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** Compared against when the email is unknown so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("archintel-dummy-password");
    }

    public AuthResponse register(RegisterRequest req) {
        String email = normalise(req.email());
        if (req.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password must be at most 72 bytes (BCrypt limit)");
        }
        String tz = req.timezone() == null || req.timezone().isBlank() ? "UTC" : req.timezone().trim();
        if (!ZoneId.getAvailableZoneIds().contains(tz)) throw new BadRequestException("Unknown timezone: " + tz);
        if (users.existsByEmail(email)) throw new ConflictException("An account with this email already exists");

        User user = new User(req.name().trim(), email, encoder.encode(req.password()), tz);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) { // lost a race with a concurrent registration
            throw new ConflictException("An account with this email already exists");
        }
        return respond(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(normalise(req.email())).orElse(null);
        boolean ok = encoder.matches(req.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !ok) throw new InvalidCredentialsException();
        return respond(user);
    }

    public UserResponse currentUser(UUID id) {
        return users.findById(id).map(UserResponse::from).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private AuthResponse respond(User u) {
        return new AuthResponse(jwt.issue(u.getId(), u.getEmail()), jwt.ttl().toSeconds(), UserResponse.from(u));
    }

    private static String normalise(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
