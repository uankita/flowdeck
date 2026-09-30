package com.flowdeck.security;

import com.flowdeck.config.FlowdeckProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Issues, rotates, and revokes refresh tokens in Redis. Refresh tokens are
 * opaque high-entropy strings, not JWTs: they carry no information of their
 * own and are meaningless without a Redis lookup, so there's nothing to gain
 * from making them self-describing, and a lot to gain from being able to
 * revoke one (or a whole family) by simply deleting a key.
 *
 * <h2>Rotation and reuse detection</h2>
 *
 * Every refresh token belongs to a <b>family</b>: the chain of tokens
 * descending from one login. {@link #issue} starts a new family; every
 * subsequent {@link #rotate} call replaces the family's current token with a
 * new one and marks the old one {@code ROTATED} — not deleted, so that if it
 * is ever presented again, that's distinguishable from an ordinary
 * unknown/expired token. It can only mean one of two things: the legitimate
 * client retried a request after the response was lost (rare, and
 * indistinguishable from the second case), or an attacker who captured that
 * token earlier is using it now. Since those can't be told apart, presenting
 * a {@code ROTATED} token {@link #rotate revokes the entire family} — every
 * token descended from that login, including whatever is currently the
 * legitimate active one — forcing a full re-login rather than trusting that
 * the most recent link in the chain is still in the right hands.
 *
 * <h2>Redis layout</h2>
 *
 * <pre>
 * auth:refresh:{token}          HASH  {userId, familyId, status}   TTL = refresh-token-expiry-days
 * auth:refresh:family:{familyId} SET   member = token               TTL = refresh-token-expiry-days, slides forward on every rotation
 * </pre>
 *
 * The family set exists purely so {@link #revokeFamily} can find every
 * token — active or already-rotated — belonging to a family without
 * scanning the keyspace.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final String FAMILY_KEY_PREFIX = "auth:refresh:family:";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_FAMILY_ID = "familyId";
    private static final String FIELD_STATUS = "status";

    private enum Status {
        ACTIVE,
        ROTATED
    }

    public record RotationResult(UUID userId, String newRefreshToken) {}

    private final StringRedisTemplate redis;
    private final FlowdeckProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Starts a brand-new family — call this on login/register, never on rotation. */
    public String issue(UUID userId) {
        return issueInFamily(userId, UUID.randomUUID().toString());
    }

    /**
     * Validates {@code presentedToken}, then rotates it: the presented token
     * is marked spent and a new one is issued in the same family.
     *
     * @throws InvalidRefreshTokenException if the token is unknown/expired,
     *     or if it had already been rotated away — in the latter case, as a
     *     side effect, every token in its family is revoked (see class Javadoc)
     */
    public RotationResult rotate(String presentedToken) {
        String key = tokenKey(presentedToken);
        Map<Object, Object> record = redis.opsForHash().entries(key);
        if (record.isEmpty()) {
            throw new InvalidRefreshTokenException();
        }

        String familyId = (String) record.get(FIELD_FAMILY_ID);
        UUID userId = UUID.fromString((String) record.get(FIELD_USER_ID));

        if (Status.ROTATED.name().equals(record.get(FIELD_STATUS))) {
            log.warn("Refresh token reuse detected for family {} — revoking the whole family", familyId);
            revokeFamily(familyId);
            throw new InvalidRefreshTokenException();
        }

        // Mark spent rather than delete — see class Javadoc on why a rotated
        // token needs to keep existing (as a tripwire) until its TTL expires.
        redis.opsForHash().put(key, FIELD_STATUS, Status.ROTATED.name());

        return new RotationResult(userId, issueInFamily(userId, familyId));
    }

    /**
     * Revokes the family {@code presentedToken} belongs to. Used for logout.
     * Silently does nothing for an unknown token, so logout stays idempotent
     * and doesn't leak whether a token was ever valid.
     */
    public void revokeFamilyOf(String presentedToken) {
        Map<Object, Object> record = redis.opsForHash().entries(tokenKey(presentedToken));
        if (record.isEmpty()) {
            return;
        }
        revokeFamily((String) record.get(FIELD_FAMILY_ID));
    }

    private String issueInFamily(UUID userId, String familyId) {
        String token = generateOpaqueToken();
        Duration ttl = Duration.ofDays(properties.auth().refreshTokenExpiryDays());

        String tokenKey = tokenKey(token);
        redis.opsForHash()
                .putAll(
                        tokenKey,
                        Map.of(
                                FIELD_USER_ID, userId.toString(),
                                FIELD_FAMILY_ID, familyId,
                                FIELD_STATUS, Status.ACTIVE.name()));
        redis.expire(tokenKey, ttl);

        String familyKey = familyKey(familyId);
        redis.opsForSet().add(familyKey, token);
        redis.expire(familyKey, ttl); // slide the family's TTL forward on every use

        return token;
    }

    private void revokeFamily(String familyId) {
        String familyKey = familyKey(familyId);
        Set<String> tokens = redis.opsForSet().members(familyKey);
        if (tokens != null && !tokens.isEmpty()) {
            redis.delete(tokens.stream().map(this::tokenKey).toList());
        }
        redis.delete(familyKey);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    private String familyKey(String familyId) {
        return FAMILY_KEY_PREFIX + familyId;
    }
}
