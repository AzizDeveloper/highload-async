package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRequestDTO;
import dev.aziz.highloadasyncservice.exception.InvalidTaskMessageException;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskRegistrationServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskRegistrationService taskRegistrationService;

    private UUID eventId;
    private TaskRequestDTO request;
    private Task existingTask;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();

        request = new TaskRequestDTO();
        request.setEventId(eventId);
        request.setName("  my task  ");
        request.setDuration(42L);

        existingTask = new Task();
        existingTask.setEventId(eventId);
        existingTask.setName("my task");
        existingTask.setDuration(42L);
    }

    @Test
    void shouldInsertNewTaskWhenRequestIsValid() {
        when(taskRepository.insertIfAbsent(eventId, "my task", 42L))
                .thenReturn(1);

        assertDoesNotThrow(() -> taskRegistrationService.register(request));

        verify(taskRepository).insertIfAbsent(eventId, "my task", 42L);
        verify(taskRepository, never()).findByEventId(any(UUID.class));
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldNormalizeNameBeforeInsert() {
        request.setName("    another task    ");

        when(taskRepository.insertIfAbsent(eventId, "another task", 42L))
                .thenReturn(1);

        assertDoesNotThrow(() -> taskRegistrationService.register(request));

        verify(taskRepository).insertIfAbsent(eventId, "another task", 42L);
        verify(taskRepository, never()).findByEventId(any(UUID.class));
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldIgnoreDuplicateWhenSameEventIdSameNameAndSameDuration() {
        when(taskRepository.insertIfAbsent(eventId, "my task", 42L))
                .thenReturn(0);

        when(taskRepository.findByEventId(eventId))
                .thenReturn(Optional.of(existingTask));

        assertDoesNotThrow(() -> taskRegistrationService.register(request));

        verify(taskRepository).insertIfAbsent(eventId, "my task", 42L);
        verify(taskRepository).findByEventId(eventId);
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldIgnoreDuplicateWhenIncomingNameHasWhitespaceButExistingNameIsAlreadyNormalized() {
        request.setName("      my task      ");
        existingTask.setName("my task");

        when(taskRepository.insertIfAbsent(eventId, "my task", 42L))
                .thenReturn(0);

        when(taskRepository.findByEventId(eventId))
                .thenReturn(Optional.of(existingTask));

        assertDoesNotThrow(() -> taskRegistrationService.register(request));

        verify(taskRepository).insertIfAbsent(eventId, "my task", 42L);
        verify(taskRepository).findByEventId(eventId);
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldThrowWhenSameEventIdButDifferentName() {
        existingTask.setName("another task");

        when(taskRepository.insertIfAbsent(eventId, "my task", 42L))
                .thenReturn(0);

        when(taskRepository.findByEventId(eventId))
                .thenReturn(Optional.of(existingTask));

        InvalidTaskMessageException exception = assertThrows(
                InvalidTaskMessageException.class,
                () -> taskRegistrationService.register(request)
        );

        assertEquals(
                "eventId уже зарегистрирован с другими параметрами",
                exception.getMessage()
        );

        verify(taskRepository).insertIfAbsent(eventId, "my task", 42L);
        verify(taskRepository).findByEventId(eventId);
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldThrowWhenSameEventIdButDifferentDuration() {
        existingTask.setDuration(100L);

        when(taskRepository.insertIfAbsent(eventId, "my task", 42L))
                .thenReturn(0);

        when(taskRepository.findByEventId(eventId))
                .thenReturn(Optional.of(existingTask));

        InvalidTaskMessageException exception = assertThrows(
                InvalidTaskMessageException.class,
                () -> taskRegistrationService.register(request)
        );

        assertEquals(
                "eventId уже зарегистрирован с другими параметрами",
                exception.getMessage()
        );

        verify(taskRepository).insertIfAbsent(eventId, "my task", 42L);
        verify(taskRepository).findByEventId(eventId);
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldThrowWhenSameEventIdButDifferentNameAndDuration() {
        existingTask.setName("another task");
        existingTask.setDuration(999L);

        when(taskRepository.insertIfAbsent(eventId, "my task", 42L))
                .thenReturn(0);

        when(taskRepository.findByEventId(eventId))
                .thenReturn(Optional.of(existingTask));

        InvalidTaskMessageException exception = assertThrows(
                InvalidTaskMessageException.class,
                () -> taskRegistrationService.register(request)
        );

        assertEquals(
                "eventId уже зарегистрирован с другими параметрами",
                exception.getMessage()
        );

        verify(taskRepository).insertIfAbsent(eventId, "my task", 42L);
        verify(taskRepository).findByEventId(eventId);
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldThrowWhenInsertWasSkippedButExistingTaskNotFound() {
        when(taskRepository.insertIfAbsent(eventId, "my task", 42L))
                .thenReturn(0);

        when(taskRepository.findByEventId(eventId))
                .thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> taskRegistrationService.register(request)
        );

        assertEquals("Запись события не найдена", exception.getMessage());

        verify(taskRepository).insertIfAbsent(eventId, "my task", 42L);
        verify(taskRepository).findByEventId(eventId);
        verifyNoMoreInteractions(taskRepository);
    }
}
