package ru.practicum.ewm.main.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.service.MainService;

@RestController
public class PublicController {
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private final MainService service;

    public PublicController(MainService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories(@RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "10") int size) {
        return service.getCategories(from, size);
    }

    @GetMapping("/categories/{catId}")
    public CategoryDto category(@PathVariable long catId) {
        return service.getCategory(catId);
    }

    @GetMapping("/compilations")
    public List<CompilationDto> compilations(@RequestParam(required = false) Boolean pinned,
            @RequestParam(defaultValue = "0") int from, @RequestParam(defaultValue = "10") int size) {
        return service.getCompilations(pinned, from, size);
    }

    @GetMapping("/compilations/{compId}")
    public CompilationDto compilation(@PathVariable long compId) {
        return service.getCompilation(compId);
    }

    @GetMapping("/events")
    public List<EventShortDto> events(@RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories, @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE_PATTERN) LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE_PATTERN) LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "false") boolean onlyAvailable, @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int from, @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        return service.searchPublicEvents(text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size,
                request.getRemoteAddr(), "ewm-main-service");
    }

    @GetMapping("/events/{id}")
    public EventFullDto event(@PathVariable long id, HttpServletRequest request) {
        return service.getPublicEvent(id, request.getRemoteAddr(), "ewm-main-service");
    }
}
