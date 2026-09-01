package org.example.apimywebsite.util;

import org.example.apimywebsite.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StartupRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    @Bean
    public CommandLineRunner runner(UserRepository userRepository) {
        return args -> {
            try {
                long count = userRepository.count();
                log.info("Users count in DB: {}", count);
            } catch (Exception e) {
                log.error("Failed to query users on startup", e);
            }
        };
    }
}
