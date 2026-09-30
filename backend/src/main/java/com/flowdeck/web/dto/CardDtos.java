package com.flowdeck.web.dto;

import com.flowdeck.domain.CardPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request payloads for {@code /api/v1/workspaces/{workspaceId}/cards/**}. The response shape is {@link BoardDtos.CardResponse} — no need for a near-duplicate. */
public final class CardDtos {

    private CardDtos() {}

    /** {@code priority} left {@code null} defaults to {@code MEDIUM}, matching {@code Card}'s own field default. */
    public record CreateCardRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 8000) String description,
            CardPriority priority,
            Instant dueAt) {}

    /**
     * {@code version} must be the value most recently read for this card —
     * see {@code CardService#updateCard} for what happens on a mismatch.
     * {@code Long} rather than {@code long} so a missing field fails Bean
     * Validation (400) instead of silently comparing against 0. Every other
     * field is a full replace, not a partial patch — unlike {@code version},
     * {@code priority} here is required, not defaulted.
     */
    public record UpdateCardRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 8000) String description,
            @NotNull CardPriority priority,
            Instant dueAt,
            @NotNull Long version) {}

    /**
     * Where a card should land: {@code targetListId}, plus the two cards it
     * should end up between. Either neighbour may be omitted — both absent
     * means "the only card in the list"; {@code previousCardId} alone absent
     * means "move to the very top"; {@code nextCardId} alone absent means
     * "move to the very bottom". Mirrors {@code RankGenerator.between}'s own
     * {@code (prev, next)} parameter shape deliberately.
     */
    public record MoveCardRequest(
            @NotNull UUID targetListId, UUID previousCardId, UUID nextCardId) {}
}
