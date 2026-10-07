package dev.aziz.highloadasyncservice.config;

import dev.aziz.highloadasyncservice.dto.TaskRequestDTO;
import dev.aziz.highloadasyncservice.exception.InvalidTaskMessageException;
import jakarta.validation.ConstraintViolationException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:task-group}")
    private String groupId;

    @Value("${kafka.listener.concurrency:1}")
    private int concurrency;

    @Bean
    public ConsumerFactory<String, TaskRequestDTO> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<TaskRequestDTO> json =
                new JsonDeserializer<>(TaskRequestDTO.class, false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(json)
        );
    }

    @Bean
    public ProducerFactory<String, Object> dltProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Для битого JSON восстанавливается исходный byte[],
        // а для ошибки валидации отправляется десериализованный DTO
        Map<Class<?>, Serializer<?>> serializers = new HashMap<>();
        serializers.put(byte[].class, new ByteArraySerializer());
        serializers.put(TaskRequestDTO.class, new JsonSerializer<>());

        return new DefaultKafkaProducerFactory<>(
                props,
                new StringSerializer(),
                new DelegatingByTypeSerializer(serializers)
        );
    }

    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate(
            ProducerFactory<String, Object> dltProducerFactory
    ) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> dltKafkaTemplate
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        dltKafkaTemplate,
                        (record, exception) -> new TopicPartition(
                                record.topic() + ".DLT", record.partition()
                        )
                );
        recoverer.setFailIfSendResultIsError(true);

        // Всего три попытки доставки: исходная и два повтора
        DefaultErrorHandler handler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.addNotRetryableExceptions(
                ConstraintViolationException.class,
                InvalidTaskMessageException.class
        );
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TaskRequestDTO> kafkaListenerContainerFactory(
            ConsumerFactory<String, TaskRequestDTO> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("Kafka concurrency должна быть положительной");
        }

        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, TaskRequestDTO>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ProducerFactory<String, TaskRequestDTO> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

        JsonSerializer<TaskRequestDTO> jsonSerializer = new JsonSerializer<>();
        jsonSerializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(
                props,
                new StringSerializer(),
                jsonSerializer
        );
    }

    @Bean
    public KafkaTemplate<String, TaskRequestDTO> kafkaTemplate(ProducerFactory<String, TaskRequestDTO> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

}

