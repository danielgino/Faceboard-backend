package org.example.apimywebsite;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.example.apimywebsite")
@EnableScheduling

public class ApiMyWebsiteApplication {

    public static void main(String[] args) {

        // Loads a local .env file when present (development); does not fail when it's
        // absent (e.g. Render, where these are supplied as real platform environment
        // variables instead — Dotenv.get() already checks System.getenv() first).
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        setPropertyIfPresent(dotenv, "DB_URL");
        setPropertyIfPresent(dotenv, "DB_USERNAME");
        setPropertyIfPresent(dotenv, "DB_PASSWORD");
        setPropertyIfPresent(dotenv, "CLOUDINARY_NAME");
        setPropertyIfPresent(dotenv, "CLOUDINARY_API_KEY");
        setPropertyIfPresent(dotenv, "CLOUDINARY_API_SECRET");
        setPropertyIfPresent(dotenv, "JWT_SECRET");
        setPropertyIfPresent(dotenv, "SMTP_EMAIL");
        setPropertyIfPresent(dotenv, "SMTP_PASSWORD");


        SpringApplication.run(ApiMyWebsiteApplication.class, args);
        System.out.println("Application started!✅");

    }

    // System.setProperty(key, value) throws NullPointerException on a null value, which
    // would otherwise crash startup here (before Spring even runs) for any key missing
    // from both the .env file and the real environment. Skipping absent keys lets Spring's
    // own Environment resolution fall through to a genuine OS environment variable if one
    // exists, or — for JWT_SECRET specifically — lets JwtUtil's own fail-fast check produce
    // a clear, intentional startup failure instead of this unrelated NPE.
    private static void setPropertyIfPresent(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        if (value != null) {
            System.setProperty(key, value);
        }
    }

}
