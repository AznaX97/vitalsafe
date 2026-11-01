package com.vitalsafe.api.auth;

import com.vitalsafe.api.auth.dto.AuthResult;
import com.vitalsafe.api.auth.dto.LoginRequest;
import com.vitalsafe.api.auth.dto.RegisterRequest;
import com.vitalsafe.api.security.JwtService;
import com.vitalsafe.api.user.User;
import com.vitalsafe.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    // BCrypt silently ignores everything after 72 bytes
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final String consentVersion;
    private final String dummyHash;

    public AuthService(UserRepository users,
                       PasswordEncoder encoder,
                       JwtService jwt,
                       RefreshTokenService refreshTokens,
                       @Value("${app.consent.version}") String consentVersion) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.consentVersion = consentVersion;
        // Compared against when an email is unknown, so login takes about the same time either way
        this.dummyHash = encoder.encode("not-a-real-password-" + UUID.randomUUID());
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        // Validation already enforces this. It is checked again so the rule cannot be lost by accident.
        if (!Boolean.TRUE.equals(request.consent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You must accept the privacy notice to create an account");
        }
        if (tooLongForBcrypt(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long (72 bytes maximum)");
        }
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setConsentedAt(Instant.now());
        user.setConsentVersion(consentVersion);
        // The role is never taken from the request: new accounts are always patients

        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two registrations for the same email at the same moment: the database picks one winner
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        return issue(user);
    }

    public AuthResult login(LoginRequest request) {
        Optional<User> found = users.findByEmail(normalize(request.email()));

        // Always do one BCrypt comparison, whether or not the account exists
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = !tooLongForBcrypt(request.password()) && encoder.matches(request.password(), hash);

        if (found.isEmpty() || !passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return issue(found.get());
    }

    // Deliberately NOT @Transactional: when a used token is replayed, RefreshTokenService revokes every
    // session and then throws. An outer transaction here would roll that revoking back.
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        RefreshTokenService.Rotated rotated = refreshTokens.rotate(rawRefreshToken);
        User user = users.findById(rotated.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return new AuthResult(jwt.generate(user.getId(), user.getRole()), rotated.rawToken(), user.getEmail());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revoke(rawRefreshToken);
        }
    }

    private AuthResult issue(User user) {
        return new AuthResult(jwt.generate(user.getId(), user.getRole()), refreshTokens.issue(user.getId()),
                user.getEmail());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean tooLongForBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }
}