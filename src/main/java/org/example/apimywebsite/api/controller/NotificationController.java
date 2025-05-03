package org.example.apimywebsite.api.controller;


import jakarta.servlet.http.HttpServletRequest;
import org.example.apimywebsite.api.model.Notification;
import org.example.apimywebsite.dto.NotificationDTO;
import org.example.apimywebsite.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationDTO> getMyNotifications(HttpServletRequest request) {
        return notificationService.getMyNotifications(request);
    }

    @GetMapping("/unread-count")
    public long getUnreadCount(HttpServletRequest request) {
        return notificationService.countMyUnreadNotifications(request);
    }

    @PostMapping("/mark-all-as-read")
    public void markAllAsRead(HttpServletRequest request) {
        notificationService.markMyNotificationsAsRead(request);
    }
}