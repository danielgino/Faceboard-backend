package org.example.apimywebsite;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.example.apimywebsite")
@EnableScheduling

public class ApiMyWebsiteApplication {

    public static void main(String[] args) {
//        System.out.println("🚀 Starting ApiMyWebsiteApplication...");
//        System.out.println("🔍 DB_HOST = " + System.getenv("DB_HOST"));
//        System.out.println("🔍 DB_PORT = " + System.getenv("DB_PORT"));
//        System.out.println("🔍 DB_NAME = " + System.getenv("DB_NAME"));
//        System.out.println("🔍 DB_USERNAME = " + System.getenv("DB_USERNAME"));
//        System.out.println("🔍 DB_PASSWORD = " + System.getenv("DB_PASSWORD"));
//        System.out.println("🔍 CLOUDINARY_NAME = " + System.getenv("CLOUDINARY_NAME"));
//        System.out.println("🔍 CLOUDINARY_API_KEY = " + System.getenv("CLOUDINARY_API_KEY"));
//        System.out.println("🔍 CLOUDINARY_API_SECRET present = " + (System.getenv("CLOUDINARY_API_SECRET") != null));

//LOCALHOST
//        Dotenv dotenv = Dotenv.load();
//        System.setProperty("DB_URL", dotenv.get("DB_URL"));
//        System.setProperty("DB_USERNAME", dotenv.get("DB_USERNAME"));
//        System.setProperty("DB_PASSWORD", dotenv.get("DB_PASSWORD"));
//        System.setProperty("CLOUDINARY_NAME", dotenv.get("CLOUDINARY_NAME"));
//        System.setProperty("CLOUDINARY_API_KEY", dotenv.get("CLOUDINARY_API_KEY"));
//        System.setProperty("CLOUDINARY_API_SECRET", dotenv.get("CLOUDINARY_API_SECRET"));
//        System.setProperty("JWT_SECRET", dotenv.get("JWT_SECRET"));

        SpringApplication.run(ApiMyWebsiteApplication.class, args);
        System.out.println("✅ Application started!"); // האם אתה רואה את זה?

    }

}
