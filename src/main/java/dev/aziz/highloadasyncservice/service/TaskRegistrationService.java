package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRequestDTO;
import dev.aziz.highloadasyncservice.exception.InvalidTaskMessageException;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskRegistrationService {

    private final TaskRepository taskRepository;

    @Transactional
    public void register(TaskRequestDTO dto) {
        String normalizedName = dto.getName().strip();
        int inserted = taskRepository.insertIfAbsent(
                dto.getEventId(), normalizedName, dto.getDuration()
        );

        if (inserted == 0) {
            Task existing = taskRepository.findByEventId(dto.getEventId())
                    .orElseThrow(() -> new IllegalStateException("Запись события не найдена"));

            if (!existing.getName().equals(normalizedName)
                    || !existing.getDuration().equals(dto.getDuration())) {
                throw new InvalidTaskMessageException(
                        "eventId уже зарегистрирован с другими параметрами"
                );
            }
        }
    }

}
