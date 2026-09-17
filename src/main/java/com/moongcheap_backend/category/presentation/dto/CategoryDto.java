package com.moongcheap_backend.category.presentation.dto;

import com.moongcheap_backend.category.domain.Category;

public record CategoryDto(
    Long id,
    Long parentId,
    String name,
    Short depth
) {
    public static CategoryDto from(Category category) {
        return new CategoryDto(
            category.getId(),
            category.getParentId(),
            category.getName(),
            category.getDepth()
        );
    }
}
