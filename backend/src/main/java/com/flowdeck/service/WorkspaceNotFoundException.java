package com.flowdeck.service;

import java.util.UUID;

public class WorkspaceNotFoundException extends RuntimeException {

    public WorkspaceNotFoundException(UUID workspaceId) {
        super("No workspace with id '" + workspaceId + "'");
    }
}
