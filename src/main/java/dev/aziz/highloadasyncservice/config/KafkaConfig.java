package dev.aziz.highloadasyncservice.config;

import dev.aziz.highloadasyncservice.dto.ProcessTaskCommand;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaTemplate<String, ProcessTaskCommand> kafkaTemplate(
            ProducerFactory<String, ProcessTaskCommand> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ProducerFactory<String, ProcessTaskCommand> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProcessTaskCommand> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProcessTaskCommand> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        return factory;
    }

    @Bean
    public ConsumerFactory<String, ProcessTaskCommand> consumerFactory() {
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
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//  тут определяем как десерализировать будем сообщение, добавляем десериализатор JsonDeserializer
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
//  AUTO_OFFSET_RESET_CONFIG -> нужен чтобы определить с какой позиции начинать чтение
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "dev.aziz.highloadasyncservice.dto");
        // Fallback type in case the producer doesn't send the __TypeId__ header
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, ProcessTaskCommand.class.getName());

        // Создаем и возвращаем фабрику потребителей с указанными настройками
        return new DefaultKafkaConsumerFactory<>(props);
    }

}

