package ru.practicum.ewm.main.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
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
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.client.StatsClientException;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

@Service
@Transactional
public class MainService {
    private final UserRepository users;
    private final CategoryRepository categories;
    private final EventRepository events;
    private final ParticipationRequestRepository requests;
    private final CompilationRepository compilations;
    private final StatsClient stats;

    public MainService(UserRepository users, CategoryRepository categories, EventRepository events,
            ParticipationRequestRepository requests, CompilationRepository compilations, StatsClient stats) {
        this.users = users;
        this.categories = categories;
        this.events = events;
        this.requests = requests;
        this.compilations = compilations;
        this.stats = stats;
    }

    public List<UserDto> getUsers(List<Long> ids, int from, int size) {
        List<UserEntity> all = ids == null || ids.isEmpty() ? users.findAll() : users.findAllById(ids);
        return slice(all, from, size).stream().map(this::userDto).toList();
    }

    public UserDto createUser(UserRequest request) {
        return userDto(users.save(new UserEntity(request.name(), request.email())));
    }

    public void deleteUser(long id) {
        users.delete(user(id));
    }

    public List<CategoryDto> getCategories(int from, int size) {
        validatePage(from, size);
        return categories.findAll(new OffsetPageRequest(from, size, Sort.by("id"))).getContent().stream()
                .map(this::categoryDto).toList();
    }

    public CategoryDto getCategory(long id) {
        return categoryDto(category(id));
    }

    public CategoryDto createCategory(CategoryRequest request) {
        return categoryDto(categories.save(new CategoryEntity(request.name())));
    }

    public CategoryDto updateCategory(long id, CategoryRequest request) {
        CategoryEntity category = category(id);
        category.setName(request.name());
        return categoryDto(categories.save(category));
    }

