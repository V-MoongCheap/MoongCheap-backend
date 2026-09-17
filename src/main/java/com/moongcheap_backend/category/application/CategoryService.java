package com.moongcheap_backend.category.application;

import com.moongcheap_backend.category.infrastructure.CategoryRepository;
import com.moongcheap_backend.category.presentation.dto.CategoryDto;
import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryDto> getCategories(Short depth, Long parentId) {
        if (depth >= 2 && parentId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "depth가 2 이상이면 parent_id가 필요합니다.");
        }
        if (parentId != null) {
            return categoryRepository.findByDepthAndParentId(depth, parentId).stream()
                    .map(CategoryDto::from)
                    .toList();
        }
        return categoryRepository.findByDepth(depth).stream()
                .map(CategoryDto::from)
                .toList();
    }
}
