package com.vitalsafe.api.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AccountRequests {

    private AccountRequests() {
    }

    public record ForgotPassword(
            @NotBlank(message = "email is required") @Size(max = 254, message = "email is too long") String email) {}

    public record ResetPassword(
            @NotBlank(message = "token is required") @Size(max = 200, message = "token is too long") String token,
            @NotBlank(message = "newPassword is required") @Size(max = 200, message = "newPassword is too long") String newPassword) {}

    public record ChangePassword(
            @NotBlank(message = "currentPassword is required") @Size(max = 200, message = "currentPassword is too long") String currentPassword,
            @NotBlank(message = "newPassword is required") @Size(max = 200, message = "newPassword is too long") String newPassword) {}

    public record DeleteAccount(
            @NotBlank(message = "password is required") @Size(max = 200, message = "password is too long") String password) {}
}