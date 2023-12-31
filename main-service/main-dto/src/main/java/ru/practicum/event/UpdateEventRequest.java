package ru.practicum.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import ru.practicum.validation.ValidEventDate;

import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class UpdateEventRequest {
    @Size(min = 20, max = 2000, message = "Event annotation length must be between 20 and 2000 characters")
    private String annotation;
    private Long category;
    @Size(min = 20, max = 7000, message = "Event description length must be between 20 and 7000 characters")
    private String description;
    @ValidEventDate
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventDate;
    private Location location;
    private Boolean paid;
    @PositiveOrZero
    private Integer participantLimit;
    private Boolean requestModeration;
    @Size(min = 3, max = 120, message = "Event title length must be between 3 and 120 characters")
    private String title;
}