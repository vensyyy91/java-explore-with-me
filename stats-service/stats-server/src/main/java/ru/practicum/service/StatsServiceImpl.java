package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.EndpointHitDto;
import ru.practicum.ViewStats;
import ru.practicum.model.EndpointHit;
import ru.practicum.model.EndpointHitMapper;
import ru.practicum.repository.StatsRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsServiceImpl implements StatsService {
    private final StatsRepository statsRepository;

    @Override
    public EndpointHitDto addHit(EndpointHitDto endpointHitDto) {
        EndpointHit endpointHit = EndpointHitMapper.fromDto(endpointHitDto);
        EndpointHit newEndpointHit = statsRepository.save(endpointHit);
        log.info("Возвращен объект: {}", newEndpointHit);

        return EndpointHitMapper.toDto(newEndpointHit);
    }

    @Override
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, Set<String> uris, boolean unique) {
        try {
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("Start date must be before end date.");
            }
            List<ViewStats> stats = unique
                    ? statsRepository.getStatsWithUnique(start, end)
                    : statsRepository.getStatsWithoutUnique(start, end);
            if (uris != null && !uris.isEmpty()) {
                stats = stats.stream()
                        .filter(stat -> uris.contains(stat.getUri()))
                        .collect(Collectors.toList());
            }
            log.info("Возвращен список статистики: {}", stats);

            return stats;
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Incorrect date format, please specify date in format yyyy-MM-dd HH:mm:ss");
        }
    }
}