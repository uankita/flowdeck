package com.flowdeck.security;

import java.util.UUID;

/** Result of successfully verifying and parsing an access token. */
public record AccessTokenClaims(UUID userId, String email) {}
