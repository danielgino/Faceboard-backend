package org.example.apimywebsite.util;


import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class WebSocketDisconnectListener {

    @Autowired
    private UserService userService;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // The disconnecting user must be derived from the server-resolved, authenticated
        // Principal (established at CONNECT and cached by Spring for the session) —
        // never from a client-suppliable header.
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            return;
        }

        User user = userService.findByUserName(principal.getName());
        if (user != null) {
            ActiveChatTracker.removeActiveChat(user.getId());
        }
    }
}