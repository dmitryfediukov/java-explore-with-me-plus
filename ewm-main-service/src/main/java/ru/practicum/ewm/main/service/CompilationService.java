package ru.practicum.ewm.main.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.model.*;
import ru.practicum.ewm.main.repository.*;
import ru.practicum.ewm.main.mapper.DtoMapper;

@Service
@Transactional(readOnly = true)
public class CompilationService {
    private final CompilationRepository compilations;
    private final EntityResolver entities;
    private final DtoMapper mapper;

    public CompilationService(CompilationRepository compilations, EntityResolver entities, DtoMapper mapper) {
        this.compilations = compilations;
        this.entities = entities;
        this.mapper = mapper;
    }

    @Transactional
    public CompilationDto createCompilation(CompilationRequest request) {
        CompilationEntity entity = new CompilationEntity();
        entity.setTitle(request.title());
        entity.setPinned(Boolean.TRUE.equals(request.pinned()));
        entity.setEvents(entities.resolveEvents(request.events()));
        return mapper.compilationDto(compilations.save(entity));
    }

    @Transactional
    public CompilationDto updateCompilation(long id, CompilationUpdate update) {
        CompilationEntity entity = entities.compilation(id);
        if (update.title() != null)
            entity.setTitle(update.title());
        if (update.pinned() != null)
            entity.setPinned(update.pinned());
        if (update.events() != null)
            entity.setEvents(entities.resolveEvents(update.events()));
        return mapper.compilationDto(entity);
    }

    @Transactional
    public void deleteCompilation(long id) {
        compilations.delete(entities.compilation(id));
    }

    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        List<CompilationEntity> all = pinned == null ? compilations.findAll() : compilations.findByPinned(pinned);
        return Pagination.slice(all, from, size).stream().map(mapper::compilationDto).toList();
    }

    public CompilationDto getCompilation(long id) {
        return mapper.compilationDto(entities.compilation(id));
    }
}
