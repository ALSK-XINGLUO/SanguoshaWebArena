package com.sanguosha.websocket.handler;

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