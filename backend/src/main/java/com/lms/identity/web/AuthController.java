package com.lms.identity.web;

import com.lms.identity.security.AuthPrincipal;
import com.lms.identity.service.AuthService;
import com.lms.identity.service.SessionService.ClientInfo;
import com.lms.identity.web.Dtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        return auth.verifyEmail(req.token());
    }

    @PostMapping("/verify-mobile")
    public MessageResponse verifyMobile(@Valid @RequestBody VerifyMobileRequest req) {
        return auth.verifyMobile(req);
    }

    @PostMapping("/resend-verification")
    public MessageResponse resend(@Valid @RequestBody ResendRequest req) {
        return auth.resendVerification(req);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req, client(http));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return auth.refresh(req.refreshToken(), client(http));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest req) {
        auth.logout(req == null ? null : req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        return auth.forgotPassword(req.email());
    }

    @PostMapping("/reset-password")
    public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest req) {
        return auth.resetPassword(req.token(), req.newPassword());
    }

    @PostMapping("/change-password")
    public MessageResponse change(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody ChangePasswordRequest req) {
        return auth.changePassword(me.userId(), req);
    }

    static ClientInfo client(HttpServletRequest http) {
        return new ClientInfo(http.getHeader("X-Device-Name"), http.getHeader("User-Agent"), http.getRemoteAddr(),
                http.getHeader("X-Device-Id"));
    }
}
