package org.example.apimywebsite.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveChatTracker {
    private static final ConcurrentHashMap<Integer, Integer> activeChats = new ConcurrentHashMap<>();

    public static void setActiveChat(int userId, int chattingWithId) {
        activeChats.put(userId, chattingWithId);
    }

    public static void removeActiveChat(int userId) {
        activeChats.remove(userId);
    }

    public static boolean isUserInChatWith(int userId, int otherUserId) {
        Integer current = activeChats.get(userId);
        System.out.println("🔍 Checking if user " + userId + " is chatting with " + otherUserId + " => " + current);
        return current != null && current == otherUserId;
    }

    public static Map<Integer, Integer> getAll() {
        return new HashMap<>(activeChats);
    }
}
