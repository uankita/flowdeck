package com.flowdeck.service;

public class DuplicateBoardKeyException extends RuntimeException {

    public DuplicateBoardKeyException(String boardKey) {
        super("Board key '" + boardKey + "' is already taken");
    }
}
