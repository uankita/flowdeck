package com.flowdeck.service;

import java.util.UUID;

public class BoardListNotFoundException extends RuntimeException {

    public BoardListNotFoundException(UUID boardListId) {
        super("No list with id '" + boardListId + "'");
    }
}
