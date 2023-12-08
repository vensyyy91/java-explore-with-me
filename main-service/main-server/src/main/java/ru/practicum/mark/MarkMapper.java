package ru.practicum.mark;

import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;

@UtilityClass
public class MarkMapper {
    public MarkDto toDto(Mark mark) {
        return MarkDto.builder()
                .id(mark.getId())
                .userId(mark.getUser().getId())
                .eventId(mark.getEvent().getId())
                .mark(mark.getMark())
                .markedOn(mark.getMarkedOn())
                .message(mark.getMessage())
                .build();
    }

    public Mark fromDto(NewMarkDto newMarkDto) {
        return Mark.builder()
                .mark(newMarkDto.getMark())
                .markedOn(LocalDateTime.now())
                .message(newMarkDto.getMessage())
                .build();
    }
}