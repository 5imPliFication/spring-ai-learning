package com.example.ai.config;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomPresenceTracker {

    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionRooms = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userRooms = new ConcurrentHashMap<>();

    public void markActive(String sessionId, String userId, String roomId) {
        if (sessionId == null || userId == null || roomId == null) return;
        sessionToUser.putIfAbsent(sessionId, userId);
        sessionRooms.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(roomId);
        userRooms.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(roomId);
    }

    public void markInactive(String sessionId, String roomId) {
        if (sessionId == null || roomId == null) return;
        Set<String> rooms = sessionRooms.get(sessionId);
        if (rooms != null) {
            rooms.remove(roomId);
            if (rooms.isEmpty()) {
                sessionRooms.remove(sessionId);
            }
        }
        String userId = sessionToUser.get(sessionId);
        if (userId != null) {
            rebuildUserRooms(userId);
        }
    }

    public void clearSession(String sessionId) {
        if (sessionId == null) return;
        String userId = sessionToUser.remove(sessionId);
        sessionRooms.remove(sessionId);
        if (userId != null) {
            rebuildUserRooms(userId);
        }
    }

    public boolean isViewing(String userId, String roomId) {
        if (userId == null || roomId == null) return false;
        Set<String> rooms = userRooms.get(userId);
        return rooms != null && rooms.contains(roomId);
    }

    private void rebuildUserRooms(String userId) {
        Set<String> rebuilt = ConcurrentHashMap.newKeySet();
        sessionToUser.forEach((sessionId, uid) -> {
            if (uid.equals(userId)) {
                Set<String> rooms = sessionRooms.get(sessionId);
                if (rooms != null) {
                    rebuilt.addAll(rooms);
                }
            }
        });
        if (rebuilt.isEmpty()) {
            userRooms.remove(userId);
        } else {
            userRooms.put(userId, rebuilt);
        }
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        clearSession(event.getSessionId());
    }
}