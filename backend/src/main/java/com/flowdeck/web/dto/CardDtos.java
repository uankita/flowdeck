package com.flowdeck.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request payloads for {@code /api/v1/workspaces/{workspaceId}/cards/**}. The response shape is {@link BoardDtos.CardResponse} — no need for a near-duplicate. */
public final class CardDtos {

    private CardDtos() {}

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
