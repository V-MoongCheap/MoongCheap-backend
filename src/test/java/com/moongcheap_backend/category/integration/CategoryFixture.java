package com.moongcheap_backend.category.integration;

import com.moongcheap_backend.category.domain.Category;
import com.moongcheap_backend.category.infrastructure.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CategoryFixture {

    @Autowired
    private CategoryRepository categoryRepository;

    public Category save(Long parentId, String name, Short depth) {
        Category category = Category.builder()
            .parentId(parentId)
            .name(name)
            .depth(depth)
            .facet(null)
            .build();
        return categoryRepository.save(category);
    }

    public void clear() {
        categoryRepository.deleteAllInBatch();
    }
}
