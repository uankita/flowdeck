package com.flowdeck.web;

import com.flowdeck.service.BoardService;
import com.flowdeck.web.dto.BoardDtos.BoardDetailResponse;
import com.flowdeck.web.dto.BoardDtos.BoardSummaryResponse;
import com.flowdeck.web.dto.BoardDtos.CreateBoardRequest;
import com.flowdeck.web.dto.BoardDtos.UpdateBoardRequest;
import com.flowdeck.web.dto.PageResponse;
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

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/boards")
@RequiredArgsConstructor
@Tag(name = "Boards", description = "Create and read Flowdeck boards within a workspace")
public class BoardController {

    private final BoardService boardService;

    @GetMapping
    @PreAuthorize(
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).VIEWER)")
    @Operation(summary = "List all non-archived boards in a workspace")
    public PageResponse<BoardSummaryResponse> listBoards(
            @PathVariable UUID workspaceId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return boardService.listActiveBoards(workspaceId, pageable);
    }

    @GetMapping("/{boardKey}")
    @PreAuthorize(
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).VIEWER)")
    @Operation(summary = "Fetch one board with its lists and cards")
    public BoardDetailResponse getBoard(
            @PathVariable UUID workspaceId, @PathVariable String boardKey) {
        return boardService.getBoard(workspaceId, boardKey);
    }

    @PostMapping
    @PreAuthorize(
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).MEMBER)")
    @Operation(summary = "Create a board, seeded with the default lists")
    public ResponseEntity<BoardDetailResponse> createBoard(
            @PathVariable UUID workspaceId, @Valid @RequestBody CreateBoardRequest request) {
        BoardDetailResponse created = boardService.createBoard(workspaceId, request);
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/workspaces/" + workspaceId + "/boards/" + created.boardKey()))
                .body(created);
    }

    @PatchMapping("/{boardKey}")
    @PreAuthorize(
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).MEMBER)")
    @Operation(summary = "Rename, redescribe, or (un)archive a board")
    public BoardDetailResponse updateBoard(
            @PathVariable UUID workspaceId,
            @PathVariable String boardKey,
            @Valid @RequestBody UpdateBoardRequest request) {
        return boardService.updateBoard(workspaceId, boardKey, request);
    }

    @DeleteMapping("/{boardKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).MEMBER)")
    @Operation(summary = "Soft-delete a board")
    public void deleteBoard(@PathVariable UUID workspaceId, @PathVariable String boardKey) {
        boardService.deleteBoard(workspaceId, boardKey);
    }
}
