package com.flowdeck.service;

import com.flowdeck.config.FlowdeckProperties;
import com.flowdeck.domain.User;
import com.flowdeck.repository.UserRepository;
import com.flowdeck.security.AccessTokenService;
import com.flowdeck.security.InvalidRefreshTokenException;
import com.flowdeck.security.RefreshTokenService;
import com.flowdeck.security.RefreshTokenService.RotationResult;
import com.flowdeck.web.dto.AuthDtos.LoginRequest;
import com.flowdeck.web.dto.AuthDtos.LogoutRequest;
import com.flowdeck.web.dto.AuthDtos.RefreshRequest;
import com.flowdeck.web.dto.AuthDtos.RegisterRequest;
import com.flowdeck.web.dto.AuthDtos.TokenPairResponse;
import com.flowdeck.web.dto.AuthDtos.UserSummaryResponse;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /**
     * A real BCrypt hash of a password nobody will ever type, computed once
     * at class-load time. {@link #login} checks against this when the email
     * is unknown, so an unknown-email login costs the same BCrypt comparison
     * as a wrong-password one — otherwise the two cases are distinguishable
     * by response time (a wrong-password check calls BCrypt; a skipped check
     * doesn't), which lets an attacker enumerate registered emails.
     */
    private static final String DUMMY_HASH_FOR_TIMING_SAFETY =
            new BCryptPasswordEncoder().encode("not-a-real-password-used-only-for-timing-safety");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final FlowdeckProperties properties;

    @Transactional
    public TokenPairResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(hashPassword(request.password()));

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Closes the race between the existsByEmail check above and this
            // insert: two concurrent registrations for the same email both
            // pass the check, and the DB's unique constraint on email is
            // what actually decides who wins.
            throw new EmailAlreadyRegisteredException(email);
        }

        log.info("Registered user {} ({})", user.getEmail(), user.getId());
        return issueTokenPair(user);
    }

    @Transactional(readOnly = true)
    public TokenPairResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        var maybeUser = userRepository.findByEmail(email).filter(User::isActive);

        // See DUMMY_HASH_FOR_TIMING_SAFETY: always run the BCrypt comparison.
        String hashToCheck = maybeUser.map(User::getPasswordHash).orElse(DUMMY_HASH_FOR_TIMING_SAFETY);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (maybeUser.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return issueTokenPair(maybeUser.get());
    }

    @Transactional(readOnly = true)
    public TokenPairResponse refresh(RefreshRequest request) {
        RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
        User user =
                userRepository
                        .findById(rotation.userId())
                        .orElseThrow(InvalidRefreshTokenException::new); // user deleted after token issue
        return toTokenPair(accessTokenService.generate(user), rotation.newRefreshToken(), user);
    }

    public void logout(LogoutRequest request) {
        refreshTokenService.revokeFamilyOf(request.refreshToken());
    }

    private TokenPairResponse issueTokenPair(User user) {
        String accessToken = accessTokenService.generate(user);
        String refreshToken = refreshTokenService.issue(user.getId());
        return toTokenPair(accessToken, refreshToken, user);
    }

    private TokenPairResponse toTokenPair(String accessToken, String refreshToken, User user) {
        long expiresInSeconds = properties.auth().accessTokenExpiryMinutes() * 60L;
        UserSummaryResponse userSummary =
                new UserSummaryResponse(user.getId(), user.getEmail(), user.getDisplayName());
        return new TokenPairResponse(accessToken, refreshToken, expiresInSeconds, userSummary);
    }

    private String hashPassword(String rawPassword) {
        try {
            return passwordEncoder.encode(rawPassword);
        } catch (IllegalArgumentException e) {
            // BCryptPasswordEncoder rejects input over 72 UTF-8 bytes. The
            // DTO's @Size(max = 72) already catches this for ASCII passwords,
            // but that annotation counts UTF-16 chars, not encoded bytes, so
            // a password full of multi-byte characters can still slip past
            // validation and land here.
            throw new InvalidPasswordException("Password is too long once encoded", e);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
