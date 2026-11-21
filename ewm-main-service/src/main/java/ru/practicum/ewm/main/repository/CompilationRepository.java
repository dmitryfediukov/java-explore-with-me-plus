package ru.practicum.ewm.main.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.ewm.main.model.CompilationEntity;

public interface CompilationRepository extends JpaRepository<CompilationEntity, Long> {
    List<CompilationEntity> findByPinned(boolean pinned);
}
