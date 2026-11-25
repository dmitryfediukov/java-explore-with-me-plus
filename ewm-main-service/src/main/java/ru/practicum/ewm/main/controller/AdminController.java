package ru.practicum.ewm.main.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.service.CategoryService;
import ru.practicum.ewm.main.service.CommentService;
import ru.practicum.ewm.main.service.CompilationService;
import ru.practicum.ewm.main.service.EventService;
import ru.practicum.ewm.main.service.UserService;

@RestController
@Validated
@RequestMapping("/admin")
public class AdminController {
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private final CategoryService categoryService;
    private final CompilationService compilationService;
    private final EventService eventService;
    private final UserService userService;
    private final CommentService commentService;

    public AdminController(CategoryService categoryService, CompilationService compilationService, EventService eventService, UserService userService, CommentService commentService) {
        this.categoryService = categoryService;
        this.compilationService = compilationService;
        this.eventService = eventService;
        this.userService = userService;
        this.commentService = commentService;
    }

    @GetMapping("/users")
    public List<UserDto> users(@RequestParam(required = false) List<Long> ids,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from, @RequestParam(defaultValue = "10") @Positive int size) {
        return userService.getUsers(ids, from, size);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto createUser(@Valid @RequestBody UserRequest request) {
        return userService.createUser(request);
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable long userId) {
        userService.deleteUser(userId);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return categoryService.createCategory(request);
    }

    @PatchMapping("/categories/{catId}")
    public CategoryDto updateCategory(@PathVariable long catId, @Valid @RequestBody CategoryRequest request) {
        return categoryService.updateCategory(catId, request);
    }

    @DeleteMapping("/categories/{catId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable long catId) {
        categoryService.deleteCategory(catId);
    }

    @PostMapping("/compilations")
    @ResponseStatus(HttpStatus.CREATED)
    public CompilationDto createCompilation(@Valid @RequestBody CompilationRequest request) {
        return compilationService.createCompilation(request);
    }

    @PatchMapping("/compilations/{compId}")
    public CompilationDto updateCompilation(@PathVariable long compId, @Valid @RequestBody CompilationUpdate update) {
        return compilationService.updateCompilation(compId, update);
    }

    @DeleteMapping("/compilations/{compId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompilation(@PathVariable long compId) {
        compilationService.deleteCompilation(compId);
    }

    @GetMapping("/events")
    public List<EventFullDto> searchEvents(@RequestParam(required = false) List<Long> users,
            @RequestParam(required = false) List<String> states, @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE_PATTERN) LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE_PATTERN) LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from, @RequestParam(defaultValue = "10") @Positive int size) {
        return eventService.searchAdminEvents(users, states, categories, rangeStart, rangeEnd, from, size);
    }

    @PatchMapping("/events/{eventId}")
    public EventFullDto updateEvent(@PathVariable long eventId, @Valid @RequestBody EventUpdate update) {
        return eventService.updateAdminEvent(eventId, update);
    }

    @GetMapping("/comments")
    public List<CommentDto> comments(@RequestParam(required = false) List<Long> users,
            @RequestParam(required = false) List<Long> events, @RequestParam(required = false) List<String> states,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE_PATTERN) LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE_PATTERN) LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size) {
        return commentService.searchComments(users, events, states, rangeStart, rangeEnd, from, size);
    }

    @PatchMapping("/comments/{commentId}")
    public CommentDto moderateComment(@PathVariable long commentId, @Valid @RequestBody CommentModeration moderation) {
        return commentService.moderateComment(commentId, moderation);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable long commentId) {
        commentService.deleteCommentByAdmin(commentId);
    }
}

