package org.example.apimywebsite.configuration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Upload-size-limit finding: pins down the exact configured multipart limits in
 * application.properties, which back every upload endpoint's server-side size enforcement -
 * Spring's DispatcherServlet/StandardServletMultipartResolver honors these before any
 * controller, service, or Cloudinary code runs, for all three upload endpoints
 * (UserController.uploadProfilePicture, StoryController.uploadStory, PostController.addPost).
 *
 * A live MockMvc-based test cannot exercise the real container-level enforcement of these
 * values (see UserControllerUploadSizeLimitTest's Javadoc for why), and a full embedded-server
 * test would require the application's real datasource beans (unavailable DB_URL in this
 * sandbox). This plain, Spring-free test instead verifies our own properties file directly, so
 * a future accidental change/removal of these lines is caught without needing a live server.
 */
class MultipartSizeLimitPropertiesTest {

    @Test
    void applicationProperties_configuresExpectedMultipartSizeLimits() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(in, "application.properties must be on the test classpath");
            properties.load(in);
        }

        assertEquals("true", properties.getProperty("spring.servlet.multipart.enabled"));
        assertEquals("5MB", properties.getProperty("spring.servlet.multipart.max-file-size"));
        assertEquals("10MB", properties.getProperty("spring.servlet.multipart.max-request-size"));
    }
}
