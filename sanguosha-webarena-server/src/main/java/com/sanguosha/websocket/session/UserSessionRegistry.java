package com.sanguosha.websocket.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理用户ID -> WebSocketSession 的映射
 * 初版使用 ConcurrentHashMap，后续可迁移到 Redis
 */
@Component
public class UserSessionRegistry {

    private final ConcurrentHashMap<Long, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessionMap.put(userId, session);
    }

    public void remove(Long userId) {
        sessionMap.remove(userId);
    }

    public WebSocketSession getSession(Long userId) {
        return sessionMap.get(userId);
    }

    public boolean isOnline(Long userId) {
        return sessionMap.containsKey(userId);
    }
}