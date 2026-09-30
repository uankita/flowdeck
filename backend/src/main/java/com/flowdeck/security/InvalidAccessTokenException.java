package com.flowdeck.security;

/**
 * An access token failed verification: expired, malformed, or signed with
 * the wrong key. Thrown by {@link AccessTokenService#parse}, caught inside
 * {@link JwtAuthenticationFilter} — never reaches {@code GlobalExceptionHandler},
 * since filters run outside DispatcherServlet's exception-resolving scope.
 */
public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
