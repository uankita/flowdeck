package com.flowdeck.repository;

import com.flowdeck.domain.WorkspaceMember;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    /** Backs {@code com.flowdeck.security.WorkspaceAuthorization} — the hot path for every authorized request. */
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
