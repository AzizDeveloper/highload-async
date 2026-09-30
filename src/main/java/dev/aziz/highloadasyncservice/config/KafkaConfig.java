package dev.aziz.highloadasyncservice.config;

import dev.aziz.highloadasyncservice.dto.TaskRequestDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

// Указывает, что это конфигурационный класс Spring
@Configuration
public class KafkaConfig {

    // Инжектирует значение из properties-файла для адреса Kafka серверов
    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    // Создает бин фабрики потребителей Kafka
    @Bean
    public ConsumerFactory<String, TaskRequestDTO> consumerFactory() {
        // Создаем Map для хранения настроек Kafka
        Map<String, Object> props = new HashMap<>();

        // Указываем адреса брокеров Kafka
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Задаем идентификатор группы потребителей
        // Все потребители с одинаковым group.id будут работать как одна группа
        // GROUP_ID_CONFIG - идентификатор группы потребителей для балансировки нагрузки
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "task-group");

        // Здесь могут быть дополнительные настройки, например:
//  тут определяем как десерализировать будем ключ, добавляем десериализатор StringDeserializer
//         props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//  тут определяем как десерализировать будем сообщение, добавляем десериализатор JsonDeserializer
//         props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
//  AUTO_OFFSET_RESET_CONFIG -> нужен чтобы определить с какой позиции начинать чтение
//         props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Создаем и возвращаем фабрику потребителей с указанными настройками
        return new DefaultKafkaConsumerFactory<>(props);
    }
}

