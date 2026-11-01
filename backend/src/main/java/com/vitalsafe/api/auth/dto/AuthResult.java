package com.vitalsafe.api.auth.dto;

/** Internal only: carries the refresh token from the service to the controller, which puts it in a cookie. */
public record AuthResult(String accessToken, String refreshToken, String email) {}