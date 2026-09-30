package com.flowdeck.security;

import com.flowdeck.config.FlowdeckProperties;
import com.flowdeck.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies short-lived JWT access tokens.
 *
 * <p>Deliberately carries only identity ({@code sub}, {@code email}) as
 * claims — no roles or permissions. Workspace roles can change at any
 * moment (someone gets removed mid-session) and a 15-minute token is long
 * enough for stale embedded roles to matter; {@code WorkspaceAuthorization}
 * re-checks membership from Postgres on every request instead, trading one
 * indexed query for correctness.
 *
 * <p>Signed with HS256, which requires a key of at least 256 bits — the
 * {@code @NotBlank} on {@code flowdeck.auth.jwt-secret} guards presence but
 * not length, so a too-short secret still fails, just at first use
 * ({@link Keys#hmacShaKeyFor}) rather than at startup.
 */
@Service
public class AccessTokenService {

    private static final String CLAIM_EMAIL = "email";

    private final SecretKey signingKey;
    private final Duration expiry;

    public AccessTokenService(FlowdeckProperties properties) {
        byte[] secretBytes = Base64.getDecoder().decode(properties.auth().jwtSecret());
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expiry = Duration.ofMinutes(properties.auth().accessTokenExpiryMinutes());
    }

    public String generate(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** @throws InvalidAccessTokenException if the token is expired, malformed, or fails signature verification */
    public AccessTokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            return new AccessTokenClaims(UUID.fromString(claims.getSubject()), claims.get(CLAIM_EMAIL, String.class));
        } catch (ExpiredJwtException e) {
            throw new InvalidAccessTokenException("Access token expired", e);
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException covers a syntactically valid-but-wrong
            // subject (not a UUID) as well as jjwt's own malformed-input case.
            throw new InvalidAccessTokenException("Invalid access token", e);
        }
    }
}
