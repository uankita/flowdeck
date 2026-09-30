package com.flowdeck.service;

import java.util.UUID;

public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(UUID cardId) {
        super("No card with id '" + cardId + "'");
    }
}
