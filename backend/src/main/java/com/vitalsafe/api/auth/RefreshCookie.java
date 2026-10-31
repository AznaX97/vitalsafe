package com.vitalsafe.api.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookie {

    public static final String NAME = "refresh_token";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshCookie(@Value("${app.refresh.cookie-secure}") boolean secure,
                         @Value("${app.refresh.expiration-days}") long days) {
        this.secure = secure;
        this.maxAge = Duration.ofDays(days);
    }

    public void set(HttpServletResponse response, String value) {
        write(response, value, maxAge);
    }

    public void clear(HttpServletResponse response) {
        write(response, "", Duration.ZERO);
    }

    private void write(HttpServletResponse response, String value, Duration age) {
        ResponseCookie cookie = ResponseCookie.from(NAME, value)
                .httpOnly(true)          // page scripts cannot read it
                .secure(secure)          // HTTPS only in production
                .sameSite("Strict")      // never sent on cross-site requests
                .path("/api/v1/auth")    // only sent to the auth endpoints
                .maxAge(age)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}