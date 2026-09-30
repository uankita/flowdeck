package com.flowdeck.web;

import com.flowdeck.service.AuthService;
import com.flowdeck.web.dto.AuthDtos.LoginRequest;
import com.flowdeck.web.dto.AuthDtos.LogoutRequest;
import com.flowdeck.web.dto.AuthDtos.RefreshRequest;
import com.flowdeck.web.dto.AuthDtos.RegisterRequest;
import com.flowdeck.web.dto.AuthDtos.TokenPairResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entirely {@code permitAll()} in {@code SecurityConfig} — none of these
 * endpoints go through {@code JwtAuthenticationFilter}. Register/login have
 * no token yet; refresh/logout carry their own credential (the refresh
 * token) in the request body rather than an {@code Authorization} header.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register, log in, and manage refresh sessions")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Create an account and receive a token pair")
    public ResponseEntity<TokenPairResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a token pair")
    public TokenPairResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a new token pair")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token's session (and its whole rotation family)")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
    }
}
