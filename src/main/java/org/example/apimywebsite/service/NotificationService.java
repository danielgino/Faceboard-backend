package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Notification;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.NotificationDTO;
import org.example.apimywebsite.repository.NotificationRepository;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.TransactionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final AuthHelper authHelper;
    private final SimpMessagingTemplate messagingTemplate;
    public NotificationService(NotificationRepository notificationRepository,SimpMessagingTemplate messagingTemplate, AuthHelper authHelper) {
        this.notificationRepository = notificationRepository;
        this.authHelper = authHelper;
        this.messagingTemplate=messagingTemplate;
    }


    // COR-002 fix: this DB save runs inside whatever @Transactional method called it (friend
    // request/accept, comment, like, ...). The WebSocket broadcast used to fire immediately,
    // mid-transaction - if a later step in that same caller's transaction failed and rolled back,
    // the client would already have received a real-time notification for a row that was never
    // actually persisted. Deferring the broadcast to afterCommit means it only ever fires once
    // the notification row has durably committed; callers invoked with no active transaction
    // (none currently) still broadcast immediately via TransactionUtils' fallback.
    public Notification createNotification(User receiver, User sender, String type, String content, Post post) {
        Notification notification = new Notification(receiver, sender, type, content, post);
        Notification saved = notificationRepository.save(notification);
        NotificationDTO dto = mapToDTO(saved);
        TransactionUtils.runAfterCommit(() -> {
            log.debug("Dispatching notification to receiver {}", receiver.getId());
            messagingTemplate.convertAndSend("/topic/notifications/" + receiver.getId(), dto);
        });

        return saved;
    }

    // L-DB4B: bounded, page-based read instead of the previous unbounded fetch. Defaults/max are
    // normalized here (not trusted from the caller), matching the pattern already established
    // for M-DB2's conversation pagination. Older notifications remain retrievable via page=1,
    // page=2, etc.; this does not replace the existing 30-day scheduled cleanup, which is a
    // separate retention concern and is left untouched.
    public static final int DEFAULT_NOTIFICATIONS_PAGE_SIZE = 50;
    public static final int MAX_NOTIFICATIONS_PAGE_SIZE = 100;

    // M-OOP1: HttpServletRequest removed from all four methods below - it was threaded through
    // from the controller but never actually read; the acting user was already resolved via
    // AuthHelper (unchanged), making the parameter pure dead transport coupling.
    public List<NotificationDTO> getMyNotifications(Integer page, Integer size) {
        User user = authHelper.getCurrentUser();
        int safePage = (page == null || page < 0) ? 0 : page;
        int safeSize = (size == null || size <= 0)
                ? DEFAULT_NOTIFICATIONS_PAGE_SIZE
                : Math.min(size, MAX_NOTIFICATIONS_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        List<Notification> notifications = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(user.getId(), pageable);
        return notifications.stream().map(this::mapToDTO).toList();
    }

    public List<NotificationDTO> getMyUnreadNotifications() {
        User user = authHelper.getCurrentUser();
        List<Notification> notifications = notificationRepository.findByReceiverIdAndReadFalseOrderByCreatedAtDesc(user.getId());
        return notifications.stream().map(this::mapToDTO).toList();
    }

    public long countMyUnreadNotifications() {
        User user = authHelper.getCurrentUser();
        return notificationRepository.countByReceiverIdAndReadFalse(user.getId());
    }

    public void markMyNotificationsAsRead() {
        User user = authHelper.getCurrentUser();
        List<Notification> notifications = notificationRepository.findByReceiverIdAndReadFalseOrderByCreatedAtDesc(user.getId());
        for (Notification n : notifications) {
            n.setRead(true);
        }
        notificationRepository.saveAll(notifications);
    }

    // COR-009 fix: made explicitly transactional (the repository call is now a single bulk
    // @Modifying JPQL DELETE, which requires an active transaction to execute) rather than
    // relying on whatever transactional behavior Spring Data's repository proxy happens to
    // supply by default for a bare derived method. Logs the affected-row count so retention
    // actually running (or silently matching zero rows every night) is visible instead of blind.
    @Transactional
    @Scheduled(cron = "0 0 2 * * *")
    public void deleteOldNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int deleted = notificationRepository.deleteByCreatedAtBefore(threshold);
        log.info("Deleted {} notification(s) older than {}", deleted, threshold);
    }

    // M-OOP2: narrow ownership method - notifications referencing a post have no JPA cascade
    // relationship on Post (unlike likes/comments/images), so this cleanup must happen
    // explicitly whenever a post is deleted. No new behavior beyond owning the existing
    // repository call; participates in the caller's existing transaction (PostService.deletePost
    // is already @Transactional).
    public void deleteNotificationsForPost(Post post) {
        notificationRepository.deleteByPost(post);
    }


    private NotificationDTO mapToDTO(Notification notification) {
        return new NotificationDTO(
                notification.getId(),
                notification.getType(),
                notification.getContent(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getSender() != null ? notification.getSender().getId() : null,
                notification.getSender() != null ? notification.getSender().getFullName() : "System",
                notification.getSender() != null ? notification.getSender().getProfilePictureUrl() : null,
                notification.getPost() != null ? notification.getPost().getPostId() : null
        );
    }
}