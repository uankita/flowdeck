package com.flowdeck.security;

/**
 * A presented refresh token was unknown, expired, or (having already been
 * rotated away) reused — see {@link RefreshTokenService} for why all three
 * cases share one exception and one HTTP response, despite very different
 * handling internally.
 */
public class InvalidRefreshTokenException extends RuntimeException {}
