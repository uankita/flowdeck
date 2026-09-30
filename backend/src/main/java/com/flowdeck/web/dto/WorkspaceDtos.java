package com.flowdeck.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request/response payloads for the workspace API. */
public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record CreateWorkspaceRequest(
            @NotBlank
            @Size(max = 60)
            @Pattern(
                    regexp = "^[a-z0-9][a-z0-9-]*$",
                    message = "must be lowercase letters, digits, and hyphens, starting alphanumeric")
            String slug,
            @NotBlank @Size(max = 200) String name) {}

    public record UpdateWorkspaceRequest(@NotBlank @Size(max = 200) String name) {}

    public record WorkspaceResponse(
            UUID id, String slug, String name, Instant createdAt, Instant updatedAt) {}
}
