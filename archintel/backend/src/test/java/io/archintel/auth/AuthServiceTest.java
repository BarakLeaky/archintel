package io.archintel.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.archintel.auth.AuthDtos.LoginRequest;
import io.archintel.auth.AuthDtos.RegisterRequest;
import io.archintel.common.error.BadRequestException;
import io.archintel.common.error.ConflictException;
import io.archintel.common.error.InvalidCredentialsException;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    private final UserRepository repo = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost: tests only
    private final JwtService jwt = new JwtService(new JwtProperties("0123456789abcdef0123456789abcdef-test", 60), Clock.systemUTC());
    private final AuthService service = new AuthService(repo, encoder, jwt);

    @Test void registerNormalisesEmailHashesPasswordAndIssuesUsableToken() {
        when(repo.existsByEmail("ada@example.com")).thenReturn(false);
        when(repo.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

        var res = service.register(new RegisterRequest(" Ada ", "  Ada@Example.COM ", "correct horse battery", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repo).saveAndFlush(saved.capture());
        assertEquals("ada@example.com", saved.getValue().getEmail());
        assertEquals("Ada", saved.getValue().getName());
        assertEquals("UTC", saved.getValue().getTimezone());
        assertNotEquals("correct horse battery", saved.getValue().getPasswordHash());
        assertTrue(encoder.matches("correct horse battery", saved.getValue().getPasswordHash()));
        assertEquals(saved.getValue().getId(), jwt.parse(res.token()).orElseThrow().id());
    }

    @Test void registerRejectsDuplicateEmail() {
        when(repo.existsByEmail("ada@example.com")).thenReturn(true);
        assertThrows(ConflictException.class,
            () -> service.register(new RegisterRequest("Ada", "ada@example.com", "correct horse battery", "UTC")));
    }

    @Test void registerRejectsUnknownTimezoneAndOversizedPasswordBytes() {
        assertThrows(BadRequestException.class,
            () -> service.register(new RegisterRequest("Ada", "ada@example.com", "correct horse battery", "Mars/Olympus")));
        // 40 chars x 3 bytes = 120 bytes: passes @Size(max=72) on characters but exceeds BCrypt's byte limit
        assertThrows(BadRequestException.class,
            () -> service.register(new RegisterRequest("Ada", "ada@example.com", "€".repeat(40), "UTC")));
    }

    @Test void loginSucceedsWithCorrectPassword() {
        User u = new User("Ada", "ada@example.com", encoder.encode("correct horse battery"), "UTC");
        when(repo.findByEmail("ada@example.com")).thenReturn(Optional.of(u));
        var res = service.login(new LoginRequest("ADA@example.com", "correct horse battery"));
        assertEquals(u.getId(), res.user().id());
    }

    @Test void loginFailsIdenticallyForWrongPasswordAndUnknownEmail() {
        User u = new User("Ada", "ada@example.com", encoder.encode("correct horse battery"), "UTC");
        when(repo.findByEmail("ada@example.com")).thenReturn(Optional.of(u));
        when(repo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        var wrong = assertThrows(InvalidCredentialsException.class,
            () -> service.login(new LoginRequest("ada@example.com", "wrong password!")));
        var unknown = assertThrows(InvalidCredentialsException.class,
            () -> service.login(new LoginRequest("nobody@example.com", "whatever password")));
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }
}
