package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// SEC-005 fix: loginByEmail must mint the token with the user's CURRENT password hash (not just
// the username), so JwtUtil can embed the fingerprint that later invalidates the token once the
// password changes/resets.
//
// UserService mixes constructor injection (userRepository, cloudinaryService) with @Autowired
// field injection (jwtUtil, passwordEncoder, ...) - @InjectMocks only performs one or the other
// depending on which constructor Mockito selects, not both, so the field-injected collaborators
// are wired manually via ReflectionTestUtils here (same pattern already used in
// JwtAuthFilterTest for the same reason).
@ExtendWith(MockitoExtension.class)
class UserServiceLoginTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    private static final String CURRENT_HASH = "$2a$10$currentHashValue0000000000000000000000";

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, cloudinaryService);
        ReflectionTestUtils.setField(userService, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(userService, "passwordEncoder", passwordEncoder);
    }

    @Test
    void loginByEmail_validCredentials_generatesTokenWithCurrentPasswordHash() {
        User user = User.builder().id(1).userName("alice").email("alice@example.com").password(CURRENT_HASH).build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(user);
        when(passwordEncoder.matches("correct-password", CURRENT_HASH)).thenReturn(true);
        when(jwtUtil.generateToken("alice", CURRENT_HASH)).thenReturn("signed-jwt");

        String token = userService.loginByEmail("alice@example.com", "correct-password");

        assertEquals("signed-jwt", token);
        verify(jwtUtil).generateToken("alice", CURRENT_HASH);
    }

    @Test
    void loginByEmail_wrongPassword_neverGeneratesToken() {
        User user = User.builder().id(1).userName("alice").email("alice@example.com").password(CURRENT_HASH).build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(user);
        when(passwordEncoder.matches("wrong-password", CURRENT_HASH)).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> userService.loginByEmail("alice@example.com", "wrong-password"));

        verify(jwtUtil, org.mockito.Mockito.never()).generateToken(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
