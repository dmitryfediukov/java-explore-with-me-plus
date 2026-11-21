package ru.practicum.ewm.main.controller;

import jakarta.validation.Valid;
import java.util.List;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.service.EventService;
import ru.practicum.ewm.main.service.RequestService;

@RestController
@Validated
@RequestMapping("/users/{userId}")
public class PrivateController {
    private final EventService eventService;
    private final RequestService requestService;

    public PrivateController(EventService eventService, RequestService requestService) {
        this.eventService = eventService;
        this.requestService = requestService;
    }

    @GetMapping("/events")
    public List<EventShortDto> userEvents(@PathVariable long userId, @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size) {
        return eventService.getUserEvents(userId, from, size);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventFullDto createEvent(@PathVariable long userId, @Valid @RequestBody EventRequest request) {
        return eventService.createEvent(userId, request);
    }

    @GetMapping("/events/{eventId}")
    public EventFullDto getEvent(@PathVariable long userId, @PathVariable long eventId) {
        return eventService.getUserEvent(userId, eventId);
    }

    @PatchMapping("/events/{eventId}")
    public EventFullDto updateEvent(@PathVariable long userId, @PathVariable long eventId,
            @Valid @RequestBody EventUpdate update) {
        return eventService.updateUserEvent(userId, eventId, update);
    }

    @GetMapping("/events/{eventId}/requests")
    public List<RequestDto> eventRequests(@PathVariable long userId, @PathVariable long eventId) {
        return requestService.getRequestsByEvent(userId, eventId);
    }

    @PatchMapping("/events/{eventId}/requests")
    public RequestStatusResult updateRequests(@PathVariable long userId, @PathVariable long eventId,
            @Valid @RequestBody RequestStatusUpdate update) {
        return requestService.updateRequests(userId, eventId, update);
    }

    @GetMapping("/requests")
    public List<RequestDto> requests(@PathVariable long userId) {
        return requestService.getRequestsByRequester(userId);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto createRequest(@PathVariable long userId, @RequestParam long eventId) {
        return requestService.createRequest(userId, eventId);
    }

    @PatchMapping("/requests/{requestId}/cancel")
    public RequestDto cancelRequest(@PathVariable long userId, @PathVariable long requestId) {
        return requestService.cancelRequest(userId, requestId);
    }
}

