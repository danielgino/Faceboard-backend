package org.example.apimywebsite.api.controller;


import org.example.apimywebsite.dto.NotificationDTO;
import org.example.apimywebsite.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // M-OOP1: HttpServletRequest removed - it was never read by NotificationService, which
    // already resolved the acting user via AuthHelper.
    // L-DB4B: page/size are optional so the existing frontend (which calls this with neither)
    // keeps working unchanged, now bounded to the newest 50 by default; older notifications
    // remain reachable via ?page=1, ?page=2, etc. Response stays a flat List<NotificationDTO>.
    @GetMapping
    public List<NotificationDTO> getMyNotifications(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return notificationService.getMyNotifications(page, size);
    }

    @GetMapping("/unread-count")
    public long getUnreadCount() {
        return notificationService.countMyUnreadNotifications();
    }

    @PostMapping("/mark-all-as-read")
    public void markAllAsRead() {
        notificationService.markMyNotificationsAsRead();
    }
}