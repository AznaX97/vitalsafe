package com.vitalsafe.api.auth;

import com.vitalsafe.api.auth.dto.AuthResponse;
import com.vitalsafe.api.auth.dto.AuthResult;
import com.vitalsafe.api.auth.dto.LoginRequest;
import com.vitalsafe.api.auth.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie cookie;

    public AuthController(AuthService authService, RefreshCookie cookie) {
        this.authService = authService;
        this.cookie = cookie;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return respond(authService.register(request), response);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return respond(authService.login(request), response);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
                                HttpServletResponse response) {
        try {
            return respond(authService.refresh(refreshToken), response);
        } catch (ResponseStatusException e) {
            cookie.clear(response);   // a dead cookie is removed, so the browser stops sending it
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
                       HttpServletResponse response) {
        authService.logout(refreshToken);
        cookie.clear(response);
    }

    // The refresh token goes only into the cookie, never into the JSON body
    private AuthResponse respond(AuthResult result, HttpServletResponse response) {
        cookie.set(response, result.refreshToken());
        return new AuthResponse(result.accessToken(), result.email());
    }
}