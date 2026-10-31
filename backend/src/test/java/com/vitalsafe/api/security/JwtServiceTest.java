package com.vitalsafe.api.security;

import com.vitalsafe.api.user.Role;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static String secretOf(char fill) {
        return Base64.getEncoder().encodeToString(String.valueOf(fill).repeat(32).getBytes(StandardCharsets.UTF_8));
    }

    private static String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        return parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);
    }

    private final JwtService service = new JwtService(secretOf('a'), 900_000);

    @Test
    void aGenuineTokenGivesBackTheCaller() {
        UUID id = UUID.randomUUID();

        AuthPrincipal principal = service.parse(service.generate(id, Role.CLINICIAN)).orElseThrow();

        assertThat(principal.userId()).isEqualTo(id);
        assertThat(principal.role()).isEqualTo(Role.CLINICIAN);
    }

    @Test
    void anExpiredTokenIsRejected() {
        JwtService alreadyExpired = new JwtService(secretOf('a'), -1_000);

        String token = alreadyExpired.generate(UUID.randomUUID(), Role.PATIENT);

        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    void aTamperedSignatureIsRejected() {
        String token = service.generate(UUID.randomUUID(), Role.PATIENT);

        assertThat(service.parse(tamperSignature(token))).isEmpty();
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService(secretOf('b'), 900_000);

        assertThat(service.parse(other.generate(UUID.randomUUID(), Role.PATIENT))).isEmpty();
    }

    @Test
    void anUnsignedTokenIsRejected() {
        String unsigned = Jwts.builder().subject(UUID.randomUUID().toString()).issuer("vitalsafe").compact();

        assertThat(service.parse(unsigned)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(service.parse("not-a-token")).isEmpty();
        assertThat(service.parse("")).isEmpty();
        assertThat(service.parse("a.b.c")).isEmpty();
    }

    @Test
    void aTooShortSecretIsRefusedAtStartup() {
        String sixteenBytes = Base64.getEncoder().encodeToString("x".repeat(16).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JwtService(sixteenBytes, 1_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}