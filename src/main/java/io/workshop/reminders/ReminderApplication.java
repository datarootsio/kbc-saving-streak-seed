package io.workshop.reminders;

import java.time.Clock;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ReminderApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReminderApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ApplicationRunner seedExamples(ReminderService reminders,
                                   @Value("${reminders.seed-data}") boolean seedData) {
        return args -> {
            if (seedData) reminders.seedExamples();
        };
    }
}
