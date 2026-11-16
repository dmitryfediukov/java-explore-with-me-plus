package ru.practicum.ewm.main.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.service.MainService;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private final MainService service;

    public AdminController(MainService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public List<UserDto> users(@RequestParam(required = false) List<Long> ids,
            @RequestParam(defaultValue = "0") int from, @RequestParam(defaultValue = "10") int size) {
        return service.getUsers(ids, from, size);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto createUser(@Valid @RequestBody UserRequest request) {
        return service.createUser(request);
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable long userId) {
        service.deleteUser(userId);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return service.createCategory(request);
    }

    @PatchMapping("/categories/{catId}")
    public CategoryDto updateCategory(@PathVariable long catId, @Valid @RequestBody CategoryRequest request) {
        return service.updateCategory(catId, request);
    }

    @DeleteMapping("/categories/{catId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable long catId) {
        service.deleteCategory(catId);
    }

    @PostMapping("/compilations")
    @ResponseStatus(HttpStatus.CREATED)
    public CompilationDto createCompilation(@Valid @RequestBody CompilationRequest request) {
        return service.createCompilation(request);
    }

    @PatchMapping("/compilations/{compId}")
    public CompilationDto updateCompilation(@PathVariable long compId, @Valid @RequestBody CompilationUpdate update) {
        return service.updateCompilation(compId, update);
    }

    @DeleteMapping("/compilations/{compId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompilation(@PathVariable long compId) {
        service.deleteCompilation(compId);
    }

    @GetMapping("/events")
    public List<EventFullDto> searchEvents(@RequestParam(required = false) List<Long> users,
            @RequestParam(required = false) List<String> states, @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE_PATTERN) LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE_PATTERN) LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "0") int from, @RequestParam(defaultValue = "10") int size) {
        return service.searchAdminEvents(users, states, categories, rangeStart, rangeEnd, from, size);
    }

    @PatchMapping("/events/{eventId}")
    public EventFullDto updateEvent(@PathVariable long eventId, @Valid @RequestBody EventUpdate update) {
        return service.updateAdminEvent(eventId, update);
    }
}
