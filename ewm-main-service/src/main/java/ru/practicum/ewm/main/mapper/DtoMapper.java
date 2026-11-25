package ru.practicum.ewm.main.mapper;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.model.*;
import ru.practicum.ewm.main.repository.*;
import ru.practicum.ewm.main.service.EventStatisticsService;

@Component
public class DtoMapper {
    private final ParticipationRequestRepository requests;
    private final EventStatisticsService statistics;

    public DtoMapper(ParticipationRequestRepository requests, EventStatisticsService statistics) {
        this.requests = requests;
        this.statistics = statistics;
    }

    public UserDto userDto(UserEntity e) {
        return new UserDto(e.getId(), e.getName(), e.getEmail());
    }

    public UserShortDto shortUser(UserEntity e) {
        return new UserShortDto(e.getId(), e.getName());
    }

    public CategoryDto categoryDto(CategoryEntity e) {
        return new CategoryDto(e.getId(), e.getName());
    }

    public RequestDto requestDto(ParticipationRequestEntity e) {
        return new RequestDto(e.getCreated(), e.getEvent().getId(), e.getId(), e.getRequester().getId(),
                e.getStatus().name());
    }

    public EventShortDto shortDto(EventEntity e, long views) {
        return new EventShortDto(e.getAnnotation(), categoryDto(e.getCategory()),
                requests.countByEvent_IdAndStatus(e.getId(), RequestStatus.CONFIRMED), e.getEventDate(), e.getId(),
                shortUser(e.getInitiator()), e.isPaid(), e.getTitle(), views);
    }

    public EventFullDto fullDto(EventEntity e) {
        return new EventFullDto(e.getAnnotation(), categoryDto(e.getCategory()),
                requests.countByEvent_IdAndStatus(e.getId(), RequestStatus.CONFIRMED), e.getCreatedOn(),
                e.getDescription(), e.getEventDate(), e.getId(), shortUser(e.getInitiator()),
                new Location(e.getLat(), e.getLon()), e.getPublishedOn(), e.isPaid(), e.getParticipantLimit(),
                e.isRequestModeration(), e.getState().name(), e.getTitle(),
                statistics.views(List.of(e)).getOrDefault(e.getId(), 0L));
    }

    public CompilationDto compilationDto(CompilationEntity e) {
        return new CompilationDto(shortDtos(e.getEvents()), e.getId(), e.isPinned(), e.getTitle());
    }

    public CommentDto commentDto(CommentEntity e) {
        return new CommentDto(e.getId(), e.getText(), e.getEvent().getId(), shortUser(e.getAuthor()),
                e.getStatus().name(), e.getCreated(), e.getPublishedOn());
    }

    public List<EventShortDto> shortDtos(List<EventEntity> list) {
        Map<Long, Long> viewCounts = statistics.views(list);
        return list.stream().map(e -> shortDto(e, viewCounts.getOrDefault(e.getId(), 0L))).toList();
    }
}
