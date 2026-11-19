package ru.practicum.ewm.main.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.ewm.main.model.ParticipationRequestEntity;
import ru.practicum.ewm.main.model.RequestStatus;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequestEntity, Long> {
    List<ParticipationRequestEntity> findByRequester_IdOrderByCreatedAsc(Long requesterId);

    List<ParticipationRequestEntity> findByEvent_IdOrderByCreatedAsc(Long eventId);

    long countByEvent_IdAndStatus(Long eventId, RequestStatus status);

    boolean existsByEvent_IdAndRequester_Id(Long eventId, Long requesterId);
}
