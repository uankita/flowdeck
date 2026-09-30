package com.flowdeck.service;

public class DuplicateLabelNameException extends RuntimeException {

    public DuplicateLabelNameException(String name) {
        super("A label named '" + name + "' already exists on this board");
    }
}
