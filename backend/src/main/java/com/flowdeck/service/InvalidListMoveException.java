package com.flowdeck.service;

/** A list-move request's own logic doesn't hold together — see {@code BoardListService#moveList}. */
public class InvalidListMoveException extends RuntimeException {

    public InvalidListMoveException(String message) {
        super(message);
    }
}
