package com.flowdeck.security;

import java.util.UUID;

/**
 * The {@code Authentication#getPrincipal()} set by {@link JwtAuthenticationFilter}
 * once an access token has been verified. Deliberately minimal — just enough
 * identity to resolve a {@code @CurrentUser} parameter or look up workspace
 * membership; anything else about the user is a database read away via
 * {@link #id()}, not baked into the token.
 */
public record AuthenticatedUser(UUID id, String email) {}
