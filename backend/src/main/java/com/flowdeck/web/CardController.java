package com.flowdeck.web;

import com.flowdeck.service.CardService;
import com.flowdeck.web.dto.BoardDtos.CardResponse;
import com.flowdeck.web.dto.CardDtos.CreateCardRequest;
import com.flowdeck.web.dto.CardDtos.MoveCardRequest;
import com.flowdeck.web.dto.CardDtos.UpdateCardRequest;
import com.flowdeck.web.dto.LabelDtos.LabelResponse;
import com.flowdeck.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Card creation and listing nest under a list
 * ({@code .../lists/{listId}/cards}); everything else addresses a card
 * directly by id ({@code .../cards/{cardId}}), since a card's id alone is
 * already enough to look it up — matching the shape {@code moveCard} already
 * established.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Create, read, update, move, and label cards")
public class CardController {

    private static final String MEMBER_OR_ABOVE =
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).MEMBER)";
    private static final String VIEWER_OR_ABOVE =
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).VIEWER)";

    private final CardService cardService;

    @PostMapping("/boards/{boardKey}/lists/{listId}/cards")
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Create a card at the end of a list")
    public ResponseEntity<CardResponse> createCard(
            @PathVariable UUID workspaceId,
            @PathVariable String boardKey,
            @PathVariable UUID listId,
            @Valid @RequestBody CreateCardRequest request) {
        CardResponse created = cardService.createCard(workspaceId, listId, request);
        return ResponseEntity.created(URI.create("/api/v1/workspaces/" + workspaceId + "/cards/" + created.id()))
                .body(created);
    }

    @GetMapping("/boards/{boardKey}/lists/{listId}/cards")
    @PreAuthorize(VIEWER_OR_ABOVE)
    @Operation(summary = "List a list's live cards in display order")
    public PageResponse<CardResponse> listCards(
            @PathVariable UUID workspaceId,
            @PathVariable String boardKey,
            @PathVariable UUID listId,
            @PageableDefault(size = 50, sort = "rank") Pageable pageable) {
        return cardService.listCardsForList(workspaceId, listId, pageable);
    }

    @GetMapping("/cards/{cardId}")
    @PreAuthorize(VIEWER_OR_ABOVE)
    @Operation(summary = "Fetch one card")
    public CardResponse getCard(@PathVariable UUID workspaceId, @PathVariable UUID cardId) {
        return cardService.getCard(workspaceId, cardId);
    }

    @PatchMapping("/cards/{cardId}")
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(
            summary = "Update a card",
            description = "Optimistic locking: `version` must match the card's current version, "
                    + "or this returns 409 with the card's current state in the body.")
    public CardResponse updateCard(
            @PathVariable UUID workspaceId,
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateCardRequest request) {
        return cardService.updateCard(workspaceId, cardId, request);
    }

    @DeleteMapping("/cards/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Soft-delete a card")
    public void deleteCard(@PathVariable UUID workspaceId, @PathVariable UUID cardId) {
        cardService.deleteCard(workspaceId, cardId);
    }

    @PatchMapping("/cards/{cardId}/move")
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Move a card into a list, landing it between two given neighbours")
    public CardResponse moveCard(
            @PathVariable UUID workspaceId,
            @PathVariable UUID cardId,
            @Valid @RequestBody MoveCardRequest request) {
        return cardService.moveCard(workspaceId, cardId, request);
    }

    @GetMapping("/cards/{cardId}/labels")
    @PreAuthorize(VIEWER_OR_ABOVE)
    @Operation(summary = "List the labels attached to a card")
    public List<LabelResponse> listLabels(@PathVariable UUID workspaceId, @PathVariable UUID cardId) {
        return cardService.listLabelsForCard(workspaceId, cardId);
    }

    @PutMapping("/cards/{cardId}/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Attach a label to a card (idempotent)")
    public void attachLabel(
            @PathVariable UUID workspaceId, @PathVariable UUID cardId, @PathVariable UUID labelId) {
        cardService.attachLabel(workspaceId, cardId, labelId);
    }

    @DeleteMapping("/cards/{cardId}/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Detach a label from a card (idempotent)")
    public void detachLabel(
            @PathVariable UUID workspaceId, @PathVariable UUID cardId, @PathVariable UUID labelId) {
        cardService.detachLabel(workspaceId, cardId, labelId);
    }
}
