package com.vitalsafe.api.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 12, max = 72, message = "Password must be 12 to 72 characters") String password,
        @NotNull(message = "You must accept the privacy notice to create an account")
        @AssertTrue(message = "You must accept the privacy notice to create an account")
        Boolean consent
) {}