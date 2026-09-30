package com.flowdeck.service;

/** A move request's own logic doesn't hold together — see {@code CardService#moveCard}. */
public class InvalidCardMoveException extends RuntimeException {

    public InvalidCardMoveException(String message) {
        super(message);
    }
}
