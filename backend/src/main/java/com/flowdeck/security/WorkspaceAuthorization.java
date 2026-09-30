package com.flowdeck.security;

import com.flowdeck.domain.WorkspaceRole;
import com.flowdeck.repository.WorkspaceMemberRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Backs {@code @PreAuthorize} checks against workspace roles, e.g.:
 *
 * <pre>{@code
 * @PreAuthorize("@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).MEMBER)")
 * }</pre>
 *
 * <p>Queries {@code workspace_members} fresh on every call rather than
 * trusting anything cached in the access token — see
 * {@link AccessTokenService} for why.
 */
@Component("workspaceAuthorization")
@RequiredArgsConstructor
public class WorkspaceAuthorization {

    private final WorkspaceMemberRepository workspaceMemberRepository;

    /** True if the caller holds {@code minimumRole} or a more privileged one in {@code workspaceId}. */
    public boolean hasAtLeastRole(UUID workspaceId, WorkspaceRole minimumRole) {
        AuthenticatedUser user = currentUserOrNull();
        if (user == null) {
            return false;
        }
        return workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, user.id())
                .map(member -> member.getRole().ordinal() <= minimumRole.ordinal())
                .orElse(false);
    }

    private AuthenticatedUser currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
