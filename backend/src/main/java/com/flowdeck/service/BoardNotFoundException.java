package com.flowdeck.service;

public class BoardNotFoundException extends RuntimeException {

    public BoardNotFoundException(String boardKey) {
        super("No board with key '" + boardKey + "'");
    }
}
