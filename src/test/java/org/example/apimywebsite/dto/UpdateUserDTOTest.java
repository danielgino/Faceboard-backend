package org.example.apimywebsite.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F2 fix: facebookUrl/instagramUrl must require an explicit https:// scheme
 * and the exact facebook.com/instagram.com host. Exercises the @Pattern
 * constraints directly via the Bean Validation API (no Spring context
 * needed), the same mechanism the @Valid on UserController.updateProfile
 * uses at runtime.
 */
class UpdateUserDTOTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<String> violationMessagesFor(String field, String value) {
        UpdateUserDTO dto = new UpdateUserDTO();
        if (field.equals("facebookUrl")) {
            dto.setFacebookUrl(value);
        } else {
            dto.setInstagramUrl(value);
        }
        Set<ConstraintViolation<UpdateUserDTO>> violations = validator.validate(dto);
        return violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals(field))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    // ---- 1. valid https:// URLs are accepted ----

    @ParameterizedTest
    @ValueSource(strings = {
            "https://facebook.com/john",
            "https://www.facebook.com/john.doe.123",
    })
    void facebookUrl_validHttpsUrl_isAccepted(String value) {
        assertTrue(violationMessagesFor("facebookUrl", value).isEmpty(), "expected no violations for: " + value);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://instagram.com/john",
            "https://www.instagram.com/john.doe.123",
    })
    void instagramUrl_validHttpsUrl_isAccepted(String value) {
        assertTrue(violationMessagesFor("instagramUrl", value).isEmpty(), "expected no violations for: " + value);
    }

    // ---- 2. empty values remain accepted ----

    @ParameterizedTest
    @NullAndEmptySource
    void facebookUrl_emptyOrNull_isAccepted(String value) {
        assertTrue(violationMessagesFor("facebookUrl", value).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void instagramUrl_emptyOrNull_isAccepted(String value) {
        assertTrue(violationMessagesFor("instagramUrl", value).isEmpty());
    }

    // ---- 3. scheme-less URLs are rejected ----

    @ParameterizedTest
    @ValueSource(strings = {
            "facebook.com/john",
            "www.facebook.com/john",
    })
    void facebookUrl_schemeLess_isRejected(String value) {
        assertFacebookRejected(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "instagram.com/john",
            "www.instagram.com/john",
    })
    void instagramUrl_schemeLess_isRejected(String value) {
        assertInstagramRejected(value);
    }

    // ---- 4. http:// URLs are rejected (HTTPS-only policy) ----

    @Test
    void facebookUrl_plainHttp_isRejected() {
        assertFacebookRejected("http://facebook.com/john");
    }

    @Test
    void instagramUrl_plainHttp_isRejected() {
        assertInstagramRejected("http://instagram.com/john");
    }

    // ---- 5. javascript:, data:, protocol-relative, malformed, deceptive-domain remain rejected ----

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "javascript://facebook.com/%0aalert(1)",
            "data:text/html,<script>alert(1)</script>",
            "//facebook.com/john",
            "https://facebook.com.evil.com/john",
            "https://evil.com/facebook.com/john",
            " https://facebook.com/john",
            "https://facebook.com/john ",
    })
    void facebookUrl_unsafeOrMalformedVariants_areRejected(String value) {
        assertFacebookRejected(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "//instagram.com/john",
            "https://instagram.com.evil.com/john",
            "https://evil.com/instagram.com/john",
    })
    void instagramUrl_unsafeOrMalformedVariants_areRejected(String value) {
        assertInstagramRejected(value);
    }

    // ---- 8. validation-error message shape is unchanged from the pre-fix behavior ----

    @Test
    void facebookUrl_violationMessage_unchanged() {
        Set<String> messages = violationMessagesFor("facebookUrl", "not-a-url");
        assertEquals(Set.of("Facebook URL must be a valid facebook.com link"), messages);
    }

    @Test
    void instagramUrl_violationMessage_unchanged() {
        Set<String> messages = violationMessagesFor("instagramUrl", "not-a-url");
        assertEquals(Set.of("Instagram URL must be a valid instagram.com link"), messages);
    }

    private void assertFacebookRejected(String value) {
        assertEquals(
                Set.of("Facebook URL must be a valid facebook.com link"),
                violationMessagesFor("facebookUrl", value),
                "expected a violation for: " + value
        );
    }

    private void assertInstagramRejected(String value) {
        assertEquals(
                Set.of("Instagram URL must be a valid instagram.com link"),
                violationMessagesFor("instagramUrl", value),
                "expected a violation for: " + value
        );
    }
}