    public void deleteCategory(long id) {
        categories.delete(category(id));
    }

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
        event.setInitiator(user(userId));
        event.setCategory(category(request.category()));
        event.setState(EventState.PENDING);
        return fullDto(events.save(event));
    }

    public EventFullDto getUserEvent(long userId, long eventId) {
        EventEntity event = event(eventId);
        if (!event.getInitiator().getId().equals(userId))
            throw ApiException.notFound("Event was not found for user");
        return fullDto(event);
    }

    public EventFullDto updateUserEvent(long userId, long eventId, EventUpdate update) {
        EventEntity event = getOwnedEvent(userId, eventId);
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
        return fullDto(events.save(event));
    }

    public EventFullDto updateAdminEvent(long eventId, EventUpdate update) {
        EventEntity event = event(eventId);
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
        return fullDto(events.save(event));
    }

    public EventFullDto getPublicEvent(long id, String ip, String app) {
        EventEntity event = event(id);
        if (event.getState() != EventState.PUBLISHED)
            throw ApiException.notFound("Published event was not found");
        recordHit("/events/" + id, ip, app);
        return fullDto(event);
    }

    public List<EventShortDto> getUserEvents(long userId, int from, int size) {
        user(userId);
        validatePage(from, size);
        List<EventEntity> all = events.findAll((root, query, cb) -> cb.equal(root.get("initiator").get("id"), userId),
                new OffsetPageRequest(from, size, Sort.by("id").descending())).getContent();
        return shortDtos(all);
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
        validatePage(from, size);
        return events.findAll(spec, new OffsetPageRequest(from, size, Sort.by("eventDate"))).getContent().stream()
                .map(this::fullDto).toList();
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
        validatePage(from, size);
        List<EventEntity> found = events.findAll(spec);
        Map<Long, Long> viewCounts = views(found);
        List<EventShortDto> result = found.stream().filter(e -> !onlyAvailable || e.getParticipantLimit() == 0
                || requests.countByEvent_IdAndStatus(e.getId(), RequestStatus.CONFIRMED) < e.getParticipantLimit())
                .map(e -> shortDto(e, viewCounts.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toCollection(ArrayList::new));
        if ("VIEWS".equals(sort))
            result.sort(Comparator.comparing(EventShortDto::views).reversed());
        else
            result.sort(Comparator.comparing(EventShortDto::eventDate));
        List<EventShortDto> page = slice(result, from, size);
        recordHit("/events", ip, app);
        return page;
    }

    public RequestDto createRequest(long requesterId, long eventId) {
        UserEntity requester = user(requesterId);
        EventEntity event = event(eventId);
        if (event.getInitiator().getId().equals(requesterId))
            throw ApiException.conflict("Initiator cannot request own event");
        if (event.getState() != EventState.PUBLISHED)
            throw ApiException.conflict("Event is not published");
        if (requests.existsByEvent_IdAndRequester_Id(eventId, requesterId))
            throw ApiException.conflict("Request already exists");
        long confirmed = requests.countByEvent_IdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() > 0 && confirmed >= event.getParticipantLimit())
            throw ApiException.conflict("Participant limit is reached");
        ParticipationRequestEntity request = new ParticipationRequestEntity();
        request.setCreated(LocalDateTime.now());
        request.setEvent(event);
        request.setRequester(requester);
        request.setStatus(!event.isRequestModeration() || event.getParticipantLimit() == 0 ? RequestStatus.CONFIRMED
                : RequestStatus.PENDING);
        return requestDto(requests.save(request));
    }

    public List<RequestDto> getRequestsByRequester(long requesterId) {
        user(requesterId);
        return requests.findByRequester_IdOrderByCreatedAsc(requesterId).stream().map(this::requestDto).toList();
    }

    public List<RequestDto> getRequestsByEvent(long userId, long eventId) {
        getOwnedEvent(userId, eventId);
        return requests.findByEvent_IdOrderByCreatedAsc(eventId).stream().map(this::requestDto).toList();
    }

    public RequestDto cancelRequest(long userId, long requestId) {
        ParticipationRequestEntity request = request(requestId);
        if (!request.getRequester().getId().equals(userId))
            throw ApiException.notFound("Request does not belong to user");
        request.setStatus(RequestStatus.CANCELED);
        return requestDto(requests.save(request));
    }

    public RequestStatusResult updateRequests(long userId, long eventId, RequestStatusUpdate update) {
        EventEntity event = getOwnedEvent(userId, eventId);
        if (!"CONFIRMED".equals(update.status()) && !"REJECTED".equals(update.status()))
            throw ApiException.badRequest("Status must be CONFIRMED or REJECTED");
        List<RequestDto> confirmed = new ArrayList<>();
        List<RequestDto> rejected = new ArrayList<>();
        long alreadyConfirmed = requests.countByEvent_IdAndStatus(eventId, RequestStatus.CONFIRMED);
        if ("CONFIRMED".equals(update.status()) && event.getParticipantLimit() > 0
                && alreadyConfirmed >= event.getParticipantLimit()) {
            throw ApiException.conflict("Participant limit is reached");
        }
        for (Long id : update.requestIds()) {
            ParticipationRequestEntity request = request(id);
            if (!request.getEvent().getId().equals(eventId) || request.getStatus() != RequestStatus.PENDING)
                throw ApiException.conflict("Request is not pending for this event");
            if ("REJECTED".equals(update.status())) {
                request.setStatus(RequestStatus.REJECTED);
                rejected.add(requestDto(request));
            } else if (event.getParticipantLimit() > 0 && alreadyConfirmed >= event.getParticipantLimit()) {
                request.setStatus(RequestStatus.REJECTED);
                rejected.add(requestDto(request));
            } else {
                request.setStatus(RequestStatus.CONFIRMED);
                confirmed.add(requestDto(request));
                alreadyConfirmed++;
            }
            requests.save(request);
        }
        return new RequestStatusResult(confirmed, rejected);
    }

    public CompilationDto createCompilation(CompilationRequest request) {
        CompilationEntity entity = new CompilationEntity();
        entity.setTitle(request.title());
        entity.setPinned(Boolean.TRUE.equals(request.pinned()));
        entity.setEvents(resolveEvents(request.events()));
        return compilationDto(compilations.save(entity));
    }

    public CompilationDto updateCompilation(long id, CompilationUpdate update) {
        CompilationEntity entity = compilation(id);
        if (update.title() != null)
            entity.setTitle(update.title());
        if (update.pinned() != null)
            entity.setPinned(update.pinned());
        if (update.events() != null)
            entity.setEvents(resolveEvents(update.events()));
        return compilationDto(compilations.save(entity));
    }

    public void deleteCompilation(long id) {
        compilations.delete(compilation(id));
    }

    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        List<CompilationEntity> all = pinned == null ? compilations.findAll() : compilations.findByPinned(pinned);
        return slice(all, from, size).stream().map(this::compilationDto).toList();
    }

    public CompilationDto getCompilation(long id) {
        return compilationDto(compilation(id));
    }

    private EventEntity getOwnedEvent(long userId, long eventId) {
        EventEntity event = event(eventId);
        if (!event.getInitiator().getId().equals(userId))
            throw ApiException.notFound("Event was not found for user");
        return event;
    }

    private void applyUpdate(EventEntity event, EventUpdate update) {
        if (update.title() != null)
            event.setTitle(update.title());
        if (update.annotation() != null)
            event.setAnnotation(update.annotation());
        if (update.description() != null)
            event.setDescription(update.description());
        if (update.category() != null)
            event.setCategory(category(update.category()));
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

    private UserEntity user(long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User with id=" + id + " was not found"));
    }

    private CategoryEntity category(long id) {
        return categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category with id=" + id + " was not found"));
    }

    private EventEntity event(long id) {
        return events.findById(id).orElseThrow(() -> ApiException.notFound("Event with id=" + id + " was not found"));
    }

    private ParticipationRequestEntity request(long id) {
        return requests.findById(id)
                .orElseThrow(() -> ApiException.notFound("Request with id=" + id + " was not found"));
    }

    private CompilationEntity compilation(long id) {
        return compilations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Compilation with id=" + id + " was not found"));
    }

    private List<EventEntity> resolveEvents(List<Long> ids) {
        if (ids == null || ids.isEmpty())
            return new ArrayList<>();
        List<EventEntity> result = events.findAllById(ids);
        if (result.size() != ids.stream().distinct().count())
            throw ApiException.notFound("One or more events were not found");
        return result;
    }

    private UserDto userDto(UserEntity e) {
        return new UserDto(e.getId(), e.getName(), e.getEmail());
    }

    private UserShortDto shortUser(UserEntity e) {
        return new UserShortDto(e.getId(), e.getName());
    }

    private CategoryDto categoryDto(CategoryEntity e) {
        return new CategoryDto(e.getId(), e.getName());
    }

    private RequestDto requestDto(ParticipationRequestEntity e) {
        return new RequestDto(e.getCreated(), e.getEvent().getId(), e.getId(), e.getRequester().getId(),
                e.getStatus().name());
    }

    private EventShortDto shortDto(EventEntity e, long views) {
        return new EventShortDto(e.getAnnotation(), categoryDto(e.getCategory()),
                requests.countByEvent_IdAndStatus(e.getId(), RequestStatus.CONFIRMED), e.getEventDate(), e.getId(),
                shortUser(e.getInitiator()), e.isPaid(), e.getTitle(), views);
    }

    private EventFullDto fullDto(EventEntity e) {
        return new EventFullDto(e.getAnnotation(), categoryDto(e.getCategory()),
                requests.countByEvent_IdAndStatus(e.getId(), RequestStatus.CONFIRMED), e.getCreatedOn(),
                e.getDescription(), e.getEventDate(), e.getId(), shortUser(e.getInitiator()),
                new Location(e.getLat(), e.getLon()), e.getPublishedOn(), e.isPaid(), e.getParticipantLimit(),
                e.isRequestModeration(), e.getState().name(), e.getTitle(),
                views(List.of(e)).getOrDefault(e.getId(), 0L));
    }

    private CompilationDto compilationDto(CompilationEntity e) {
        return new CompilationDto(shortDtos(e.getEvents()), e.getId(), e.isPinned(), e.getTitle());
    }

    private List<EventShortDto> shortDtos(List<EventEntity> list) {
        Map<Long, Long> viewCounts = views(list);
        return list.stream().map(e -> shortDto(e, viewCounts.getOrDefault(e.getId(), 0L))).toList();
    }

    private Map<Long, Long> views(Collection<EventEntity> list) {
        if (list.isEmpty())
            return Map.of();
        List<String> uris = list.stream().map(e -> "/events/" + e.getId()).toList();
        try {
            List<ViewStats> found = stats.getStats(LocalDateTime.of(2000, 1, 1, 0, 0),
                    LocalDateTime.now().plusSeconds(1), uris, true);
            return found.stream()
                    .collect(Collectors.toMap(s -> parseEventId(s.getUri()), ViewStats::getHits, (a, b) -> a));
        } catch (StatsClientException | IllegalArgumentException e) {
            return Map.of();
        }
    }

    private Long parseEventId(String uri) {
        try {
            return Long.parseLong(uri.substring(uri.lastIndexOf('/') + 1));
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    private void recordHit(String uri, String ip, String app) {
        try {
            stats.saveHit(new EndpointHit(null, app == null ? "ewm-main-service" : app, uri,
                    ip == null ? "unknown" : ip, LocalDateTime.now()));
        } catch (StatsClientException ignored) {
        }
    }

    private void validatePage(int from, int size) {
        if (from < 0 || size < 1)
            throw ApiException.badRequest("from must be non-negative and size must be positive");
    }

    private <T> List<T> slice(List<T> list, int from, int size) {
        if (from < 0 || size < 1)
            throw ApiException.badRequest("from must be non-negative and size must be positive");
        if (from >= list.size())
            return List.of();
        return list.subList(from, Math.min(list.size(), from + size));
    }
}
