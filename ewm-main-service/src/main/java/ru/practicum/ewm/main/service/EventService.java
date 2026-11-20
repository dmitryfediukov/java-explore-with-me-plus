package ru.practicum.ewm.main.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.exception.ApiException;
import ru.practicum.ewm.main.model.*;
import ru.practicum.ewm.main.repository.*;
import ru.practicum.ewm.main.mapper.DtoMapper;

@Service
@Transactional(readOnly = true)
public class EventService {
    private final EventRepository events;
    private final ParticipationRequestRepository requests;
    private final EntityResolver entities;
    private final DtoMapper mapper;
    private final EventStatisticsService statistics;

    public EventService(EventRepository events, ParticipationRequestRepository requests, EntityResolver entities, DtoMapper mapper, EventStatisticsService statistics) {
        this.events = events;
        this.requests = requests;
        this.entities = entities;
        this.mapper = mapper;
        this.statistics = statistics;
    }

    @Transactional
    public EventFullDto createEvent(long userId, EventRequest request) {
        validateEventDate(request.eventDate());
        EventEntity event = new EventEntity();
        event.setTitle(request.title());
        event.setAnnotation(request.annotation());
        event.setDescription(request.description());
        event.setEventDate(request.eventDate());
        event.setCreatedOn(LocalDateTime.now());
        event.setPaid(Boolean.TRUE.equals(request.paid()));
        event.setParticipantLimit(request.participantLimit() == null ? 0 : request.participantLimit());
        event.setRequestModeration(!Boolean.FALSE.equals(request.requestModeration()));
        event.setLat(request.location().lat());
        event.setLon(request.location().lon());
        event.setInitiator(entities.user(userId));
        event.setCategory(entities.category(request.category()));
        event.setState(EventState.PENDING);
        return mapper.fullDto(events.save(event));
    }

    public EventFullDto getUserEvent(long userId, long eventId) {
        EventEntity event = entities.event(eventId);
        if (!event.getInitiator().getId().equals(userId))
            throw ApiException.notFound("Event was not found for user");
        return mapper.fullDto(event);
    }

    @Transactional
    public EventFullDto updateUserEvent(long userId, long eventId, EventUpdate update) {
        EventEntity event = entities.getOwnedEvent(userId, eventId);
        if (event.getState() == EventState.PUBLISHED)
            throw ApiException.conflict("Published event cannot be changed");
        applyUpdate(event, update);
        if (update.stateAction() != null) {
            if ("SEND_TO_REVIEW".equals(update.stateAction()))
                event.setState(EventState.PENDING);
            else if ("CANCEL_REVIEW".equals(update.stateAction()))
                event.setState(EventState.CANCELED);
            else
                throw ApiException.badRequest("Unknown user stateAction");
        }
        return mapper.fullDto(event);
    }

