package com.flowdeck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdeck.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises {@code /api/auth/**} and {@code /api/v1/me} end to end against a
 * real Postgres 16 (users) and Redis 7 (refresh tokens) — both needed here,
 * unlike {@code BoardApiIntegrationTest}, because this class is the one that
 * actually drives {@code RefreshTokenService} through HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthApiIntegrationTest {

    /**
     * Must match {@code flowdeck.auth.jwt-secret} in application-test.yml —
     * used to hand-build tokens (e.g. an already-expired one) that
     * {@code AccessTokenService} would never produce itself.
     */
    private static final String TEST_JWT_SECRET_BASE64 =
            "dGVzdC1vbmx5LXNlY3JldC1ub3QtdXNlZC1mb3ItYW55dGhpbmctcmVhbA==";
    private static final SecretKey TEST_SIGNING_KEY =
            Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_JWT_SECRET_BASE64));

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String REFRESH_URL = "/api/auth/refresh";
    private static final String LOGOUT_URL = "/api/auth/logout";
    private static final String ME_URL = "/api/v1/me";
    private static final String PASSWORD = "correct horse battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // No @ServiceConnection module for plain Redis is used here — see the
    // @DynamicPropertySource below, which wires host/port directly instead.
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        redisTemplate.execute(
                (RedisConnection connection) -> {
                    connection.serverCommands().flushDb();
                    return null;
                });
    }

    private JsonNode register(String email, String password) throws Exception {
        String body =
                mockMvc.perform(post(REGISTER_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"email": "%s", "password": "%s", "displayName": "Test User"}
                                        """
                                                .formatted(email, password)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String body =
                mockMvc.perform(post(REFRESH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"refreshToken": "%s"}
                                        """.formatted(refreshToken)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }

    // ── register ────────────────────────────────────────────────────────

    @Test
    void registerCreatesAnAccountAndReturnsATokenPair() throws Exception {
        JsonNode response = register("ada@example.com", PASSWORD);

        assertThat(response.path("accessToken").asText()).isNotBlank();
        assertThat(response.path("refreshToken").asText()).isNotBlank();
        assertThat(response.path("user").path("email").asText()).isEqualTo("ada@example.com");
    }

    @Test
    void registeringADuplicateEmailIsRejected() throws Exception {
        register("grace@example.com", PASSWORD);

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "grace@example.com", "password": "%s", "displayName": "Someone Else"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void registerNormalizesEmailCaseForDuplicateDetection() throws Exception {
        register("henry@example.com", PASSWORD);

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "HENRY@EXAMPLE.COM", "password": "%s", "displayName": "Duplicate"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isConflict());
    }

    // ── login: successful + bad credentials ────────────────────────────

    @Test
    void loginWithCorrectCredentialsSucceeds() throws Exception {
        register("bob@example.com", PASSWORD);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "bob@example.com", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.accessTokenExpiresInSeconds").value(15 * 60))
                .andExpect(jsonPath("$.user.email").value("bob@example.com"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        register("carol@example.com", PASSWORD);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "carol@example.com", "password": "wrong password entirely"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void loginWithUnknownEmailFailsTheSameWayAsWrongPassword() throws Exception {
        // Same status and body shape as a wrong password — see
        // AuthService#DUMMY_HASH_FOR_TIMING_SAFETY: the response must not
        // reveal whether the email is registered.
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@example.com", "password": "whatever"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    // ── expired access token ───────────────────────────────────────────

    @Test
    void expiredAccessTokenIsRejectedOnAProtectedEndpoint() throws Exception {
        JsonNode registered = register("dave@example.com", PASSWORD);
        String userId = registered.path("user").path("id").asText();

        String expiredToken =
                Jwts.builder()
                        .subject(userId)
                        .claim("email", "dave@example.com")
                        .issuedAt(Date.from(Instant.now().minus(20, ChronoUnit.MINUTES)))
                        .expiration(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)))
                        .signWith(TEST_SIGNING_KEY, Jwts.SIG.HS256)
                        .compact();

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Access token expired"));
    }

    @Test
    void aValidAccessTokenReachesMe() throws Exception {
        JsonNode registered = register("erin@example.com", PASSWORD);
        String accessToken = registered.path("accessToken").asText();

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("erin@example.com"));
    }

    @Test
    void meWithNoTokenIsRejected() throws Exception {
        mockMvc.perform(get(ME_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void meWithAMalformedTokenIsRejected() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt-at-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid access token"));
    }

    // ── refresh rotation ────────────────────────────────────────────────

    @Test
    void refreshingRotatesToADifferentRefreshTokenThatItselfWorks() throws Exception {
        JsonNode registered = register("frank@example.com", PASSWORD);
        String firstRefreshToken = registered.path("refreshToken").asText();

        JsonNode rotated = refresh(firstRefreshToken);
        String secondRefreshToken = rotated.path("refreshToken").asText();

        assertThat(secondRefreshToken).isNotBlank().isNotEqualTo(firstRefreshToken);
        assertThat(rotated.path("accessToken").asText()).isNotBlank();
        assertThat(rotated.path("user").path("email").asText()).isEqualTo("frank@example.com");

        // The new refresh token is itself good for a further rotation.
        JsonNode rotatedAgain = refresh(secondRefreshToken);
        assertThat(rotatedAgain.path("refreshToken").asText()).isNotEqualTo(secondRefreshToken);
    }

    @Test
    void aRotatedAwayRefreshTokenNoLongerWorksOnItsOwn() throws Exception {
        JsonNode registered = register("gina@example.com", PASSWORD);
        String firstRefreshToken = registered.path("refreshToken").asText();

        refresh(firstRefreshToken); // rotates it away

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(firstRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshingWithAnUnknownTokenIsRejected() throws Exception {
        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "not-a-real-token"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── reuse of a rotated refresh token revokes the family ────────────

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamily() throws Exception {
        JsonNode registered = register("hana@example.com", PASSWORD);
        String tokenA = registered.path("refreshToken").asText();

        // Rotate twice: A -> B -> C. B is now "rotated away" but still
        // unexpired — exactly what a captured, not-yet-used-again stolen
        // token looks like.
        String tokenB = refresh(tokenA).path("refreshToken").asText();
        String tokenC = refresh(tokenB).path("refreshToken").asText();

        // Replay the rotated-away tokenB — this is the reuse.
        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(tokenB)))
                .andExpect(status().isUnauthorized());

        // tokenC was the CURRENT, legitimate, never-reused refresh token
        // right up until tokenB's reuse a moment ago. It must now be dead
        // too — the only way that's possible is if reuse revoked the whole
        // family, not just the specific token that was replayed. A naive
        // "reject only the reused token" implementation would leave tokenC
        // working, which is exactly what this asserts against.
        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(tokenC)))
                .andExpect(status().isUnauthorized());
    }

    // ── logout ──────────────────────────────────────────────────────────

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        JsonNode registered = register("ivan@example.com", PASSWORD);
        String refreshToken = registered.path("refreshToken").asText();

        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutIsIdempotentForAnUnknownToken() throws Exception {
        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "not-a-real-token"}
                                """))
                .andExpect(status().isNoContent());
    }
}
