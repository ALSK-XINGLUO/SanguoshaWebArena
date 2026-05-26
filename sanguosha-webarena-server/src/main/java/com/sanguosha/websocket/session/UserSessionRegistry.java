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
    private final ConcurrentHashMap<Long, Long> disconnectTimeMap = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessionMap.put(userId, session);
        disconnectTimeMap.remove(userId); // clear disconnect record on reconnect
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

    /**
     * 记录用户断线时间（仅在游戏进行中时调用）
     */
    public void recordDisconnect(Long userId) {
        disconnectTimeMap.putIfAbsent(userId, System.currentTimeMillis());
    }

    /**
     * 清除用户断线记录（重连时调用）
     */
    public void clearDisconnect(Long userId) {
        disconnectTimeMap.remove(userId);
    }

    /**
     * 获取用户断线时间戳，null表示未断线
     */
    public Long getDisconnectTime(Long userId) {
        return disconnectTimeMap.get(userId);
    }

    /**
     * 获取所有有断线记录的用户ID
     */
    public ConcurrentHashMap.KeySetView<Long, Long> getDisconnectedUserIds() {
        return disconnectTimeMap.keySet();
    }
}