package ru.practicum.category;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CategoryMapper {
    public CategoryDto toDto(Category category) {
        return new CategoryDto(category.getId(), category.getName());
    }

    public Category fromDto(CategoryDto categoryDto) {
        return Category.builder()
                .id(categoryDto.getId())
                .name(categoryDto.getName())
                .build();
    }

    public Category fromDto(NewCategoryDto newCategoryDto) {
        return Category.builder()
                .name(newCategoryDto.getName())
                .build();
    }
}