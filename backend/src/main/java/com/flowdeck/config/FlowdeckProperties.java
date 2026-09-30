package com.flowdeck.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings under the {@code flowdeck.*} prefix.
 *
 * <p>Validated at startup, so a prod deployment missing {@code JWT_SECRET}
 * fails immediately rather than on the first login attempt.
 */
@ConfigurationProperties(prefix = "flowdeck")
@Validated
public record FlowdeckProperties(@NotNull @Valid Auth auth, @NotNull @Valid Cors cors) {

    public record Auth(
            @NotBlank(message = "flowdeck.auth.jwt-secret must be set (env JWT_SECRET)")
            String jwtSecret,
            @Positive int jwtExpiryMinutes) {}

    public record Cors(
            @NotEmpty(message = "at least one allowed origin is required")
            List<String> allowedOrigins) {}
}
