package dev.aziz.highloadasyncservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableResilientMethods
@EnableScheduling
public class HighloadAsyncServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HighloadAsyncServiceApplication.class, args);
    }

}
