package ru.practicum.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.NewEndpointHitDto;
import ru.practicum.category.Category;
import ru.practicum.category.CategoryRepository;
import ru.practicum.enums.State;
import ru.practicum.enums.StateActionAdmin;
import ru.practicum.enums.StateActionUser;
import ru.practicum.enums.Status;
import ru.practicum.exception.IllegalOperationException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.request.*;
import ru.practicum.user.User;
import ru.practicum.user.UserRepository;

import javax.persistence.criteria.Predicate;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {
    private final static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final EventClient eventClient;

    @Override
    public List<EventFullDto> getAllEvents(Set<Long> users,
                                           Set<String> states,
                                           Set<Long> categories,
                                           String rangeStart,
                                           String rangeEnd,
                                           int from,
                                           int size) {

        Specification<Event> specification = getEventQuery(users, states, categories, rangeStart, rangeEnd);
        List<Event> events = eventRepository.findAll(specification, PageRequest.of(from / size, size)).getContent();
        log.info("Возвращен список событий: {}", events);

        return events.stream().map(EventMapper::toFullDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(long eventId, UpdateEventAdminRequest updateEventAdminRequest) {
        Event event = getEvent(eventId);
        updateEvent(event, updateEventAdminRequest);
        log.info("Обновлено событие: {}", event);

        return EventMapper.toFullDto(event);
    }

    @Override
    public List<EventFullDto> getPublishedEvents(String text,
                                                 Set<Long> categories,
                                                 Boolean paid,
                                                 String rangeStart,
                                                 String rangeEnd,
                                                 boolean onlyAvailable,
                                                 String sort,
                                                 int from,
                                                 int size,
                                                 HttpServletRequest request) {

        Specification<Event> specification = getPublishedEventQuery(
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable
        );
        List<Event> events;
        if (sort != null) {
            Sort eventSort;
            switch (sort) {
                case "EVENT_DATE":
                    eventSort = Sort.by(Sort.Direction.ASC, "eventDate");
                    break;
                case "VIEWS":
                    eventSort = Sort.by(Sort.Direction.DESC, "views");
                    break;
                default:
                    throw new IllegalArgumentException("Sort must be EVENT_DATE or VIEWS");
            }
            events = eventRepository.findAll(specification, PageRequest.of(from / size, size, eventSort)).getContent();
        } else {
            events = eventRepository.findAll(specification, PageRequest.of(from / size, size)).getContent();
        }
        eventClient.sendStatistic(NewEndpointHitDto.builder()
                .app("ewm-main-service")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build());
        log.info("Возвращен список событий: {}", events);

        return events.stream().map(EventMapper::toFullDto).collect(Collectors.toList());
    }

    @Override
    public EventFullDto getPublishedEventById(long id, HttpServletRequest request) {
        Event event = getEvent(id);
        if (event.getState() != State.PUBLISHED) {
            throw new NotFoundException("Event with id=" + id + " was not found");
        }
        event.setViews(event.getViews() + 1);
        eventClient.sendStatistic(NewEndpointHitDto.builder()
                .app("ewm-main-service")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build());
        log.info("Возвращено событие: {}", event);

        return EventMapper.toFullDto(event);
    }

    @Override
    public List<EventShortDto> getUserEvents(long userId, int from, int size) {
        getUser(userId);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, PageRequest.of(from / size, size)).getContent();
        log.info("Возвращен список событий: {}", events);

        return events.stream().map(EventMapper::toShortDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto addNewEvent(long userId, NewEventDto newEventDto) {
        checkEventDate(newEventDto.getEventDate());
        Event event = EventMapper.fromDto(newEventDto);
        event.setCategory(getCategory(newEventDto.getCategory()));
        event.setInitiator(getUser(userId));
        Event newEvent = eventRepository.save(event);
        log.info("Добавлено событие: {}", newEvent);

        return EventMapper.toFullDto(newEvent);
    }

    @Override
    public EventFullDto getUserEventById(long userId, long eventId) {
        getUser(userId);
        Event event = getEvent(eventId);
        checkEventInitiator(userId, event);
        log.info("Возвращено событие: {}", event);

        return EventMapper.toFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateUserEvent(long userId, long eventId, UpdateEventUserRequest updateRequest) {
        getUser(userId);
        Event event = getEvent(eventId);
        checkEventInitiator(userId, event);
        checkEventNotPublished(event);
        checkEventDate(updateRequest.getEventDate());
        updateEvent(event, updateRequest);
        log.info("Обновлено событие: {}", event);

        return EventMapper.toFullDto(event);
    }

    @Override
    public List<ParticipationRequestDto> getUserEventRequests(long userId, long eventId) {
        getUser(userId);
        getEvent(eventId);
        List<Request> requests = requestRepository.findAllByEventId(eventId);
        log.info("Возвращен список событий: {}", requests);

        return requests.stream().map(RequestMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateUserEventRequestsStatus(long userId,
                                                                        long eventId,
                                                                        EventRequestStatusUpdateRequest updateRequest) {
        getUser(userId);
        Event event = getEvent(eventId);
        List<Request> requests = requestRepository.findAllById(updateRequest.getRequestIds());
        EventRequestStatusUpdateResult result = new EventRequestStatusUpdateResult();
        for (Request request : requests) {
            if (request.getStatus() != Status.PENDING) {
                throw new IllegalArgumentException("Request must have status PENDING");
            }
            if (updateRequest.getStatus() == Status.REJECTED) {
                request.setStatus(Status.REJECTED);
                result.getRejectedRequests().add(RequestMapper.toDto(request));
            } else {
                if (event.getParticipantLimit() == 0 || event.getConfirmedRequests() < event.getParticipantLimit()) {
                    request.setStatus(Status.CONFIRMED);
                    result.getConfirmedRequests().add(RequestMapper.toDto(request));
                    event.setConfirmedRequests(event.getConfirmedRequests() + 1);
                } else {
                    request.setStatus(Status.REJECTED);
                    result.getRejectedRequests().add(RequestMapper.toDto(request));
                }
            }
        }
        log.info("Возвращен результат обновления статусов запросов: {}", result);

        return result;
    }

    private Event getEvent(long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private User getUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }

    private Category getCategory(long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }

    private Request getRequest(long reqId) {
        return requestRepository.findById(reqId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + reqId + " was not found"));
    }

    private void checkEventDate(String date) {
        if (date == null) return;
        LocalDateTime eventDate = LocalDateTime.parse(date, FORMATTER);
        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new IllegalArgumentException("The event date and time cannot be earlier than two hours from the current moment.");
        }
    }

    private void checkEventInitiator(long userId, Event event) {
        if (event.getInitiator().getId() != userId) {
            throw new NotFoundException("Event with id=" + event.getId() + " was not found");
        }
    }

    private void checkEventNotPublished(Event event) {
        if (event.getState() == State.PUBLISHED) {
            throw new IllegalOperationException("Only pending or canceled events can be changed");
        }
    }

    private Specification<Event> getEventQuery(Set<Long> users,
                                               Set<String> states,
                                               Set<Long> categories,
                                               String rangeStart,
                                               String rangeEnd) {
        return (event, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (users != null && !users.isEmpty()) {
                predicates.add(event.get("initiator").get("id").in(users));
            }
            if (states != null && !states.isEmpty()) {
                Set<State> statesSet = states.stream().map(State::valueOf).collect(Collectors.toSet());
                predicates.add(event.get("state").in(statesSet));
            }
            if (categories != null && !categories.isEmpty()) {
                predicates.add(event.get("category").get("id").in(categories));
            }
            try {
                if (rangeStart != null && rangeEnd != null) {
                    LocalDateTime start = LocalDateTime.parse(rangeStart, FORMATTER);
                    LocalDateTime end = LocalDateTime.parse(rangeEnd, FORMATTER);
                    predicates.add(builder.between(event.get("eventDate"), start, end));
                } else if (rangeStart != null) {
                    LocalDateTime start = LocalDateTime.parse(rangeStart, FORMATTER);
                    predicates.add(builder.greaterThanOrEqualTo(event.get("eventDate"), start));
                } else if (rangeEnd != null) {
                    LocalDateTime end = LocalDateTime.parse(rangeEnd, FORMATTER);
                    predicates.add(builder.lessThanOrEqualTo(event.get("eventDate"), end));
                }
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Incorrect date format, please specify date in format yyyy-MM-dd HH:mm:ss");
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<Event> getPublishedEventQuery(String text,
                                                        Set<Long> categories,
                                                        Boolean paid,
                                                        String rangeStart,
                                                        String rangeEnd,
                                                        boolean onlyAvailable) {
        return (event, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(event.get("state"), State.PUBLISHED));
            if (text != null) {
                predicates.add(builder.like(
                        builder.lower(event.get("annotation")),
                        builder.lower(builder.literal("%" + text + "%"))
                ));
            }
            if (categories != null && !categories.isEmpty()) {
                predicates.add(event.get("category").get("id").in(categories));
            }
            if (paid != null) {
                predicates.add(builder.equal(event.get("paid"), paid));
            }
            try {
                if (rangeStart != null && rangeEnd != null) {
                    LocalDateTime start = LocalDateTime.parse(rangeStart, FORMATTER);
                    LocalDateTime end = LocalDateTime.parse(rangeEnd, FORMATTER);
                    predicates.add(builder.between(event.get("eventDate"), start, end));
                } else if (rangeStart != null) {
                    LocalDateTime start = LocalDateTime.parse(rangeStart, FORMATTER);
                    predicates.add(builder.greaterThanOrEqualTo(event.get("eventDate"), start));
                } else if (rangeEnd != null) {
                    LocalDateTime end = LocalDateTime.parse(rangeEnd, FORMATTER);
                    predicates.add(builder.lessThanOrEqualTo(event.get("eventDate"), end));
                } else {
                    predicates.add(builder.greaterThan(event.get("eventDate"), LocalDateTime.now()));
                }
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Incorrect date format, please specify date in format yyyy-MM-dd HH:mm:ss");
            }
            if (onlyAvailable) {
                predicates.add(builder.or(
                        builder.equal(event.get("confirmedRequests"), 0),
                        builder.lessThan(event.get("confirmedRequests"), event.get("participantLimit"))
                ));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private <T extends UpdateEventRequest> void updateEvent(Event event, T request) {
        String annotation = request.getAnnotation();
        Long category = request.getCategory();
        String description = request.getDescription();
        String eventDate = request.getEventDate();
        Location location = request.getLocation();
        Boolean paid = request.getPaid();
        Integer participantLimit = request.getParticipantLimit();
        Boolean requestModeration = request.getRequestModeration();
        String title = request.getTitle();
        if (annotation != null) {
            event.setAnnotation(annotation);
        }
        if (category != null) {
            event.setCategory(categoryRepository.findById(category)
                    .orElseThrow(() -> new NotFoundException("Category with id=" + category + " was not found")));
        }
        if (description != null) {
            event.setDescription(description);
        }
        if (eventDate != null) {
            try {
                event.setEventDate(LocalDateTime.parse(eventDate, FORMATTER));
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Incorrect date format, please specify date in format yyyy-MM-dd HH:mm:ss");
            }
        }
        if (location != null) {
            event.setLat(location.getLat());
            event.setLon(location.getLon());
        }
        if (paid != null) {
            event.setPaid(paid);
        }
        if (participantLimit != null) {
            event.setParticipantLimit(participantLimit);
        }
        if (requestModeration != null) {
            event.setRequestModeration(requestModeration);
        }
        if (title != null) {
            event.setTitle(title);
        }
        if (request instanceof UpdateEventAdminRequest) {
            StateActionAdmin stateAction = ((UpdateEventAdminRequest) request).getStateAction();
            if (stateAction != null) {
                if (stateAction == StateActionAdmin.PUBLISH_EVENT) {
                    event.setState(State.PUBLISHED);
                } else if (stateAction == StateActionAdmin.REJECT_EVENT) {
                    event.setState(State.CANCELED);
                }
            }
        }
        if (request instanceof UpdateEventUserRequest) {
            StateActionUser stateAction = ((UpdateEventUserRequest) request).getStateAction();
            if (stateAction != null) {
                if (stateAction == StateActionUser.SEND_TO_REVIEW) {
                    event.setState(State.PENDING);
                } else if (stateAction == StateActionUser.CANCEL_REVIEW) {
                    event.setState(State.CANCELED);
                }
            }
        }
    }
}