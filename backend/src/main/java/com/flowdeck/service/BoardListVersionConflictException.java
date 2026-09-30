package com.flowdeck.service;

import com.flowdeck.web.dto.BoardDtos.BoardListResponse;
import lombok.Getter;

/** Same reasoning as {@link CardVersionConflictException}, for {@code BoardList} updates. */
@Getter
public class BoardListVersionConflictException extends RuntimeException {

    private final BoardListResponse currentState;

    public BoardListVersionConflictException(BoardListResponse currentState) {
        super(
                "List '%s' was changed by someone else since you last read it (current version %d)"
                        .formatted(currentState.id(), currentState.version()));
        this.currentState = currentState;
    }
}
