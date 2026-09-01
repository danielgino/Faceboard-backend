package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.PasswordResetToken;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.PasswordResetTokenRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * L-DUP5 fix: PasswordResetService.requestReset previously called tokenRepo.save(prt) twice in a
 * row, re-setting the same three fields to (functionally) the same values in between - a
 * redundant persistence call with no required effect (nothing reads the token between the two
 * saves; a single @Transactional flush happens at commit regardless). The duplicate save + a
 * stray double semicolon were removed; this suite is the first dedicated coverage for
 * PasswordResetService (none existed before) and pins the token-save count explicitly so a future
 * regression re-introducing the duplicate would be caught.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private PasswordResetTokenRepository tokenRepo;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private MailService mail;

    @InjectMocks
    private PasswordResetService passwordResetService;

    // ---- requestReset ----

    @Test
    void requestReset_knownEmail_savesTokenExactlyOnce_andSendsMail() {
        User user = User.builder().id(1).email("alice@example.com").name("Alice").lastname("A").build();
        when(userRepo.findByEmail("alice@example.com")).thenReturn(user);

        passwordResetService.requestReset("alice@example.com");

        verify(tokenRepo).deleteByUserId(1);
        verify(tokenRepo, times(1)).save(any(PasswordResetToken.class));
        verify(mail).send(eq("alice@example.com"), anyString(), anyString());
    }

    @Test
    void requestReset_knownEmail_savedTokenHasConsistentFinalState() {
        User user = User.builder().id(1).email("alice@example.com").name("Alice").lastname("A").build();
        when(userRepo.findByEmail("alice@example.com")).thenReturn(user);

        passwordResetService.requestReset("alice@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepo, times(1)).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertEquals(user, saved.getUser());
        assertNotNull(saved.getTokenHash());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void requestReset_unknownEmail_noTokenOrUserMutation_noMailSent() {
        when(userRepo.findByEmail("nobody@example.com")).thenReturn(null);

        passwordResetService.requestReset("nobody@example.com");

        verifyNoInteractions(tokenRepo);
        verifyNoInteractions(mail);
        verify(userRepo, never()).save(any());
    }

    // ---- resetPassword: success ----

    @Test
    void resetPassword_validToken_claimsItAtomically_updatesPassword_deletesUserTokens() {
        User user = User.builder().id(1).email("alice@example.com").build();
        PasswordResetToken prt = PasswordResetToken.builder()
                .user(user)
                .tokenHash("irrelevant-in-this-mock")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(tokenRepo.findByTokenHashAndExpiresAtAfterAndUsedAtIsNull(anyString(), any()))
                .thenReturn(Optional.of(prt));
        when(tokenRepo.markUsedIfUnexpiredAndUnused(anyString(), any())).thenReturn(1);
        when(encoder.encode("NewPass1!")).thenReturn("encoded-hash");

        passwordResetService.resetPassword("raw-token", "NewPass1!");

        // The service hashes rawToken itself (SHA-256 of "raw-token"), so this can't assert a
        // literal value without duplicating that hashing here - anyString() mirrors how the
        // pre-existing findBy... stub above already handles this same constraint.
        verify(tokenRepo).markUsedIfUnexpiredAndUnused(anyString(), any());
        assertEquals("encoded-hash", user.getPassword());
        verify(userRepo).save(user);
        verify(tokenRepo).deleteByUserId(1);
    }

    // ---- COR-003: the atomic claim is what actually enforces single-use under a race. A plain
    // Mockito unit test cannot spin up two real concurrent transactions against MySQL row
    // locking (that would need a real-database integration test - noted as a limitation), but it
    // can and must prove the service correctly treats "someone else already claimed this token"
    // (claimed != 1) as a hard rejection: no password write, no token deletion. This is exactly
    // the outcome the loser of a real race must get once the fix is in place, so this pins the
    // service-level half of the invariant even without a live concurrency test. ----

    @Test
    void resetPassword_tokenAlreadyClaimedConcurrently_rejected_noPasswordWrite_noTokenDeletion() {
        User user = User.builder().id(1).email("alice@example.com").build();
        PasswordResetToken prt = PasswordResetToken.builder()
                .user(user)
                .tokenHash("irrelevant-in-this-mock")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(tokenRepo.findByTokenHashAndExpiresAtAfterAndUsedAtIsNull(anyString(), any()))
                .thenReturn(Optional.of(prt));
        // Simulates the loser of a race: another transaction's UPDATE already flipped usedAt
        // between this thread's SELECT and its own attempted claim.
        when(tokenRepo.markUsedIfUnexpiredAndUnused(anyString(), any())).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> passwordResetService.resetPassword("raw-token", "NewPass1!"));

        assertEquals(400, ex.getStatusCode().value());
        assertNull(user.getPassword());
        verify(userRepo, never()).save(any());
        verify(tokenRepo, never()).deleteByUserId(anyInt());
    }

    // ---- resetPassword: rejected requests retain existing behavior, no mutation ----

    @Test
    void resetPassword_weakPassword_rejectedBeforeAnyLookup_noRepositoryInteractions() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> passwordResetService.resetPassword("raw-token", "weak"));

        assertEquals(400, ex.getStatusCode().value());
        verifyNoInteractions(tokenRepo);
        verifyNoInteractions(userRepo);
    }

    @Test
    void resetPassword_tokenNotFound_invalidOrMalformed_rejected_noMutation() {
        when(tokenRepo.findByTokenHashAndExpiresAtAfterAndUsedAtIsNull(anyString(), any()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> passwordResetService.resetPassword("garbage-token", "NewPass1!"));

        assertEquals(400, ex.getStatusCode().value());
        verify(userRepo, never()).save(any());
        verify(tokenRepo, never()).save(any());
        verify(tokenRepo, never()).deleteByUserId(anyInt());
    }

    @Test
    void resetPassword_tokenExpired_repositoryQueryExcludesIt_treatedAsNotFound_rejected_noMutation() {
        // findByTokenHashAndExpiresAtAfterAndUsedAtIsNull's own JPQL (expiresAt > now) is what
        // excludes an expired token at the database level - from the service's perspective this
        // is indistinguishable from "not found", which is exactly the existing (correct)
        // behavior being pinned here: no separate "expired" branch exists or should be added.
        when(tokenRepo.findByTokenHashAndExpiresAtAfterAndUsedAtIsNull(anyString(), any()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> passwordResetService.resetPassword("expired-token", "NewPass1!"));

        assertEquals(400, ex.getStatusCode().value());
        verify(userRepo, never()).save(any());
        verify(tokenRepo, never()).save(any());
    }

    @Test
    void resetPassword_tokenAlreadyUsed_repositoryQueryExcludesIt_treatedAsNotFound_rejected_noMutation() {
        // Same reasoning as the expired case: usedAtIsNull in the query excludes already-used
        // tokens at the database level; the service sees "not found" either way.
        when(tokenRepo.findByTokenHashAndExpiresAtAfterAndUsedAtIsNull(anyString(), any()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> passwordResetService.resetPassword("already-used-token", "NewPass1!"));

        assertEquals(400, ex.getStatusCode().value());
        verify(userRepo, never()).save(any());
        verify(tokenRepo, never()).save(any());
    }
}
