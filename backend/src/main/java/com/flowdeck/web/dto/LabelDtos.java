package com.flowdeck.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request/response payloads for the board-scoped label API. */
public final class LabelDtos {

    private LabelDtos() {}

    private static final String HEX_COLOR_PATTERN = "^#[0-9a-fA-F]{6}$";
    private static final String HEX_COLOR_MESSAGE = "must be a hex color like #4f46e5";

    public record CreateLabelRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Pattern(regexp = HEX_COLOR_PATTERN, message = HEX_COLOR_MESSAGE) String color) {}

    public record UpdateLabelRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Pattern(regexp = HEX_COLOR_PATTERN, message = HEX_COLOR_MESSAGE) String color) {}

    public record LabelResponse(UUID id, String name, String color) {}
}
