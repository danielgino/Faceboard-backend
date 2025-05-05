package org.example.apimywebsite.util;

import org.example.apimywebsite.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StartupRunner {

    @Bean
    public CommandLineRunner runner(UserRepository userRepository) {
        return args -> {
            try {
                long count = userRepository.count();
                System.out.println("✅ Users count in DB: " + count);
            } catch (Exception e) {
                System.out.println("❌ Failed to query users: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}
