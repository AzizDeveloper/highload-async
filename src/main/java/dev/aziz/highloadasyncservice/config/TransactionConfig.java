package dev.aziz.highloadasyncservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// Конфигурация транзакций
@Configuration
public class TransactionConfig {
    // Создаем шаблон для программного управления транзакциями
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        // Устанавливаем уровень изоляции READ_COMMITTED
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // Требуем создания новой транзакции
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

}
