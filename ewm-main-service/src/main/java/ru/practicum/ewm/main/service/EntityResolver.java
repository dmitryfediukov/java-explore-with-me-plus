package ru.practicum.ewm.main.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.exception.ApiException;
import ru.practicum.ewm.main.model.*;
import ru.practicum.ewm.main.repository.*;

@Component
@Transactional(readOnly = true)
public class EntityResolver {
    private final UserRepository users;
    private final CategoryRepository categories;
    private final EventRepository events;
    private final ParticipationRequestRepository requests;
    private final CompilationRepository compilations;
    private final CommentRepository comments;

    public EntityResolver(UserRepository users, CategoryRepository categories, EventRepository events, ParticipationRequestRepository requests, CompilationRepository compilations, CommentRepository comments) {
        this.users = users;
        this.categories = categories;
        this.events = events;
        this.requests = requests;
        this.compilations = compilations;
        this.comments = comments;
    }

    public EventEntity getOwnedEvent(long userId, long eventId) {
        EventEntity event = event(eventId);
        if (!event.getInitiator().getId().equals(userId))
            throw ApiException.notFound("Event was not found for user");
        return event;
    }

    public UserEntity user(long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User with id=" + id + " was not found"));
    }

    public CategoryEntity category(long id) {
        return categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category with id=" + id + " was not found"));
    }

    public EventEntity event(long id) {
        return events.findById(id).orElseThrow(() -> ApiException.notFound("Event with id=" + id + " was not found"));
    }

    public ParticipationRequestEntity request(long id) {
        return requests.findById(id)
                .orElseThrow(() -> ApiException.notFound("Request with id=" + id + " was not found"));
    }

    public CompilationEntity compilation(long id) {
        return compilations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Compilation with id=" + id + " was not found"));
    }

    public CommentEntity comment(long id) {
        return comments.findById(id)
                .orElseThrow(() -> ApiException.notFound("Comment with id=" + id + " was not found"));
    }

    public List<EventEntity> resolveEvents(List<Long> ids) {
        if (ids == null || ids.isEmpty())
            return new ArrayList<>();
        List<EventEntity> result = events.findAllById(ids);
        if (result.size() != ids.stream().distinct().count())
            throw ApiException.notFound("One or more events were not found");
        return result;
    }
}
