package com.flowdeck.service;

import com.flowdeck.domain.Workspace;
import com.flowdeck.domain.WorkspaceMember;
import com.flowdeck.domain.WorkspaceRole;
import com.flowdeck.repository.UserRepository;
import com.flowdeck.repository.WorkspaceMemberRepository;
import com.flowdeck.repository.WorkspaceRepository;
import com.flowdeck.security.AuthenticatedUser;
import com.flowdeck.web.dto.PageResponse;
import com.flowdeck.web.dto.WorkspaceDtos.CreateWorkspaceRequest;
import com.flowdeck.web.dto.WorkspaceDtos.UpdateWorkspaceRequest;
import com.flowdeck.web.dto.WorkspaceDtos.WorkspaceResponse;
import com.flowdeck.web.mapper.WorkspaceMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceMapper workspaceMapper;

    /** The creator becomes {@link WorkspaceRole#OWNER} — there's no other way to acquire that role. */
    @Transactional
    public WorkspaceResponse createWorkspace(AuthenticatedUser creator, CreateWorkspaceRequest request) {
        if (workspaceRepository.existsBySlug(request.slug())) {
            throw new DuplicateWorkspaceSlugException(request.slug());
        }

        Workspace workspace = new Workspace();
        workspace.setSlug(request.slug());
        workspace.setName(request.name());
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember owner = new WorkspaceMember();
        owner.setWorkspace(workspace);
        // A reference, not a findById: we already know this id is valid (it's
        // the verified caller), so there's no reason to spend a SELECT on it.
        owner.setUser(userRepository.getReferenceById(creator.id()));
        owner.setRole(WorkspaceRole.OWNER);
        workspaceMemberRepository.save(owner);

        log.info("Created workspace {} ({}), owner {}", workspace.getSlug(), workspace.getId(), creator.id());
        return workspaceMapper.toResponse(workspace);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkspaceResponse> listForUser(AuthenticatedUser user, Pageable pageable) {
        Page<WorkspaceResponse> page =
                workspaceMemberRepository
                        .findByUserId(user.id(), pageable)
                        .map(WorkspaceMember::getWorkspace)
                        .map(workspaceMapper::toResponse);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(UUID workspaceId) {
        return workspaceRepository
                .findById(workspaceId)
                .map(workspaceMapper::toResponse)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request) {
        Workspace workspace =
                workspaceRepository.findById(workspaceId).orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
        workspace.setName(request.name());
        return workspaceMapper.toResponse(workspaceRepository.save(workspace));
    }

    /** Hard delete — cascades to every board/list/card/member in this workspace via the schema's {@code ON DELETE CASCADE}. */
    @Transactional
    public void deleteWorkspace(UUID workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new WorkspaceNotFoundException(workspaceId);
        }
        workspaceRepository.deleteById(workspaceId);
        log.info("Deleted workspace {}", workspaceId);
    }
}
