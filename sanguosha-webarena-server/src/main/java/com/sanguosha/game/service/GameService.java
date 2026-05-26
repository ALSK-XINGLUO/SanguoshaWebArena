package com.sanguosha.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanguosha.game.engine.GameEngine;
import com.sanguosha.game.state.GameAction;
import com.sanguosha.game.state.GameState;
import com.sanguosha.room.entity.Room;
import com.sanguosha.room.service.RoomManager;
import com.sanguosha.websocket.message.MessageType;
import com.sanguosha.websocket.session.UserSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;

/**
 * 游戏服务 - 整合游戏引擎与WebSocket通信
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final ObjectMapper objectMapper;
    private final GameEngine gameEngine;
    private final RoomManager roomManager;
    private final UserSessionRegistry sessionRegistry;

    /**
     * 开始游戏（当房间内所有玩家准备就绪时触发）
     */
    public void startGame(Room room) {
        if (room.getPlayers().size() != 2) {
            log.warn("房间 {} 玩家数量不足，无法开始游戏", room.getId());
            return;
        }

        room.setStatus("PLAYING");

        Room.PlayerSlot p1 = room.getPlayers().get(0);
        Room.PlayerSlot p2 = room.getPlayers().get(1);

        String gameId = UUID.randomUUID().toString().substring(0, 8);
        GameState state = gameEngine.startGame(
                room.getId(), gameId,
                p1.getUserId(), p1.getUsername(),
                p2.getUserId(), p2.getUsername()
        );

        log.info("游戏 {} 开始: {} vs {}", gameId, p1.getUsername(), p2.getUsername());

        // 广播游戏开始消息
        broadcastGameStart(room, state, gameId);

        // 处理初始阶段（DRAW阶段）
        processGamePhase(room, state);
    }

    /**
     * 广播游戏开始
     */
    private void broadcastGameStart(Room room, GameState state, String gameId) {
        for (Room.PlayerSlot player : room.getPlayers()) {
            Map<String, Object> gameStartData = Map.of(
                    "gameId", gameId,
                    "roomId", room.getId(),
                    "gameState", state.toClientMap(player.getUserId())
            );
            sendToPlayer(player.getUserId(), MessageType.GAME_START, gameStartData);
        }
    }

    /**
     * 处理游戏阶段，将pendingAction发送给对应玩家
     */
    private void processGamePhase(Room room, GameState state) {
        try {
            // 循环处理不需要玩家交互的阶段
            while (state.getPendingAction() == null && !state.isFinished()) {
                GameAction pendingAction = gameEngine.processPhase(state);

                if (pendingAction != null) {
                    state.setPendingAction(pendingAction);
                    broadcastGameState(room, state);
                    return;
                }

                // 没有pending action，阶段自动推进
            }

            if (state.isFinished()) {
                broadcastGameOver(room, state);
                room.setStatus("WAITING");
            }
        } catch (Exception e) {
            log.error("处理游戏阶段异常", e);
        }
    }

    /**
     * 处理出牌消息
     */
    public void handlePlayCard(Long userId, String username, WebSocketSession session, Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        String gameId = (String) params.get("gameId");
        String cardId = (String) params.get("cardId");
        String targetUserId = (String) params.get("targetUserId");
        String targetCardId = (String) params.get("targetCardId");

        GameState state = gameEngine.getGame(gameId);
        if (state == null) {
            sendError(session, "游戏不存在");
            return;
        }

        Room room = roomManager.getRoom(state.getRoomId());
        if (room == null) {
            sendError(session, "房间不存在");
            return;
        }

        GameEngine.ActionResult result = gameEngine.playCard(
                state, userId, cardId, targetUserId, targetCardId
        );

        if (!result.success()) {
            sendError(session, result.message());
            return;
        }

        log.info("玩家 {} 出牌: {}, 结果: {}", username, cardId, result.message());

        // 如果有待处理的响应请求，状态中已包含
        if (result.pendingAction() != null) {
            state.setPendingAction(result.pendingAction());
        } else {
            state.setPendingAction(null);
        }

        // 广播更新后的状态
        broadcastGameState(room, state);

        // 检查游戏是否结束
        if (state.isFinished()) {
            broadcastGameOver(room, state);
            room.setStatus("WAITING");
            return;
        }

        // 继续处理阶段
        processGamePhase(room, state);
    }

    /**
     * 处理响应消息（出闪/出杀等）
     */
    public void handleResponse(Long userId, String username, WebSocketSession session, Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        String gameId = (String) params.get("gameId");
        String cardId = (String) params.get("cardId");
        String targetUserId = (String) params.get("targetUserId");

        GameState state = gameEngine.getGame(gameId);
        if (state == null) {
            sendError(session, "游戏不存在");
            return;
        }

        Room room = roomManager.getRoom(state.getRoomId());
        if (room == null) {
            sendError(session, "房间不存在");
            return;
        }

        GameEngine.ActionResult result = gameEngine.handleResponse(
                state, userId, cardId, targetUserId
        );

        if (!result.success()) {
            sendError(session, result.message());
            return;
        }

        log.info("玩家 {} 响应: {}, 结果: {}", username,
                cardId != null ? cardId : "跳过", result.message());

        // 清除pending action或设置新的
        state.setPendingAction(result.pendingAction());

        // 广播更新后的状态
        broadcastGameState(room, state);

        // 检查游戏是否结束
        if (state.isFinished()) {
            broadcastGameOver(room, state);
            room.setStatus("WAITING");
            return;
        }

        // 继续处理阶段
        processGamePhase(room, state);
    }

    /**
     * 结束出牌阶段
     */
    public void handleEndTurn(Long userId, String username, WebSocketSession session, Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        String gameId = (String) params.get("gameId");

        GameState state = gameEngine.getGame(gameId);
        if (state == null) {
            sendError(session, "游戏不存在");
            return;
        }

        Room room = roomManager.getRoom(state.getRoomId());
        if (room == null) {
            sendError(session, "房间不存在");
            return;
        }

        GameEngine.ActionResult result = gameEngine.endPlayPhase(state, userId);
        if (!result.success()) {
            sendError(session, result.message());
            return;
        }

        log.info("玩家 {} 结束回合", username);

        state.setPendingAction(null);
        broadcastGameState(room, state);

        if (state.isFinished()) {
            broadcastGameOver(room, state);
            room.setStatus("WAITING");
            return;
        }

        processGamePhase(room, state);
    }

    /**
     * 向房间内所有玩家广播游戏状态
     */
    private void broadcastGameState(Room room, GameState state) {
        for (Room.PlayerSlot player : room.getPlayers()) {
            Map<String, Object> stateData = Map.of(
                    "gameId", state.getGameId(),
                    "gameState", state.toClientMap(player.getUserId())
            );
            sendToPlayer(player.getUserId(), MessageType.GAME_STATE, stateData);
        }
    }

    /**
     * 广播游戏结束
     */
    private void broadcastGameOver(Room room, GameState state) {
        for (Room.PlayerSlot player : room.getPlayers()) {
            Map<String, Object> gameOverData = Map.of(
                    "gameId", state.getGameId(),
                    "winnerId", state.getWinnerId(),
                    "winnerName", state.getWinnerName(),
                    "gameState", state.toClientMap(player.getUserId())
            );
            sendToPlayer(player.getUserId(), MessageType.GAME_OVER, gameOverData);
        }
    }

    /**
     * 发送消息给指定玩家
     */
    private void sendToPlayer(Long userId, String type, Object data) {
        WebSocketSession session = sessionRegistry.getSession(userId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> message = Map.of("type", type, "data", data);
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
                log.error("发送消息给用户 {} 失败, type={}", userId, type, e);
            }
        }
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            Map<String, Object> errorMsg = Map.of("type", MessageType.ERROR, "data", Map.of("message", message));
            String json = objectMapper.writeValueAsString(errorMsg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception ignored) {}
    }
}