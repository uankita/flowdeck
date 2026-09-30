package com.flowdeck.service;

public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String message, Throwable cause) {
        super(message, cause);
    }
}
