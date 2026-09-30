package dev.aziz.highloadasyncservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class WorkerConfig {

    @Value("${worker.pool.size}")
    private int poolSize;

    @Bean
    public ExecutorService taskExecutor() {
        return Executors.newFixedThreadPool(poolSize);
    }

}