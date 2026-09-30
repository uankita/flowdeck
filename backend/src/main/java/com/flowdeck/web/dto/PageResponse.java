package com.flowdeck.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * A page of results, shaped for the API rather than exposing Spring Data's
 * own {@code Page} JSON (which carries internal-looking fields like
 * {@code pageable} and {@code sort}). Every list endpoint returns this, with
 * {@code content} already mapped to response DTOs — never entities.
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    /** {@code page} must already contain response DTOs, not entities — map before calling this. */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
