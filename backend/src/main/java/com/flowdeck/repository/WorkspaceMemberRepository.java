package com.flowdeck.repository;

import com.flowdeck.domain.WorkspaceMember;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    /** Backs {@code com.flowdeck.security.WorkspaceAuthorization} — the hot path for every authorized request. */
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /**
     * "My workspaces" — join-fetches {@code workspace} so mapping each page
     * element doesn't cost a lazy load per row.
     */
    @EntityGraph(attributePaths = "workspace")
    Page<WorkspaceMember> findByUserId(UUID userId, Pageable pageable);
}
