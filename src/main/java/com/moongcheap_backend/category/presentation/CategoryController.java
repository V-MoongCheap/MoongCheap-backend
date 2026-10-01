package com.moongcheap_backend.category.presentation;

import com.moongcheap_backend.category.application.CategoryService;
import com.moongcheap_backend.category.presentation.dto.CategoryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Category · 카테고리", description = "카테고리 조회")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "카테고리 목록 조회", description = "depth와 parent_id 기준으로 카테고리 목록을 조회합니다. depth 2 이상이면 parent_id 필수.")
    @GetMapping
    public List<CategoryDto> getCategories(
        @RequestParam Short depth,
        @RequestParam(required = false) Long parentId) {
        return categoryService.getCategories(depth, parentId);
    }
}
