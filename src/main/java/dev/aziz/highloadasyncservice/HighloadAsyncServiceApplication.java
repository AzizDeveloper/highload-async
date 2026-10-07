package dev.aziz.highloadasyncservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
public class HighloadAsyncServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HighloadAsyncServiceApplication.class, args);
    }

}
