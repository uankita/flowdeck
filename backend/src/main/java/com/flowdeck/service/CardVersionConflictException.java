package com.flowdeck.service;

import com.flowdeck.web.dto.BoardDtos.CardResponse;
import lombok.Getter;

/**
 * The caller's {@code version} didn't match the card's current one — either
 * caught by an explicit pre-check ({@code CardService#updateCard} comparing
 * the request's {@code version} against the freshly-loaded card before
 * touching anything) or by Hibernate's own {@code @Version}-driven
 * {@code ObjectOptimisticLockingFailureException} at flush time, which
 * {@code CardService} catches and re-wraps as this — one exception type, one
 * response shape, regardless of which layer caught the conflict.
 *
 * <p>Carries the card's current state so {@code GlobalExceptionHandler} can
 * put it in the 409 body: the client shouldn't have to re-fetch just to see
 * what changed.
 */
@Getter
public class CardVersionConflictException extends RuntimeException {

    private final CardResponse currentState;

    public CardVersionConflictException(CardResponse currentState) {
        super(
                "Card '%s' was changed by someone else since you last read it (current version %d)"
                        .formatted(currentState.id(), currentState.version()));
        this.currentState = currentState;
    }
}
