package ru.practicum.ewm.main.service;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.model.*;
import ru.practicum.ewm.main.repository.*;
import ru.practicum.ewm.main.mapper.DtoMapper;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categories;
    private final EntityResolver entities;
    private final DtoMapper mapper;

    public CategoryService(CategoryRepository categories, EntityResolver entities, DtoMapper mapper) {
        this.categories = categories;
        this.entities = entities;
        this.mapper = mapper;
    }

    public List<CategoryDto> getCategories(int from, int size) {
        return categories.findAll(new OffsetPageRequest(from, size, Sort.by("id"))).getContent().stream()
                .map(mapper::categoryDto).toList();
    }

    public CategoryDto getCategory(long id) {
        return mapper.categoryDto(entities.category(id));
    }

    @Transactional
    public CategoryDto createCategory(CategoryRequest request) {
        return mapper.categoryDto(categories.save(new CategoryEntity(request.name())));
    }

    @Transactional
    public CategoryDto updateCategory(long id, CategoryRequest request) {
        CategoryEntity category = entities.category(id);
        category.setName(request.name());
        return mapper.categoryDto(category);
    }

    @Transactional
    public void deleteCategory(long id) {
        categories.delete(entities.category(id));
    }
}
