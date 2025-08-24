package org.example.apimywebsite.util;


import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketDisconnectListener {

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        System.out.println("🔌 SessionDisconnectEvent triggered");
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String userIdStr = headerAccessor.getFirstNativeHeader("userId");

        if (userIdStr != null) {
            int userId = Integer.parseInt(userIdStr);
            ActiveChatTracker.removeActiveChat(userId);
        }
    }
}