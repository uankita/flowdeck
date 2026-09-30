package com.flowdeck.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request payloads for {@code /api/v1/workspaces/{workspaceId}/boards/{boardKey}/lists/**}.
 * The response shape is {@link BoardDtos.BoardListResponse} — no need for a near-duplicate.
 */
public final class BoardListDtos {

    private BoardListDtos() {}

    public record CreateBoardListRequest(
            @NotBlank @Size(max = 120) String name, @Positive Integer wipLimit) {}

    /**
     * {@code version} must be the value most recently read for this list —
     * see {@code BoardListService#updateList} for what happens on a
     * mismatch. {@code Long} rather than {@code long} so a missing field
     * fails Bean Validation (400) instead of silently comparing against 0.
     */
    public record UpdateBoardListRequest(
            @NotBlank @Size(max = 120) String name,
            @Positive Integer wipLimit,
            @NotNull Long version) {}

    /** Same {@code (prev, next)} shape as {@code CardDtos.MoveCardRequest} — see that Javadoc. */
    public record MoveBoardListRequest(UUID previousListId, UUID nextListId) {}
}
