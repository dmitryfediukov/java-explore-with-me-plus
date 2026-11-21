package ru.practicum.ewm.main.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.service.CategoryService;
import ru.practicum.ewm.main.service.CompilationService;
import ru.practicum.ewm.main.service.EventService;

@RestController
@Validated
public class PublicController {
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private final CategoryService categoryService;
    private final CompilationService compilationService;
    private final EventService eventService;

    public PublicController(CategoryService categoryService, CompilationService compilationService, EventService eventService) {
        this.categoryService = categoryService;
        this.compilationService = compilationService;
        this.eventService = eventService;
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories(@RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size) {
        return categoryService.getCategories(from, size);
    }

    @GetMapping("/categories/{catId}")
    public CategoryDto category(@PathVariable long catId) {
        return categoryService.getCategory(catId);
    }

    @GetMapping("/compilations")
    public List<CompilationDto> compilations(@RequestParam(required = false) Boolean pinned,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from, @RequestParam(defaultValue = "10") @Positive int size) {
        return compilationService.getCompilations(pinned, from, size);
    }

    @GetMapping("/compilations/{compId}")
    public CompilationDto compilation(@PathVariable long compId) {
        return compilationService.getCompilation(compId);
    }

    @GetMapping("/events")
    public List<EventShortDto> events(@RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories, @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE_PATTERN) LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE_PATTERN) LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "false") boolean onlyAvailable, @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from, @RequestParam(defaultValue = "10") @Positive int size,
            HttpServletRequest request) {
        return eventService.searchPublicEvents(text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size,
                request.getRemoteAddr(), "ewm-main-service");
    }

    @GetMapping("/events/{id}")
    public EventFullDto event(@PathVariable long id, HttpServletRequest request) {
        return eventService.getPublicEvent(id, request.getRemoteAddr(), "ewm-main-service");
    }
}

