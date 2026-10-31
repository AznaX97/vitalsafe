package com.vitalsafe.api.security;

import com.vitalsafe.api.user.Role;

import java.util.UUID;

/** Who is calling: taken from a verified access token, never from the request body. */
public record AuthPrincipal(UUID userId, Role role) {}