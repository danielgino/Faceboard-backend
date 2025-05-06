package org.example.apimywebsite.util;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MemoryLogger {

    @Scheduled(fixedRate = 10000) // כל 10 שניות
    public void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();

        long totalMemory = runtime.totalMemory();     // מוקצה ל־JVM
        long freeMemory = runtime.freeMemory();       // מהפנוי
        long usedMemory = totalMemory - freeMemory;   // בשימוש בפועל
        long maxMemory = runtime.maxMemory();         // המקסימום האפשרי

        System.out.printf("🔍 RAM: used=%dMB, total=%dMB, max=%dMB, free=%dMB%n",
                usedMemory / (1024 * 1024),
                totalMemory / (1024 * 1024),
                maxMemory / (1024 * 1024),
                freeMemory / (1024 * 1024)
        );
    }
}
