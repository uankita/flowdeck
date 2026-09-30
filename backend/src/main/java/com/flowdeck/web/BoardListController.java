package com.flowdeck.web;

import com.flowdeck.service.BoardListService;
import com.flowdeck.web.dto.BoardDtos.BoardListResponse;
import com.flowdeck.web.dto.BoardListDtos.CreateBoardListRequest;
import com.flowdeck.web.dto.BoardListDtos.MoveBoardListRequest;
import com.flowdeck.web.dto.BoardListDtos.UpdateBoardListRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code GET} here: a board's lists (with their cards) are already fully
 * available from {@code BoardController#getBoard}, and a separate flat
 * listing would just be a redundant, less useful view of the same data.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/boards/{boardKey}/lists")
@RequiredArgsConstructor
@Tag(name = "Lists", description = "Create, update, move, and delete a board's lists")
public class BoardListController {

    private static final String MEMBER_OR_ABOVE =
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).MEMBER)";

    private final BoardListService boardListService;

    @PostMapping
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Create a list at the end of a board")
    public BoardListResponse createList(
            @PathVariable UUID workspaceId,
            @PathVariable String boardKey,
            @Valid @RequestBody CreateBoardListRequest request) {
        return boardListService.createList(workspaceId, boardKey, request);
    }

    @PatchMapping("/{listId}")
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(
            summary = "Update a list",
            description = "Optimistic locking: `version` must match the list's current version, "
                    + "or this returns 409 with the list's current state in the body.")
    public BoardListResponse updateList(
            @PathVariable UUID workspaceId,
            @PathVariable String boardKey,
            @PathVariable UUID listId,
            @Valid @RequestBody UpdateBoardListRequest request) {
        return boardListService.updateList(workspaceId, boardKey, listId, request);
    }

    @DeleteMapping("/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Delete a list and its cards")
    public void deleteList(
            @PathVariable UUID workspaceId, @PathVariable String boardKey, @PathVariable UUID listId) {
        boardListService.deleteList(workspaceId, boardKey, listId);
    }

    @PatchMapping("/{listId}/move")
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Reorder a list, landing it between two given neighbours")
    public BoardListResponse moveList(
            @PathVariable UUID workspaceId,
            @PathVariable String boardKey,
            @PathVariable UUID listId,
            @Valid @RequestBody MoveBoardListRequest request) {
        return boardListService.moveList(workspaceId, boardKey, listId, request);
    }
}
