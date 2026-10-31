package com.vitalsafe.api.user;

import com.vitalsafe.api.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    public record MeResponse(UUID id, String email, Role role, Instant consentedAt, String consentVersion) {}

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        // A valid token for an account that no longer exists must not work
        User user = users.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return new MeResponse(user.getId(), user.getEmail(), user.getRole(),
                user.getConsentedAt(), user.getConsentVersion());
    }
}