    @Transactional
    public EventFullDto updateAdminEvent(long eventId, EventUpdate update) {
        EventEntity event = entities.event(eventId);
        applyUpdate(event, update);
        if (update.stateAction() != null) {
            if ("PUBLISH_EVENT".equals(update.stateAction())) {
                if (event.getState() != EventState.PENDING)
                    throw ApiException.conflict("Only pending events can be published");
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if ("REJECT_EVENT".equals(update.stateAction())) {
                if (event.getState() == EventState.PUBLISHED)
                    throw ApiException.conflict("Published event cannot be rejected");
                event.setState(EventState.CANCELED);
            } else
                throw ApiException.badRequest("Unknown admin stateAction");
        }
        return mapper.fullDto(event);
    }

    public EventFullDto getPublicEvent(long id, String ip, String app) {
        EventEntity event = entities.event(id);
        if (event.getState() != EventState.PUBLISHED)
            throw ApiException.notFound("Published event was not found");
        statistics.recordHit("/events/" + id, ip, app);
        return mapper.fullDto(event);
    }

    public List<EventShortDto> getUserEvents(long userId, int from, int size) {
        entities.user(userId);
        List<EventEntity> all = events.findAll((root, query, cb) -> cb.equal(root.get("initiator").get("id"), userId),
                new OffsetPageRequest(from, size, Sort.by("id").descending())).getContent();
        return mapper.shortDtos(all);
    }

    public List<EventFullDto> searchAdminEvents(List<Long> usersFilter, List<String> states, List<Long> categoryIds,
            LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size) {
        Specification<EventEntity> spec = (root, query, cb) -> cb.conjunction();
        if (usersFilter != null && !usersFilter.isEmpty())
            spec = spec.and((r, q, cb) -> r.get("initiator").get("id").in(usersFilter));
        if (states != null && !states.isEmpty()) {
            List<EventState> parsed = states.stream().map(this::eventState).toList();
            spec = spec.and((r, q, cb) -> r.get("state").in(parsed));
        }
        if (categoryIds != null && !categoryIds.isEmpty())
            spec = spec.and((r, q, cb) -> r.get("category").get("id").in(categoryIds));
        if (rangeStart != null)
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("eventDate"), rangeStart));
        if (rangeEnd != null)
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("eventDate"), rangeEnd));
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw ApiException.badRequest("rangeStart must not be after rangeEnd");
        }
        return events.findAll(spec, new OffsetPageRequest(from, size, Sort.by("eventDate"))).getContent().stream()
                .map(mapper::fullDto).toList();
    }

    public List<EventShortDto> searchPublicEvents(String text, List<Long> categoryIds, Boolean paid,
            LocalDateTime rangeStart, LocalDateTime rangeEnd, boolean onlyAvailable, String sort, int from, int size,
            String ip, String app) {
        LocalDateTime start = rangeStart == null ? LocalDateTime.now() : rangeStart;
        LocalDateTime end = rangeEnd == null ? LocalDateTime.of(9999, 12, 31, 23, 59) : rangeEnd;
        if (start.isAfter(end))
            throw ApiException.badRequest("rangeStart must not be after rangeEnd");
        if (sort != null && !sort.equals("EVENT_DATE") && !sort.equals("VIEWS")) {
            throw ApiException.badRequest("sort must be EVENT_DATE or VIEWS");
        }
        Specification<EventEntity> spec = (root, query, cb) -> cb.equal(root.get("state"), EventState.PUBLISHED);
        if (text != null && !text.isBlank()) {
            String pattern = "%" + text.toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> cb.or(cb.like(cb.lower(r.get("annotation")), pattern),
                    cb.like(cb.lower(r.get("description")), pattern)));
        }
        if (categoryIds != null && !categoryIds.isEmpty())
            spec = spec.and((r, q, cb) -> r.get("category").get("id").in(categoryIds));
        if (paid != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("paid"), paid));
        spec = spec.and((r, q, cb) -> cb.between(r.get("eventDate"), start, end));
        List<EventEntity> found = events.findAll(spec);
        Map<Long, Long> viewCounts = statistics.views(found);
        List<EventShortDto> result = found.stream().filter(e -> !onlyAvailable || e.getParticipantLimit() == 0
                || requests.countByEvent_IdAndStatus(e.getId(), RequestStatus.CONFIRMED) < e.getParticipantLimit())
                .map(e -> mapper.shortDto(e, viewCounts.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toCollection(ArrayList::new));
        if ("VIEWS".equals(sort))
            result.sort(Comparator.comparing(EventShortDto::views).reversed());
        else
            result.sort(Comparator.comparing(EventShortDto::eventDate));
        List<EventShortDto> page = Pagination.slice(result, from, size);
        statistics.recordHit("/events", ip, app);
        return page;
    }

    private void applyUpdate(EventEntity event, EventUpdate update) {
        if (update.title() != null)
            event.setTitle(update.title());
        if (update.annotation() != null)
            event.setAnnotation(update.annotation());
        if (update.description() != null)
            event.setDescription(update.description());
        if (update.category() != null)
            event.setCategory(entities.category(update.category()));
        if (update.eventDate() != null) {
            validateEventDate(update.eventDate());
            event.setEventDate(update.eventDate());
        }
        if (update.location() != null) {
            event.setLat(update.location().lat());
            event.setLon(update.location().lon());
        }
        if (update.paid() != null)
            event.setPaid(update.paid());
        if (update.participantLimit() != null)
            event.setParticipantLimit(update.participantLimit());
        if (update.requestModeration() != null)
            event.setRequestModeration(update.requestModeration());
    }

    private void validateEventDate(LocalDateTime date) {
        if (date.isBefore(LocalDateTime.now().plusHours(2)))
            throw ApiException.badRequest("Event date must be at least two hours in the future");
    }

    private EventState eventState(String value) {
        try {
            return EventState.valueOf(value);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Unknown event state: " + value);
        }
    }
}
