package com.sanguosha.websocket.dispatcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanguosha.game.service.GameService;
import com.sanguosha.websocket.message.MessageType;
import com.sanguosha.lobby.service.LobbyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDispatcher {

    private static final Set<String> IGNORED_TYPES = Set.of("PING", "PONG");

    private final ObjectMapper objectMapper;
    private final LobbyService lobbyService;
    private final GameService gameService;

    public void dispatch(Long userId, String username, WebSocketSession session, String payload) {
        try {
            Map<String, Object> message = objectMapper.readValue(payload, new TypeReference<>() {});
            String type = (String) message.get("type");
            Object data = message.get("data");

            if (type == null) {
                sendError(session, "消息类型不能为空");
                return;
            }

            // ignore heartbeat messages
            if (IGNORED_TYPES.contains(type)) {
                return;
            }

            switch (type) {
                // Room
                case MessageType.ROOM_LIST -> lobbyService.handleRoomList(userId, username, session);
                case MessageType.CREATE_ROOM -> lobbyService.handleCreateRoom(userId, username, session, data);
                case MessageType.JOIN_ROOM -> lobbyService.handleJoinRoom(userId, username, session, data);
                case MessageType.LEAVE_ROOM -> lobbyService.handleLeaveRoom(userId, username, session, data);
                case MessageType.PLAYER_READY -> lobbyService.handlePlayerReady(userId, username, session, data);
                case MessageType.CHAT -> lobbyService.handleChat(userId, username, session, data);

                // Game
                case MessageType.GAME_START -> lobbyService.handlePlayerReady(userId, username, session, data);
                case MessageType.FETCH_GAME_STATE -> gameService.handleFetchGameState(userId, username, session, data);
                case MessageType.PLAY_CARD -> gameService.handlePlayCard(userId, username, session, data);
                case MessageType.DISCARD_CARD -> gameService.handleEndTurn(userId, username, session, data);
                case MessageType.END_TURN -> gameService.handleEndTurn(userId, username, session, data);
                case MessageType.PENDING_RESPONSE -> gameService.handleResponse(userId, username, session, data);

                // Surrender
                case MessageType.SURRENDER -> gameService.handleSurrender(userId, username, session, data);

                // Skill
                case MessageType.USE_SKILL -> gameService.handleUseSkill(userId, username, session, data);

                // Debug
                case MessageType.TEST_CHANGE_HAND -> gameService.handleTestChangeHand(userId, username, session, data);

                default -> sendError(session, "未知消息类型: " + type);
            }
        } catch (Exception e) {
            log.error("消息分发异常 userId={}", userId, e);
            sendError(session, "消息处理异常: " + e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            Map<String, Object> errorMsg = Map.of("type", MessageType.ERROR, "data", Map.of("message", message));
            String json = objectMapper.writeValueAsString(errorMsg);
            session.sendMessage(new org.springframework.web.socket.TextMessage(json));
        } catch (Exception ignored) {}
    }
}