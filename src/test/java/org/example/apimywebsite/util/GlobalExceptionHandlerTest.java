package org.example.apimywebsite.util;

import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.CommentRepository;
import org.example.apimywebsite.repository.FriendshipRepository;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.CommentService;
import org.example.apimywebsite.service.FriendshipService;
import org.example.apimywebsite.service.NotificationService;
import org.example.apimywebsite.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for GlobalExceptionHandler (H8a fix): ResponseStatusException must preserve its
 * own intended HTTP status and a safe, developer-supplied reason — not be flattened to a
 * blanket 400 by the generic RuntimeException handler. Plain RuntimeException handling
 * (H8 fix): every current business-logic exception already throws a typed
 * ResponseStatusException, so anything still reaching the generic RuntimeException handler
 * is unexpected — it must return a fixed, safe 500, never the raw exception message.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- Core status-preservation scenarios ----

    @Test
    void responseStatusException_badRequest_remains400() {
        ResponseEntity<?> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required"));

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void responseStatusException_unauthorized_returns401() {
        ResponseEntity<?> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void responseStatusException_forbidden_returns403() {
        ResponseEntity<?> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to delete this comment"));

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void responseStatusException_conflict_returns409() {
        // H8b: FriendshipService's "already processed"/"non-pending" cases now use CONFLICT.
        ResponseEntity<?> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.CONFLICT, "Friend request not found or already processed."));

        assertEquals(409, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Friend request not found or already processed.", body.get("message"));
    }

    @Test
    void responseStatusException_internalServerErrorWithCause_returns500_andNeverLeaksTheCause() {
        // H8b: StoryService's Cloudinary-upload-failure case now uses a ResponseStatusException
        // carrying the original IOException as cause, purely for server-side diagnostics.
        // handleResponseStatusException must only ever read getReason(), never the cause.
        Throwable sensitiveCause = new java.io.IOException("Cloudinary API error: invalid api_key ck_live_9f2a...");
        ResponseEntity<?> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload to Cloudinary failed", sensitiveCause));

        assertEquals(500, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(1, body.size());
        assertEquals("Upload to Cloudinary failed", body.get("message"));
    }

    @Test
    void responseStatusException_notFound_returns404() {
        ResponseEntity<?> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No friendship found between users"));

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void responseStatusException_retainsExisting_messageOnly_shape() {
        ResponseEntity<?> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(1, body.size());
        assertEquals("User not found", body.get("message"));
    }

    // ---- No-reason / blank-reason: safe fallback, no internal detail leakage ----

    @Test
    void responseStatusException_withNoReason_usesGenericSafeFallback_notSpringsOwnFormatting() {
        ResponseEntity<?> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR));

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        String message = (String) body.get("message");
        assertNotNull(message);
        // ResponseStatusException.getMessage() would have returned "500 INTERNAL_SERVER_ERROR"
        // (Spring's own status-code formatting) — must not leak that through instead.
        assertFalse(message.contains("INTERNAL_SERVER_ERROR"));
        assertFalse(message.toLowerCase().contains("exception"));
        assertEquals("Request could not be completed.", message);
    }

    @Test
    void responseStatusException_withBlankReason_usesGenericSafeFallback() {
        ResponseEntity<?> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "   "));

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Request could not be completed.", body.get("message"));
    }

    // ---- Corrected generic RuntimeException handling (H8 fix): 500, fixed safe message,
    // never echoes ex.getMessage() ----

    @Test
    void plainRuntimeException_returns500_withFixedSafeMessage_neverRawMessage() {
        ResponseEntity<?> response = handler.handleRuntimeException(
                new RuntimeException("NullPointerException at PostService.java:142: user.getEmail() was null"));

        assertEquals(500, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(1, body.size());
        assertEquals("An unexpected error occurred.", body.get("message"));
        assertFalse(((String) body.get("message")).contains("PostService"), "must not leak the raw exception message");
    }

    @Test
    void plainIllegalArgumentException_stillHandledByGenericHandler_returns500_withFixedSafeMessage() {
        // IllegalArgumentException is a RuntimeException subtype with no dedicated handler, so
        // it must still fall through to handleRuntimeException, now with the corrected
        // 500/fixed-safe-message behavior instead of echoing its raw message.
        ResponseEntity<?> response = handler.handleRuntimeException(
                new IllegalArgumentException("some unexpected illegal argument"));

        assertEquals(500, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("An unexpected error occurred.", body.get("message"));
    }

    // ---- Real-service integration: exact production exception instances ----

    @Test
    void friendshipService_noRelationship_producesExceptionHandledAs404() {
        FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserMapper userMapper = mock(UserMapper.class);
        FriendshipService friendshipService = new FriendshipService(friendshipRepository, notificationService, userRepository, userMapper, -1);

        User userA = User.builder().id(1).build();
        User userB = User.builder().id(2).build();
        when(friendshipRepository.findByUserAndFriend(userA, userB)).thenReturn(Optional.empty());
        when(friendshipRepository.findByUserAndFriend(userB, userA)).thenReturn(Optional.empty());

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> friendshipService.getRelationshipBetweenUsers(userA, userB));

        ResponseEntity<?> response = handler.handleResponseStatusException(thrown);

        assertEquals(404, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("No friendship found between users", body.get("message"));
    }

    @Test
    void commentService_forbiddenDelete_producesExceptionHandledAs403() {
        CommentRepository commentRepository = mock(CommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PostRepository postRepository = mock(PostRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuthHelper authHelper = new AuthHelper(userRepository);
        CommentService commentService = new CommentService(commentRepository, authHelper, postRepository, notificationService);

        User currentUser = User.builder().id(1).userName("attacker").build();
        User commentOwner = User.builder().id(2).build();
        User postOwner = User.builder().id(3).build();
        Post post = new Post();
        post.setUser(postOwner);
        Comment comment = new Comment();
        comment.setUser(commentOwner);
        comment.setPost(post);

        when(userRepository.findByUserName("attacker")).thenReturn(currentUser);
        when(commentRepository.findById(99L)).thenReturn(Optional.of(comment));

        Authentication auth = new UsernamePasswordAuthenticationToken("attacker", null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> commentService.deleteComment(99L));

        ResponseEntity<?> response = handler.handleResponseStatusException(thrown);

        assertEquals(403, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("You are not allowed to delete this comment", body.get("message"));
    }

    // ---- DataIntegrityViolationException handling (H8c fix): a DB-level uniqueness race
    // (e.g. two concurrent registrations for the same email) must never leak the raw
    // Hibernate/JDBC message - only a fixed, safe message reaches the client. ----

    @Test
    void dataIntegrityViolationException_returns400() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry 'attacker@example.com' for key " +
                        "'users.UK6dotkott2kjsp8vw4d0m25fb7'] [insert into users (email,user_name) values (?,?)]");

        ResponseEntity<?> response = handler.handleDataIntegrityViolationException(ex);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void dataIntegrityViolationException_returnsExactFixedSafeMessageShape() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry 'someone@example.com' for key 'users.email']");

        ResponseEntity<?> response = handler.handleDataIntegrityViolationException(ex);

        assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(1, body.size());
        assertEquals("A conflicting value already exists.", body.get("message"));
    }

    @Test
    void dataIntegrityViolationException_doesNotLeakRawDetailsOrCause() {
        String duplicateValue = "attacker@example.com";
        String constraintName = "users.UK6dotkott2kjsp8vw4d0m25fb7";
        String rawHibernateMessage = "could not execute statement [Duplicate entry '" + duplicateValue
                + "' for key '" + constraintName + "'] [insert into users (email, user_name) values (?, ?)]";
        Throwable jdbcCause = new SQLIntegrityConstraintViolationException(
                "Duplicate entry '" + duplicateValue + "' for key '" + constraintName + "'");
        DataIntegrityViolationException ex = new DataIntegrityViolationException(rawHibernateMessage, jdbcCause);

        ResponseEntity<?> response = handler.handleDataIntegrityViolationException(ex);

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        String message = (String) body.get("message");

        assertEquals("A conflicting value already exists.", message);
        assertFalse(message.contains(duplicateValue), "must not leak the duplicate value");
        assertFalse(message.contains(constraintName), "must not leak the constraint/index name");
        assertFalse(message.contains("users"), "must not leak the table name");
        assertFalse(message.toLowerCase().contains("insert into"), "must not leak SQL text");
        assertFalse(message.toLowerCase().contains("sqlintegrityconstraint"), "must not leak the cause's class name");
        assertFalse(message.toLowerCase().contains("hibernate"), "must not leak implementation details");
        assertFalse(message.contains(rawHibernateMessage), "must not contain the raw Hibernate message anywhere");
    }

    // ---- Existing validation / malformed-request handlers: confirmed unchanged by this task ----

    @Test
    void validationException_behaviorUnchanged_fieldErrorMap() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "updateUserDTO");
        bindingResult.addError(new FieldError(
                "updateUserDTO", "facebookUrl", "Facebook URL must be a valid facebook.com link"));
        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("validationException_behaviorUnchanged_fieldErrorMap"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Facebook URL must be a valid facebook.com link", response.getBody().get("facebookUrl"));
    }

    @Test
    void malformedRequest_behaviorUnchanged_genericSafeMessage() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error: raw internal detail that must not leak");

        ResponseEntity<Map<String, String>> response = handler.handleParsingError(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Invalid request format", response.getBody().get("message"));
    }

    // ---- Upload-size-limit finding: MaxUploadSizeExceededException handling ----

    @Test
    void maxUploadSizeExceeded_returns413_withExactFixedSafeMessageShape() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(5 * 1024 * 1024);

        ResponseEntity<?> response = handler.handleMaxUploadSizeExceededException(ex);

        assertEquals(413, response.getStatusCode().value());
        assertInstanceOf(Map.class, response.getBody());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(1, body.size());
        assertEquals("Uploaded file is too large.", body.get("message"));
    }

    @Test
    void maxUploadSizeExceeded_doesNotLeakConfiguredLimitOrParserDetails() {
        // Realistic shape: MaxUploadSizeExceededException's own getMessage() embeds the
        // configured byte limit, and its cause is typically the underlying multipart
        // parser's own exception (e.g. commons-fileupload's FileSizeLimitExceededException).
        Throwable parserCause = new IllegalStateException(
                "The field file exceeds its maximum permitted size of 5242880 bytes.");
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(5 * 1024 * 1024, parserCause);

        ResponseEntity<?> response = handler.handleMaxUploadSizeExceededException(ex);

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        String message = (String) body.get("message");

        assertEquals("Uploaded file is too large.", message);
        assertFalse(message.contains("5242880"), "must not leak the configured byte limit");
        assertFalse(message.toLowerCase().contains("bytes"), "must not leak size/parser phrasing");
        assertFalse(message.toLowerCase().contains("field"), "must not leak multipart field details");
        assertFalse(message.toLowerCase().contains("filesizelimitexceeded"), "must not leak the parser's exception class name");
        assertFalse(message.contains(ex.getMessage()), "must not contain the raw exception message");
    }
}
