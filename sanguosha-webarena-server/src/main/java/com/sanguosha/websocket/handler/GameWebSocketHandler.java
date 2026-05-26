package com.sanguosha.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanguosha.room.entity.Room;
import com.sanguosha.room.service.RoomManager;
import com.sanguosha.websocket.dispatcher.MessageDispatcher;
import com.sanguosha.websocket.session.UserSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final UserSessionRegistry sessionRegistry;
    private final MessageDispatcher messageDispatcher;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Long userId = (Long) attrs.get("userId");
        String username = (String) attrs.get("username");

        if (userId == null) {
            try { session.close(); } catch (Exception ignored) {}
            return;
        }

        sessionRegistry.register(userId, session);
        log.info("用户 [{}] {} 已建立WebSocket连接", userId, username);

        // Check if user was already in a room (e.g. after browser refresh)
        Room existingRoom = roomManager.findRoomByUserId(userId);
        if (existingRoom != null) {
            Map<String, Object> roomData = new java.util.LinkedHashMap<>();
            roomData.put("roomId", existingRoom.getId());
            roomData.put("status", existingRoom.getStatus());

            Map<String, Object> msg = Map.of(
                "type", "RECONNECT_ROOM",
                "data", roomData
            );
            try {
                String json = objectMapper.writeValueAsString(msg);
                session.sendMessage(new TextMessage(json));
                log.info("用户 [{}] {} WebSocket重连，当前在房间 {} (状态: {})",
                    userId, username, existingRoom.getId(), existingRoom.getStatus());
            } catch (Exception e) {
                log.error("发送重连消息失败 userId={}", userId, e);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Map<String, Object> attrs = session.getAttributes();
        Long userId = (Long) attrs.get("userId");
        String username = (String) attrs.get("username");

        messageDispatcher.dispatch(userId, username, session, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Map<String, Object> attrs = session.getAttributes();
        Long userId = (Long) attrs.get("userId");

        if (userId != null) {
            sessionRegistry.remove(userId);
            log.info("用户 [{}] WebSocket连接已关闭", userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Map<String, Object> attrs = session.getAttributes();
        Long userId = (Long) attrs.get("userId");

        if (userId != null) {
            sessionRegistry.remove(userId);
        }
        log.error("WebSocket传输错误 userId={}", userId, exception);
    }
}