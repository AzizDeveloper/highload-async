package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRequestDTO;
import dev.aziz.highloadasyncservice.exception.TaskPublishingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskPublisher {

    private final KafkaTemplate<String, TaskRequestDTO> kafkaTemplate;

    @Value("${kafka.topic:tasks}")
    private String topic;

    public String publish(TaskRequestDTO dto) {
        String eventId = dto.getEventId().toString();

        try {
            CompletableFuture<SendResult<String, TaskRequestDTO>> future =
                    kafkaTemplate.send(topic, eventId, dto);

            future.get(5, TimeUnit.SECONDS);

            return eventId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskPublishingException("Interrupted while publishing task", e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Failed to publish task {} to Kafka", eventId, e);
            throw new TaskPublishingException("Failed to publish task to Kafka", e);
        }
    }
}
