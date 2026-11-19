package ru.practicum.ewm.main.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.ewm.main.model.CategoryEntity;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
}
