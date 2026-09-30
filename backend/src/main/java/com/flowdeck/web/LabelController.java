package com.flowdeck.web;

import com.flowdeck.service.LabelService;
import com.flowdeck.web.dto.LabelDtos.CreateLabelRequest;
import com.flowdeck.web.dto.LabelDtos.LabelResponse;
import com.flowdeck.web.dto.LabelDtos.UpdateLabelRequest;
import com.flowdeck.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/boards/{boardKey}/labels")
@RequiredArgsConstructor
@Tag(name = "Labels", description = "Create, update, and delete a board's labels")
public class LabelController {

    private static final String MEMBER_OR_ABOVE =
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).MEMBER)";
    private static final String VIEWER_OR_ABOVE =
            "@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(com.flowdeck.domain.WorkspaceRole).VIEWER)";

    private final LabelService labelService;

    @PostMapping
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Create a label on a board")
    public ResponseEntity<LabelResponse> createLabel(
            @PathVariable UUID workspaceId,
            @PathVariable String boardKey,
            @Valid @RequestBody CreateLabelRequest request) {
        LabelResponse created = labelService.createLabel(workspaceId, boardKey, request);
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/workspaces/" + workspaceId + "/boards/" + boardKey + "/labels/"
                                        + created.id()))
                .body(created);
    }

    @GetMapping
    @PreAuthorize(VIEWER_OR_ABOVE)
    @Operation(summary = "List a board's labels")
    public PageResponse<LabelResponse> listLabels(
            @PathVariable UUID workspaceId,
            @PathVariable String boardKey,
            @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return labelService.listLabels(workspaceId, boardKey, pageable);
    }

    @PatchMapping("/{labelId}")
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Rename or recolor a label")
    public LabelResponse updateLabel(
            @PathVariable UUID workspaceId,
            @PathVariable String boardKey,
            @PathVariable UUID labelId,
            @Valid @RequestBody UpdateLabelRequest request) {
        return labelService.updateLabel(workspaceId, boardKey, labelId, request);
    }

    @DeleteMapping("/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MEMBER_OR_ABOVE)
    @Operation(summary = "Delete a label, removing it from every card that carries it")
    public void deleteLabel(
            @PathVariable UUID workspaceId, @PathVariable String boardKey, @PathVariable UUID labelId) {
        labelService.deleteLabel(workspaceId, boardKey, labelId);
    }
}
