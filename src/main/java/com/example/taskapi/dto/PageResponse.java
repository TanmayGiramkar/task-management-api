package com.example.taskapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "One page of results")
public record PageResponse<T>(
        @Schema(description = "Items on this page")
        List<T> content,
        @Schema(description = "Zero-based page index", example = "0")
        int page,
        @Schema(description = "Requested page size", example = "10")
        int size,
        @Schema(description = "Number of items across all pages", example = "6")
        long totalElements,
        @Schema(description = "Number of pages", example = "1")
        int totalPages,
        @Schema(description = "Whether this is the first page", example = "true")
        boolean first,
        @Schema(description = "Whether this is the last page", example = "true")
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}