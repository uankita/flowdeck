package com.flowdeck.web;

import com.flowdeck.domain.User;
import com.flowdeck.repository.UserRepository;
import com.flowdeck.security.AuthenticatedUser;
import com.flowdeck.security.CurrentUser;
import com.flowdeck.web.dto.AuthDtos.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Smallest possible proof that {@link CurrentUser} resolves end to end. */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Me", description = "The authenticated caller's own profile")
public class MeController {

    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Fetch the authenticated user's own profile")
    public UserSummaryResponse me(@CurrentUser AuthenticatedUser currentUser) {
        User user =
                userRepository
                        .findById(currentUser.id())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Authenticated user no longer exists: " + currentUser.id()));
        return new UserSummaryResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
