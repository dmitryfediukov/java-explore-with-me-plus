package ru.practicum.ewm.main.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.exception.ApiException;
import ru.practicum.ewm.main.mapper.DtoMapper;
import ru.practicum.ewm.main.model.*;
import ru.practicum.ewm.main.repository.CommentRepository;
import ru.practicum.ewm.main.repository.OffsetPageRequest;

@Service
@Transactional(readOnly = true)
public class CommentService {
    private final CommentRepository comments;
    private final EntityResolver entities;
    private final DtoMapper mapper;

    public CommentService(CommentRepository comments, EntityResolver entities, DtoMapper mapper) {
        this.comments = comments;
        this.entities = entities;
        this.mapper = mapper;
    }

    @Transactional
    public CommentDto createComment(long userId, CommentRequest request) {
        UserEntity author = entities.user(userId);
        EventEntity event = entities.event(request.eventId());
        if (event.getState() != EventState.PUBLISHED)
            throw ApiException.conflict("Event is not published");
        CommentEntity comment = new CommentEntity();
        comment.setText(request.text());
        comment.setCreated(LocalDateTime.now());
        comment.setAuthor(author);
        comment.setEvent(event);
        comment.setStatus(CommentStatus.PENDING);
        return mapper.commentDto(comments.save(comment));
    }

    public List<CommentDto> getUserComments(long userId, int from, int size) {
        entities.user(userId);
        return comments.findAll((root, query, cb) -> cb.equal(root.get("author").get("id"), userId),
                new OffsetPageRequest(from, size, Sort.by("created").descending())).getContent().stream()
                .map(mapper::commentDto).toList();
    }

    @Transactional
    public CommentDto updateComment(long userId, long commentId, CommentUpdate update) {
        CommentEntity comment = ownComment(userId, commentId);
        comment.setText(update.text());
        comment.setStatus(CommentStatus.PENDING);
        comment.setPublishedOn(null);
        return mapper.commentDto(comment);
    }

    @Transactional
    public void deleteComment(long userId, long commentId) {
        comments.delete(ownComment(userId, commentId));
    }

    public List<CommentDto> getPublishedComments(long eventId, int from, int size) {
        EventEntity event = entities.event(eventId);
        if (event.getState() != EventState.PUBLISHED)
            throw ApiException.notFound("Published event was not found");
        Specification<CommentEntity> spec = (root, query, cb) -> cb.and(cb.equal(root.get("event").get("id"), eventId),
                cb.equal(root.get("status"), CommentStatus.PUBLISHED));
        return comments.findAll(spec, new OffsetPageRequest(from, size, Sort.by("created").descending())).getContent()
                .stream().map(mapper::commentDto).toList();
    }

    public CommentDto getPublishedComment(long commentId) {
        CommentEntity comment = entities.comment(commentId);
        if (comment.getStatus() != CommentStatus.PUBLISHED || comment.getEvent().getState() != EventState.PUBLISHED)
            throw ApiException.notFound("Published comment was not found");
        return mapper.commentDto(comment);
    }

    public List<CommentDto> searchComments(List<Long> users, List<Long> events, List<String> states,
            LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd))
            throw ApiException.badRequest("rangeStart must not be after rangeEnd");
        Specification<CommentEntity> spec = (root, query, cb) -> cb.conjunction();
        if (users != null && !users.isEmpty())
            spec = spec.and((root, query, cb) -> root.get("author").get("id").in(users));
        if (events != null && !events.isEmpty())
            spec = spec.and((root, query, cb) -> root.get("event").get("id").in(events));
        if (states != null && !states.isEmpty()) {
            List<CommentStatus> parsed = states.stream().map(this::commentStatus).toList();
            spec = spec.and((root, query, cb) -> root.get("status").in(parsed));
        }
        if (rangeStart != null)
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("created"), rangeStart));
        if (rangeEnd != null)
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("created"), rangeEnd));
        return comments.findAll(spec, new OffsetPageRequest(from, size, Sort.by("created").descending())).getContent()
                .stream().map(mapper::commentDto).toList();
    }

    @Transactional
    public CommentDto moderateComment(long commentId, CommentModeration moderation) {
        CommentEntity comment = entities.comment(commentId);
        CommentStatus status = commentStatus(moderation.status());
        if (status != CommentStatus.PUBLISHED && status != CommentStatus.REJECTED)
            throw ApiException.badRequest("Status must be PUBLISHED or REJECTED");
        if (comment.getStatus() == status)
            throw ApiException.conflict("Comment already has status " + status.name());
        comment.setStatus(status);
        comment.setPublishedOn(status == CommentStatus.PUBLISHED ? LocalDateTime.now() : null);
        return mapper.commentDto(comment);
    }

    @Transactional
    public void deleteCommentByAdmin(long commentId) {
        comments.delete(entities.comment(commentId));
    }

    private CommentEntity ownComment(long userId, long commentId) {
        entities.user(userId);
        CommentEntity comment = entities.comment(commentId);
        if (!comment.getAuthor().getId().equals(userId))
            throw ApiException.notFound("Comment does not belong to user");
        return comment;
    }

    private CommentStatus commentStatus(String value) {
        try {
            return CommentStatus.valueOf(value);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Unknown comment status: " + value);
        }
    }
}
