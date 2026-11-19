package ru.practicum.ewm.main.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.service.MainService;

@RestController
@RequestMapping("/users/{userId}")
public class PrivateController {
    private final MainService service;

    public PrivateController(MainService service) {
        this.service = service;
    }

    @GetMapping("/events")
    public List<EventShortDto> userEvents(@PathVariable long userId, @RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "10") int size) {
        return service.getUserEvents(userId, from, size);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventFullDto createEvent(@PathVariable long userId, @Valid @RequestBody EventRequest request) {
        return service.createEvent(userId, request);
    }

    @GetMapping("/events/{eventId}")
    public EventFullDto getEvent(@PathVariable long userId, @PathVariable long eventId) {
        return service.getUserEvent(userId, eventId);
    }

    @PatchMapping("/events/{eventId}")
    public EventFullDto updateEvent(@PathVariable long userId, @PathVariable long eventId,
            @Valid @RequestBody EventUpdate update) {
        return service.updateUserEvent(userId, eventId, update);
    }

    @GetMapping("/events/{eventId}/requests")
    public List<RequestDto> eventRequests(@PathVariable long userId, @PathVariable long eventId) {
        return service.getRequestsByEvent(userId, eventId);
    }

    @PatchMapping("/events/{eventId}/requests")
    public RequestStatusResult updateRequests(@PathVariable long userId, @PathVariable long eventId,
            @Valid @RequestBody RequestStatusUpdate update) {
        return service.updateRequests(userId, eventId, update);
    }

    @GetMapping("/requests")
    public List<RequestDto> requests(@PathVariable long userId) {
        return service.getRequestsByRequester(userId);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto createRequest(@PathVariable long userId, @RequestParam long eventId) {
        return service.createRequest(userId, eventId);
    }

    @PatchMapping("/requests/{requestId}/cancel")
    public RequestDto cancelRequest(@PathVariable long userId, @PathVariable long requestId) {
        return service.cancelRequest(userId, requestId);
    }
}
