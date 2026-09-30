package com.flowdeck.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request/response payloads for {@code /api/auth/**} and {@code /api/v1/me}. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            // 72 is BCrypt's hard input limit (in UTF-8 bytes, not chars — see
            // AuthService for the defensive catch this doesn't fully cover).
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 120) String displayName) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    public record UserSummaryResponse(UUID id, String email, String displayName) {}

    public record TokenPairResponse(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresInSeconds,
            UserSummaryResponse user) {}
}
