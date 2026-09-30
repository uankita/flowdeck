package com.flowdeck.web;

import com.flowdeck.service.BoardService;
import com.flowdeck.web.dto.BoardDtos.BoardDetailResponse;
import com.flowdeck.web.dto.BoardDtos.BoardSummaryResponse;
import com.flowdeck.web.dto.BoardDtos.CreateBoardRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/boards")
@RequiredArgsConstructor
@Tag(name = "Boards", description = "Create and read Flowdeck boards within a workspace")
public class BoardController {

    private final BoardService boardService;

    @GetMapping
    @Operation(summary = "List all non-archived boards in a workspace")
    public List<BoardSummaryResponse> listBoards(@PathVariable UUID workspaceId) {
        return boardService.listActiveBoards(workspaceId);
    }

    @GetMapping("/{boardKey}")
    @Operation(summary = "Fetch one board with its lists and cards")
    public BoardDetailResponse getBoard(
            @PathVariable UUID workspaceId, @PathVariable String boardKey) {
        return boardService.getBoard(workspaceId, boardKey);
    }

    @PostMapping
    @Operation(summary = "Create a board, seeded with the default lists")
    public ResponseEntity<BoardDetailResponse> createBoard(
            @PathVariable UUID workspaceId, @Valid @RequestBody CreateBoardRequest request) {
        BoardDetailResponse created = boardService.createBoard(workspaceId, request);
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/workspaces/" + workspaceId + "/boards/" + created.boardKey()))
                .body(created);
    }
}
