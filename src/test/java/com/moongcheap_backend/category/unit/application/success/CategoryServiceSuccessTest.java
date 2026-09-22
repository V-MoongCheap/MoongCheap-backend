package com.moongcheap_backend.category.unit.application.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.category.application.CategoryService;
import com.moongcheap_backend.category.domain.Category;
import com.moongcheap_backend.category.infrastructure.CategoryRepository;
import com.moongcheap_backend.category.presentation.dto.CategoryDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceSuccessTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category stubCategory(Long id, Long parentId, String name, short depth) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(id);
        when(category.getParentId()).thenReturn(parentId);
        when(category.getName()).thenReturn(name);
        when(category.getDepth()).thenReturn(depth);
        return category;
    }

    @Nested
    @DisplayName("getCategories - 성공")
    class GetCategoriesTest {

        @Test
        void depth_1_parentId_null로_카테고리를_조회한다() {
            short depth = 1;
            Category category = stubCategory(1L, null, "전자기기", depth);
            when(categoryRepository.findByDepth(depth)).thenReturn(List.of(category));

            List<CategoryDto> result = categoryService.getCategories(depth, null);

            verify(categoryRepository).findByDepth(depth);
            verify(categoryRepository, never()).findByDepthAndParentId(depth, null);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("전자기기");
        }

        @Test
        void depth_2_parentId_1L로_카테고리를_조회한다() {
            short depth = 2;
            long parentId = 1L;
            Category category = stubCategory(2L, parentId, "스마트폰", depth);
            when(categoryRepository.findByDepthAndParentId(depth, parentId)).thenReturn(List.of(category));

            List<CategoryDto> result = categoryService.getCategories(depth, parentId);

            verify(categoryRepository).findByDepthAndParentId(depth, parentId);
            verify(categoryRepository, never()).findByDepth(depth);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("스마트폰");
        }

        @Test
        void depth_1이고_parentId도_함께_전달된다() {
            short depth = 1;
            long parentId = 1L;
            Category category = stubCategory(3L, parentId, "가전", depth);
            when(categoryRepository.findByDepthAndParentId(depth, parentId)).thenReturn(List.of(category));

            List<CategoryDto> result = categoryService.getCategories(depth, parentId);

            verify(categoryRepository).findByDepthAndParentId(depth, parentId);
            verify(categoryRepository, never()).findByDepth(depth);
            assertThat(result).hasSize(1);
        }

        @Test
        void 해당_depth에_카테고리가_없는_상태에서_조회한다() {
            short depth = 1;
            when(categoryRepository.findByDepth(depth)).thenReturn(List.of());

            List<CategoryDto> result = categoryService.getCategories(depth, null);

            assertThat(result).isEmpty();
        }
    }
}
