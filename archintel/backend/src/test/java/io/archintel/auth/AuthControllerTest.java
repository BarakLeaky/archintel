package io.archintel.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.archintel.auth.AuthDtos.AuthResponse;
import io.archintel.auth.AuthDtos.UserResponse;
import io.archintel.common.error.ApiExceptionHandler;
import io.archintel.common.error.ConflictException;
import io.archintel.common.error.InvalidCredentialsException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AuthService authService;
    @MockBean JwtService jwtService;

    @Test void registerReturns201WithTokenWithoutAuthentication() throws Exception {
        var user = new UserResponse(UUID.randomUUID(), "Ada", "ada@example.com", "UTC");
        when(authService.register(any())).thenReturn(new AuthResponse("tok", 7200, user));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"password\":\"correct horse battery\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").value("tok"))
            .andExpect(jsonPath("$.user.email").value("ada@example.com"));
    }

    @Test void registerValidationErrorsUseTheCommonErrorShape() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors.email").exists())
            .andExpect(jsonPath("$.fieldErrors.password").exists())
            .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test void duplicateEmailIs409() throws Exception {
        when(authService.register(any())).thenThrow(new ConflictException("An account with this email already exists"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"password\":\"correct horse battery\"}"))
            .andExpect(status().isConflict());
    }

    @Test void badLoginIs401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ada@example.com\",\"password\":\"nope\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }
}
