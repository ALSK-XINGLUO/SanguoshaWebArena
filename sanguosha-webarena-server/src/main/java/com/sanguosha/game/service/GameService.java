package com.sanguosha.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanguosha.game.card.GameCard;
import com.sanguosha.game.engine.GameEngine;
import com.sanguosha.game.player.GamePlayer;
import com.sanguosha.game.state.GameAction;
import com.sanguosha.game.state.GameState;
import com.sanguosha.game.skill.SkillUseRequest;
import com.sanguosha.room.entity.Room;
import com.sanguosha.room.service.RoomManager;
import com.sanguosha.websocket.message.MessageType;
import com.sanguosha.websocket.session.UserSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
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
                // PLAY 阶段需要等待用户出牌或结束出牌，不能自动推进
                if ("PLAY".equals(state.getPhase())) {
                    broadcastGameState(room, state);
                    return;
                }

                GameAction pendingAction = gameEngine.processPhase(state);

                if (pendingAction != null) {
                    state.setPendingAction(pendingAction);
                    broadcastGameState(room, state);
                    return;
                }

                // 没有pending action，阶段自动推进
            }

            // 处理在 processPhase 内部设置的 pendingAction（如闪电触发的濒死求桃）
            if (state.getPendingAction() != null) {
                broadcastGameState(room, state);
                return;
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
     * 处理获取当前游戏状态（用于页面刷新/重连后拉取状态）
     */
    public void handleFetchGameState(Long userId, String username, WebSocketSession session, Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        String gameId = (String) params.get("gameId");
        String roomId = (String) params.get("roomId");

        // Try to find game by gameId first
        GameState state = gameId != null ? gameEngine.getGame(gameId) : null;
        if (state == null && roomId != null) {
            state = gameEngine.getGameByRoomId(roomId);
        }
        // Fallback: find by userId
        if (state == null) {
            state = gameEngine.findGameByUserId(userId);
        }

        if (state == null) {
            sendError(session, "游戏不存在");
            return;
        }

        Room room = roomManager.getRoom(state.getRoomId());
        if (room != null) {
            Map<String, Object> stateData = Map.of(
                    "gameId", state.getGameId(),
                    "gameState", state.toClientMap(userId)
            );
            sendToPlayer(userId, MessageType.GAME_STATE, stateData);
            log.info("用户 {} 获取当前游戏状态: gameId={}", username, state.getGameId());
        } else {
            sendError(session, "房间不存在");
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
        // 多目标支持（铁索连环等）
        List<String> targetUserIds = null;
        Object rawTargets = params.get("targetUserIds");
        if (rawTargets instanceof List) {
            targetUserIds = ((List<?>) rawTargets).stream()
                    .map(Object::toString)
                    .toList();
        }

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
                state, userId, cardId, targetUserId, targetCardId, targetUserIds
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
     * 处理投降
     */
    public void handleSurrender(Long userId, String username, WebSocketSession session, Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        String gameId = (String) params.get("gameId");

        GameState state = gameEngine.getGame(gameId);
        if (state == null) {
            sendError(session, "游戏不存在");
            return;
        }

        if (state.isFinished()) {
            sendError(session, "游戏已结束");
            return;
        }

        Room room = roomManager.getRoom(state.getRoomId());
        if (room == null) {
            sendError(session, "房间不存在");
            return;
        }

        Long opponentId = gameEngine.removePlayerFromGame(state.getGameId(), userId);
        if (opponentId == null) {
            sendError(session, "无法处理投降");
            return;
        }

        var opponent = state.getPlayers().stream()
                .filter(p -> p.getUserId().equals(opponentId))
                .findFirst().orElse(null);

        state.setFinished(true);
        state.setWinnerId(opponentId);
        state.setWinnerName(opponent != null ? opponent.getUsername() : "对手");
        state.setPendingAction(null);
        state.addLog(username + " 投降了！" + (opponent != null ? opponent.getUsername() : "对手") + " 获胜！");

        log.info("玩家 {} 投降，{} 获胜 (gameId={})", username,
                opponent != null ? opponent.getUsername() : "对手", gameId);

        broadcastGameOver(room, state);
        sessionRegistry.clearDisconnect(userId);
        scheduleRoomClose(room.getId(), gameId);
    }

    /**
     * 测试换牌 — 测试功能，不限制次数
     */
    public void handleTestChangeHand(Long userId, String username, WebSocketSession session, Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        String gameId = (String) params.get("gameId");
        String roomId = (String) params.get("roomId");

        // 尝试通过 gameId 或 roomId 查找游戏
        GameState state = gameId != null ? gameEngine.getGame(gameId) : null;
        if (state == null && roomId != null) {
            state = gameEngine.getGameByRoomId(roomId);
        }
        if (state == null) {
            sendError(session, "游戏不存在");
            return;
        }

        if (state.isFinished()) {
            sendError(session, "游戏已结束");
            return;
        }

        // 查找玩家
        GamePlayer player = state.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst().orElse(null);
        if (player == null || !player.isAlive()) {
            sendError(session, "玩家不存在或已死亡");
            return;
        }

        // 有 pendingAction 时不允许换牌
        if (state.getPendingAction() != null) {
            sendError(session, "当前有等待响应的动作，不能换牌");
            return;
        }

        // 换牌逻辑：旧手牌入弃牌堆，从牌堆摸等量的牌
        int handSize = player.getHandCards().size();
        if (handSize == 0) {
            handSize = 4; // 手牌为空时默认摸4张
        }

        // 将旧手牌放入弃牌堆
        for (GameCard c : new ArrayList<>(player.getHandCards())) {
            state.discardCard(c);
        }
        player.getHandCards().clear();

        // 摸新牌（牌堆不足时自动洗弃牌堆）
        List<GameCard> drawn = state.drawCards(handSize);
        player.drawCards(drawn);

        state.addLog(username + " 使用测试换牌，重新获得 " + drawn.size() + " 张手牌");

        log.info("测试换牌: {} 手牌 {}→{} 张", username, handSize, drawn.size());

        Map<String, Object> toastData = Map.of("message", "已重新换牌", "type", "success");
        sendToPlayer(userId, MessageType.TOAST, toastData);

        // 广播 GAME_SYNC
        Room room = state.getRoomId() != null ? roomManager.getRoom(state.getRoomId()) : null;
        if (room != null) {
            broadcastGameState(room, state);
        }
    }

    /**
     * 处理玩家使用主动技能（如丈八蛇矛转化杀）
     */
    public void handleUseSkill(Long userId, String username, WebSocketSession session, Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        String gameId = (String) params.get("gameId");
        String skillCode = (String) params.get("skillCode");

        if (gameId == null || skillCode == null) {
            sendError(session, "参数不完整");
            return;
        }

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

        @SuppressWarnings("unchecked")
        List<String> selectedCardIds = (List<String>) params.get("selectedCardIds");
        String targetUserId = (String) params.get("targetUserId");
        Boolean isResponse = (Boolean) params.get("isResponse");
        String targetCardId = (String) params.get("targetCardId");

        SkillUseRequest request = SkillUseRequest.builder()
                .skillCode(skillCode)
                .selectedCardIds(selectedCardIds)
                .targetUserId(targetUserId)
                .targetCardId(targetCardId)
                .isResponse(isResponse != null && isResponse)
                .build();

        GameEngine.ActionResult result = gameEngine.useSkill(state, userId, request);

        if (!result.success()) {
            sendError(session, result.message());
            return;
        }

        log.info("玩家 {} 使用技能: {}, 结果: {}", username, skillCode, result.message());

        // 广播更新后的状态（技能内部已更新 pendingAction 等）
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
     * 定时检查断线超时玩家（每15秒执行一次）
     */
    @Scheduled(fixedRate = 15000)
    public void checkDisconnectTimeouts() {
        for (Long userId : sessionRegistry.getDisconnectedUserIds()) {
            Long disconnectTime = sessionRegistry.getDisconnectTime(userId);
            if (disconnectTime == null) continue;
            if (System.currentTimeMillis() - disconnectTime < 60_000) continue;

            Room room = roomManager.findRoomByUserId(userId);
            if (room == null || !"PLAYING".equals(room.getStatus())) {
                sessionRegistry.clearDisconnect(userId);
                continue;
            }

            GameState state = gameEngine.findGameByUserId(userId);
            if (state == null || state.isFinished()) {
                sessionRegistry.clearDisconnect(userId);
                continue;
            }

            log.info("玩家 {} 断线超过60秒，自动判负 (roomId={}, gameId={})",
                    userId, room.getId(), state.getGameId());

            Long opponentId = gameEngine.removePlayerFromGame(state.getGameId(), userId);
            var opponent = state.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(opponentId))
                    .findFirst().orElse(null);

            state.setFinished(true);
            state.setWinnerId(opponentId);
            state.setWinnerName(opponent != null ? opponent.getUsername() : "对手");
            state.setPendingAction(null);
            state.addLog((opponent != null ? opponent.getUsername() : "对手") + " 获胜（对方断线超时）");

            broadcastGameOver(room, state);
            sessionRegistry.clearDisconnect(userId);
            scheduleRoomClose(room.getId(), state.getGameId());
        }
    }

    /**
     * 立即关闭房间并清理游戏状态
     */
    private void scheduleRoomClose(String roomId, String gameId) {
        gameEngine.removeGame(gameId);
        roomManager.removeRoom(roomId);
        log.info("房间 {} (游戏 {}) 已自动关闭", roomId, gameId);
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