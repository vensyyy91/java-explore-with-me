package ru.practicum.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.request.ParticipationRequestDto;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventRequestStatusUpdateResult {
    private List<ParticipationRequestDto> confirmedRequests = new ArrayList<>();
    private List<ParticipationRequestDto> rejectedRequests = new ArrayList<>();
}