package com.flowdeck.web;

import com.flowdeck.service.CardService;
import com.flowdeck.web.dto.BoardDtos.CardResponse;
import com.flowdeck.web.dto.CardDtos.MoveCardRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Move cards between and within lists")
public class CardController {

    private final CardService cardService;

    @PatchMapping("/{cardId}/move")
    @PreAuthorize(
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).MEMBER)")
    @Operation(summary = "Move a card into a list, landing it between two given neighbours")
    public CardResponse moveCard(
            @PathVariable UUID workspaceId,
            @PathVariable UUID cardId,
            @Valid @RequestBody MoveCardRequest request) {
        return cardService.moveCard(workspaceId, cardId, request);
    }
}
