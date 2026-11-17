package com.vitalsafe.api.account;

import com.vitalsafe.api.account.dto.AccountRequests;
import com.vitalsafe.api.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService service;
    private final boolean cookieSecure;

    public AccountController(AccountService service, @Value("${app.refresh.cookie-secure:false}") boolean cookieSecure) {
        this.service = service;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthPrincipal caller,
                                               @Valid @RequestBody AccountRequests.ChangePassword request) {
        service.changePassword(caller.userId(), request.currentPassword(), request.newPassword());
        return signedOut();
    }

    // A POST with a body, because some proxies drop the body of a DELETE
    @PostMapping("/delete")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal AuthPrincipal caller,
                                              @Valid @RequestBody AccountRequests.DeleteAccount request) {
        service.deleteAccount(caller.userId(), request.password());
        return signedOut();
    }

    // Every session was ended on the server, so the browser's refresh cookie is removed too
    private ResponseEntity<Void> signedOut() {
        ResponseCookie cleared = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }
}