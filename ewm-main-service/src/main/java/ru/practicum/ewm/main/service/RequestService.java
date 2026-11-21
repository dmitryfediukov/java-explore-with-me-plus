package ru.practicum.ewm.main.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.exception.ApiException;
import ru.practicum.ewm.main.model.*;
import ru.practicum.ewm.main.repository.*;
import ru.practicum.ewm.main.mapper.DtoMapper;

@Service
@Transactional(readOnly = true)
public class RequestService {
    private final ParticipationRequestRepository requests;
    private final EntityResolver entities;
    private final DtoMapper mapper;

    public RequestService(ParticipationRequestRepository requests, EntityResolver entities, DtoMapper mapper) {
        this.requests = requests;
        this.entities = entities;
        this.mapper = mapper;
    }

    @Transactional
    public RequestDto createRequest(long requesterId, long eventId) {
        UserEntity requester = entities.user(requesterId);
        EventEntity event = entities.event(eventId);
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
        return mapper.requestDto(requests.save(request));
    }

    public List<RequestDto> getRequestsByRequester(long requesterId) {
        entities.user(requesterId);
        return requests.findByRequester_IdOrderByCreatedAsc(requesterId).stream().map(mapper::requestDto).toList();
    }

    public List<RequestDto> getRequestsByEvent(long userId, long eventId) {
        entities.getOwnedEvent(userId, eventId);
        return requests.findByEvent_IdOrderByCreatedAsc(eventId).stream().map(mapper::requestDto).toList();
    }

    @Transactional
    public RequestDto cancelRequest(long userId, long requestId) {
        ParticipationRequestEntity request = entities.request(requestId);
        if (!request.getRequester().getId().equals(userId))
            throw ApiException.notFound("Request does not belong to user");
        request.setStatus(RequestStatus.CANCELED);
        return mapper.requestDto(request);
    }

    @Transactional
    public RequestStatusResult updateRequests(long userId, long eventId, RequestStatusUpdate update) {
        EventEntity event = entities.getOwnedEvent(userId, eventId);
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
            ParticipationRequestEntity request = entities.request(id);
            if (!request.getEvent().getId().equals(eventId) || request.getStatus() != RequestStatus.PENDING)
                throw ApiException.conflict("Request is not pending for this event");
            if ("REJECTED".equals(update.status())) {
                request.setStatus(RequestStatus.REJECTED);
                rejected.add(mapper.requestDto(request));
            } else if (event.getParticipantLimit() > 0 && alreadyConfirmed >= event.getParticipantLimit()) {
                request.setStatus(RequestStatus.REJECTED);
                rejected.add(mapper.requestDto(request));
            } else {
                request.setStatus(RequestStatus.CONFIRMED);
                confirmed.add(mapper.requestDto(request));
                alreadyConfirmed++;
            }
        }
        return new RequestStatusResult(confirmed, rejected);
    }
}
