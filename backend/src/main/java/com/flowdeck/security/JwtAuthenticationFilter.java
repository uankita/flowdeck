package com.flowdeck.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <token>}, verifies it, and populates the
 * {@link SecurityContextHolder} for the rest of the request.
 *
 * <p>Never rejects a request itself: on a missing header it simply continues
 * with no {@code Authentication}, and on an invalid/expired token it records
 * why (see {@link #AUTH_ERROR_ATTRIBUTE}) but still continues. Either way,
 * {@code authorizeHttpRequests()} — not this filter — is what turns "no
 * Authentication" into a 401 for a protected path, via
 * {@link JwtAuthenticationEntryPoint}. This split matters because a filter's
 * own exceptions never reach {@code GlobalExceptionHandler} (that only wraps
 * DispatcherServlet), so throwing here would produce Tomcat's default error
 * page, not a clean {@code ProblemDetail}.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute {@link JwtAuthenticationEntryPoint} reads for a specific 401 message. */
    public static final String AUTH_ERROR_ATTRIBUTE = "flowdeck.auth.error";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenService accessTokenService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                AccessTokenClaims claims = accessTokenService.parse(token);
                authenticate(request, claims);
            } catch (InvalidAccessTokenException e) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, AccessTokenClaims claims) {
        AuthenticatedUser principal = new AuthenticatedUser(claims.userId(), claims.email());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
