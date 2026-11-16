package ru.practicum.ewm.main.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class Dtos {
    private Dtos() {
    }

    public record UserRequest(@NotBlank @Size(min = 2, max = 250) String name,
            @NotBlank @Email @Size(min = 6, max = 254) String email) {
    }

    public record UserDto(Long id, String name, String email) {
    }

    public record UserShortDto(Long id, String name) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 50) String name) {
    }

    public record CategoryDto(Long id, String name) {
    }

    public record Location(@NotNull Double lat, @NotNull Double lon) {
    }

    public record EventRequest(@NotBlank @Size(min = 3, max = 120) String title,
            @NotBlank @Size(min = 20, max = 2000) String annotation,
            @NotBlank @Size(min = 20, max = 7000) String description, @NotNull Long category,
            @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime eventDate,
            @NotNull @Valid Location location, Boolean paid, @PositiveOrZero Integer participantLimit,
            Boolean requestModeration) {
    }

    public record EventUpdate(@Size(min = 3, max = 120) String title, @Size(min = 20, max = 2000) String annotation,
            @Size(min = 20, max = 7000) String description, Long category,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime eventDate, @Valid Location location,
            Boolean paid, @PositiveOrZero Integer participantLimit, Boolean requestModeration, String stateAction) {
    }

    public record EventShortDto(String annotation, CategoryDto category, Long confirmedRequests,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime eventDate, Long id, UserShortDto initiator,
            Boolean paid, String title, Long views) {
    }

    public record EventFullDto(String annotation, CategoryDto category, Long confirmedRequests,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdOn, String description,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime eventDate, Long id, UserShortDto initiator,
            Location location, @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime publishedOn, Boolean paid,
            Integer participantLimit, Boolean requestModeration, String state, String title, Long views) {
    }

    public record CompilationRequest(List<Long> events, Boolean pinned, @NotBlank @Size(max = 50) String title) {
    }

    public record CompilationUpdate(List<Long> events, Boolean pinned, @Size(max = 50) String title) {
    }

    public record CompilationDto(List<EventShortDto> events, Long id, Boolean pinned, String title) {
    }

    public record RequestDto(@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime created, Long event, Long id,
            Long requester, String status) {
    }

    public record RequestStatusUpdate(@NotNull List<Long> requestIds, @NotBlank String status) {
    }

    public record RequestStatusResult(List<RequestDto> confirmedRequests, List<RequestDto> rejectedRequests) {
    }
}
