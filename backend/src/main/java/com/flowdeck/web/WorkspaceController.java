package com.flowdeck.web;

import com.flowdeck.security.AuthenticatedUser;
import com.flowdeck.security.CurrentUser;
import com.flowdeck.service.WorkspaceService;
import com.flowdeck.web.dto.PageResponse;
import com.flowdeck.web.dto.WorkspaceDtos.CreateWorkspaceRequest;
import com.flowdeck.web.dto.WorkspaceDtos.UpdateWorkspaceRequest;
import com.flowdeck.web.dto.WorkspaceDtos.WorkspaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creating a workspace needs no {@code @PreAuthorize} role check — there's no
 * workspace yet to hold one, so any authenticated user may create one
 * (becoming its {@code OWNER}). Every other endpoint here checks membership
 * the same way {@code BoardController} does.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspaces", description = "Create and manage workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    @Operation(summary = "Create a workspace; the caller becomes its OWNER")
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @CurrentUser AuthenticatedUser currentUser, @Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceResponse created = workspaceService.createWorkspace(currentUser, request);
        return ResponseEntity.created(URI.create("/api/v1/workspaces/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List workspaces the caller belongs to")
    public PageResponse<WorkspaceResponse> listMyWorkspaces(
            @CurrentUser AuthenticatedUser currentUser,
            // Sorts "workspace.name", not "name": the query this Pageable
            // drives (WorkspaceMemberRepository#findByUserId) returns
            // Page<WorkspaceMember>, which has no "name" of its own — only
            // its nested workspace association does.
            @PageableDefault(size = 20, sort = "workspace.name") Pageable pageable) {
        return workspaceService.listForUser(currentUser, pageable);
    }

    @GetMapping("/{workspaceId}")
    @PreAuthorize(
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).VIEWER)")
    @Operation(summary = "Fetch one workspace")
    public WorkspaceResponse getWorkspace(@PathVariable UUID workspaceId) {
        return workspaceService.getWorkspace(workspaceId);
    }

    @PatchMapping("/{workspaceId}")
    @PreAuthorize(
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).ADMIN)")
    @Operation(summary = "Rename a workspace")
    public WorkspaceResponse updateWorkspace(
            @PathVariable UUID workspaceId, @Valid @RequestBody UpdateWorkspaceRequest request) {
        return workspaceService.updateWorkspace(workspaceId, request);
    }

    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).OWNER)")
    @Operation(summary = "Delete a workspace and everything in it — OWNER only")
    public void deleteWorkspace(@PathVariable UUID workspaceId) {
        workspaceService.deleteWorkspace(workspaceId);
    }
}
