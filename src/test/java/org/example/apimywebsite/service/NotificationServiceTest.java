package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Notification;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.NotificationDTO;
import org.example.apimywebsite.repository.NotificationRepository;
import org.example.apimywebsite.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L-DB4B: NotificationService.getMyNotifications was previously unbounded
 * (findByReceiverIdOrderByCreatedAtDesc with no Pageable). It now normalizes page/size and
 * bounds the query at the database level via Pageable, mirroring M-DB2's conversation
 * pagination. Mock-level proof only - real ORDER BY/LIMIT execution against a live database is
 * not exercised here (no DB-backed test environment in this project).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private AuthHelper authHelper;

    @InjectMocks
    private NotificationService notificationService;

    private static final int USER_ID = 1;

    private Notification aNotification(long id) {
        Notification n = new Notification();
        n.setId(id);
        n.setReceiver(User.builder().id(USER_ID).build());
        n.setType("LIKE");
        n.setContent("someone liked your post");
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    @Test
    void getMyNotifications_noParams_defaultsToPageZeroAndDefaultSize() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(USER_ID).build());
        when(notificationRepository.findByReceiverIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        notificationService.getMyNotifications(null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByReceiverIdOrderByCreatedAtDesc(eq(USER_ID), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(NotificationService.DEFAULT_NOTIFICATIONS_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getMyNotifications_oversizedSize_clampedToMax() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(USER_ID).build());
        when(notificationRepository.findByReceiverIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        notificationService.getMyNotifications(0, Integer.MAX_VALUE);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByReceiverIdOrderByCreatedAtDesc(eq(USER_ID), captor.capture());
        assertEquals(NotificationService.MAX_NOTIFICATIONS_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getMyNotifications_negativePageAndSize_normalizeToDefaults() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(USER_ID).build());
        when(notificationRepository.findByReceiverIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        notificationService.getMyNotifications(-5, -10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByReceiverIdOrderByCreatedAtDesc(eq(USER_ID), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(NotificationService.DEFAULT_NOTIFICATIONS_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getMyNotifications_queriesOnlyForAuthenticatedCurrentUser_returnsFlatListInRepositoryOrder() {
        User currentUser = User.builder().id(USER_ID).build();
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        Notification newest = aNotification(2L);
        Notification older = aNotification(1L);
        when(notificationRepository.findByReceiverIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(newest, older));

        List<NotificationDTO> result = notificationService.getMyNotifications(2, 30);

        verify(notificationRepository).findByReceiverIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class));
        assertEquals(List.of(2L, 1L), result.stream().map(NotificationDTO::getId).toList());
    }

    // ---- COR-002 fix: createNotification's WebSocket broadcast must not fire until the
    // notification row's transaction has actually committed - otherwise a receiver could get a
    // real-time push for a notification that a later failure in the same transaction rolls back
    // and that therefore never really exists. ----

    @Test
    void createNotification_savesRow_andBroadcastsImmediately_whenNoTransactionIsActive() {
        // Mirrors every other test in this class: no real Spring transaction manager is present,
        // so TransactionUtils' immediate fallback runs - same behavior as before this fix for any
        // caller invoked outside a transaction.
        User receiver = User.builder().id(USER_ID).build();
        User sender = User.builder().id(2).build();
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.createNotification(receiver, sender, "LIKE", "liked your post", null);

        verify(notificationRepository).save(any());
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + USER_ID), any(NotificationDTO.class));
    }

    @Test
    void createNotification_underActiveTransaction_defersBroadcastUntilAfterCommit() {
        User receiver = User.builder().id(USER_ID).build();
        User sender = User.builder().id(2).build();
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.createNotification(receiver, sender, "LIKE", "liked your post", null);

            verify(notificationRepository).save(any());
            verify(messagingTemplate, org.mockito.Mockito.never()).convertAndSend(anyString(), any(Object.class));

            List<org.springframework.transaction.support.TransactionSynchronization> syncs =
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, syncs.size());

            syncs.get(0).afterCommit();

            verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + USER_ID), any(NotificationDTO.class));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---- COR-009 fix: retention cleanup now calls a bulk @Modifying delete (returning the
    // affected-row count) instead of a derived deleteBy method that Spring Data JPA would
    // execute as a per-entity load-then-remove. ----

    @Test
    void deleteOldNotifications_deletesRowsOlderThanThirtyDays() {
        when(notificationRepository.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(4);

        notificationService.deleteOldNotifications();

        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationRepository).deleteByCreatedAtBefore(thresholdCaptor.capture());
        LocalDateTime expectedThreshold = LocalDateTime.now().minusDays(30);
        // Allow a small tolerance for the wall-clock time elapsed between the two LocalDateTime.now() calls.
        assertTrue(java.time.Duration.between(thresholdCaptor.getValue(), expectedThreshold).abs().getSeconds() < 5);
    }
}
