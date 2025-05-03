package org.example.apimywebsite.service;

import jakarta.servlet.http.HttpServletRequest;
import org.example.apimywebsite.api.model.Notification;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.NotificationDTO;
import org.example.apimywebsite.repository.NotificationRepository;
import org.example.apimywebsite.util.AuthHelper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuthHelper authHelper;
    private final SimpMessagingTemplate messagingTemplate;
    public NotificationService(NotificationRepository notificationRepository,SimpMessagingTemplate messagingTemplate, AuthHelper authHelper) {
        this.notificationRepository = notificationRepository;
        this.authHelper = authHelper;
        this.messagingTemplate=messagingTemplate;
    }


    public Notification createNotification(User receiver, User sender, String type, String content, Post post) {
        Notification notification = new Notification(receiver, sender, type, content, post);
        Notification saved = notificationRepository.save(notification);
        NotificationDTO dto = mapToDTO(saved);
        System.out.println("📡 שולח ל: /topic/notifications/" + receiver.getId());
        messagingTemplate.convertAndSend("/topic/notifications/" + receiver.getId(), dto);

        return saved;
    }

    public List<NotificationDTO> getMyNotifications(HttpServletRequest request) {
        User user = authHelper.getUserFromRequest(request);
        List<Notification> notifications = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(user.getId());
        return notifications.stream().map(this::mapToDTO).toList();
    }

    public List<NotificationDTO> getMyUnreadNotifications(HttpServletRequest request) {
        User user = authHelper.getUserFromRequest(request);
        List<Notification> notifications = notificationRepository.findByReceiverIdAndReadFalseOrderByCreatedAtDesc(user.getId());
        return notifications.stream().map(this::mapToDTO).toList();
    }

    public long countMyUnreadNotifications(HttpServletRequest request) {
        User user = authHelper.getUserFromRequest(request);
        return notificationRepository.countByReceiverIdAndReadFalse(user.getId());
    }

    public void markMyNotificationsAsRead(HttpServletRequest request) {
        User user = authHelper.getUserFromRequest(request);
        List<Notification> notifications = notificationRepository.findByReceiverIdAndReadFalseOrderByCreatedAtDesc(user.getId());
        for (Notification n : notifications) {
            n.setRead(true);
        }
        notificationRepository.saveAll(notifications);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void deleteOldNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        notificationRepository.deleteByCreatedAtBefore(threshold);
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