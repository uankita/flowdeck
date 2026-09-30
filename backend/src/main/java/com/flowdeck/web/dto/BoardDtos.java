package com.flowdeck.web.dto;

import com.flowdeck.domain.CardPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response payloads for the board API, grouped to keep the tree flat. */
public final class BoardDtos {

    private BoardDtos() {}

    public record CreateBoardRequest(
            @NotBlank
            @Size(max = 16)
            @Pattern(
                    regexp = "^[A-Z][A-Z0-9]*$",
                    message = "must be uppercase letters and digits, starting with a letter")
            String boardKey,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description) {}

    public record BoardSummaryResponse(
            UUID id,
            String boardKey,
            String name,
            String description,
            boolean archived,
            Instant createdAt,
            Instant updatedAt) {}

    public record BoardDetailResponse(
            UUID id,
            String boardKey,
            String name,
            String description,
            boolean archived,
            List<BoardListResponse> lists,
            Instant createdAt,
            Instant updatedAt) {}

    public record BoardListResponse(
            UUID id, String name, String rank, Integer wipLimit, List<CardResponse> cards) {}

    public record CardResponse(
            UUID id,
            String title,
            String description,
            String rank,
            CardPriority priority,
            Instant dueAt) {}
}
