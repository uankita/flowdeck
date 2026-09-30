package com.flowdeck.service;

public class DuplicateWorkspaceSlugException extends RuntimeException {

    public DuplicateWorkspaceSlugException(String slug) {
        super("Workspace slug '" + slug + "' is already taken");
    }
}
