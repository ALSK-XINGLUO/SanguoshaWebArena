package com.sanguosha.lobby.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanguosha.game.service.GameService;
import com.sanguosha.room.entity.Room;
import com.sanguosha.room.service.RoomManager;
import com.sanguosha.websocket.message.MessageType;
import com.sanguosha.websocket.session.UserSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LobbyService {

    private final ObjectMapper objectMapper;
    private final RoomManager roomManager;
    private final UserSessionRegistry sessionRegistry;
    private final GameService gameService;

    /**
     * 获取房间列表（只返回公开房间）
     */
    public void handleRoomList(Long userId, String username, WebSocketSession session) {
        List<Map<String, Object>> roomList = roomManager.getRoomList().stream()
                .filter(room -> "WAITING".equals(room.getStatus()))
                .filter(room -> room.getPassword() == null)  // only public rooms
                .map(this::roomToBriefMap)
                .collect(Collectors.toList());
        sendMessage(session, MessageType.ROOM_LIST, Map.of("rooms", roomList));
    }

    /**
     * 创建房间
     */
    public void handleCreateRoom(Long userId, String username, WebSocketSession session, Object data) {
        // check if user already in a room
        Room existing = roomManager.findRoomByUserId(userId);
        if (existing != null) {
            sendError(session, "你已在房间 " + existing.getName() + " 中");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        String name = (String) params.getOrDefault("name", username + "的房间");
        Object pwdObj = params.get("password");
        String password = pwdObj != null && !pwdObj.toString().isEmpty() ? pwdObj.toString() : null;

        Room room = roomManager.createRoom(name, userId, username, password);

        sendMessage(session, MessageType.CREATE_ROOM, roomToDetailMap(room));
        log.info("用户 {} 创建房间 {}", username, room.getId());
    }

    /**
     * 加入房间
     */
    public void handleJoinRoom(Long userId, String username, WebSocketSession session, Object data) {
        Room existing = roomManager.findRoomByUserId(userId);
        if (existing != null) {
            sendError(session, "你已在房间 " + existing.getName() + " 中");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        String roomId = (String) params.get("roomId");

        Room room = roomManager.getRoom(roomId);
        if (room == null) {
            sendError(session, "房间不存在");
            return;
        }

        if (!"WAITING".equals(room.getStatus())) {
            sendError(session, "房间已开始游戏");
            return;
        }

        if (room.getPlayers().size() >= room.getMaxPlayers()) {
            sendError(session, "房间已满");
            return;
        }

        if (room.getPassword() != null && !room.getPassword().isEmpty()) {
            String inputPassword = (String) params.get("password");
            if (!room.getPassword().equals(inputPassword)) {
                sendError(session, "房间密码错误");
                return;
            }
        }

        Room.PlayerSlot slot = new Room.PlayerSlot();
        slot.setUserId(userId);
        slot.setUsername(username);
        slot.setReady(false);
        slot.setSlotIndex(room.getPlayers().size());
        room.getPlayers().add(slot);

        // notify all players in room
        broadcastToRoom(room, MessageType.ROOM_UPDATE, roomToDetailMap(room));
        log.info("用户 {} 加入房间 {}", username, roomId);
    }

    /**
     * 离开房间
     */
    public void handleLeaveRoom(Long userId, String username, WebSocketSession session, Object data) {
        Room room = roomManager.findRoomByUserId(userId);
        if (room == null) {
            sendError(session, "你不在任何房间中");
            return;
        }

        room.getPlayers().removeIf(p -> p.getUserId().equals(userId));

        if (room.getPlayers().isEmpty()) {
            roomManager.removeRoom(room.getId());
            log.info("房间 {} 已销毁（所有玩家离开）", room.getId());
        } else {
            // transfer ownership if owner left
            if (room.getOwnerId().equals(userId) && !room.getPlayers().isEmpty()) {
                Room.PlayerSlot newOwner = room.getPlayers().get(0);
                room.setOwnerId(newOwner.getUserId());
                room.setOwnerName(newOwner.getUsername());
            }
            broadcastToRoom(room, MessageType.ROOM_UPDATE, roomToDetailMap(room));
        }

        sendMessage(session, MessageType.LEAVE_ROOM, Map.of("message", "已离开房间"));
        log.info("用户 {} 离开房间 {}", username, room != null ? room.getId() : "unknown");
    }

    /**
     * 玩家准备/取消准备
     */
    public void handlePlayerReady(Long userId, String username, WebSocketSession session, Object data) {
        Room room = roomManager.findRoomByUserId(userId);
        if (room == null) {
            sendError(session, "你不在任何房间中");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        boolean ready = Boolean.TRUE.equals(params.get("ready"));

        room.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .ifPresent(p -> p.setReady(ready));

        broadcastToRoom(room, MessageType.ROOM_UPDATE, roomToDetailMap(room));

        // auto start game if all ready
        if (room.getPlayers().size() == room.getMaxPlayers()
                && room.getPlayers().stream().allMatch(Room.PlayerSlot::isReady)) {
            gameService.startGame(room);
        }
    }

    /**
     * 房间聊天
     */
    public void handleChat(Long userId, String username, WebSocketSession session, Object data) {
        Room room = roomManager.findRoomByUserId(userId);
        if (room == null) {
            sendError(session, "你不在任何房间中");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) data;
        String content = (String) params.get("content");

        Map<String, Object> chatMsg = new HashMap<>();
        chatMsg.put("senderId", userId);
        chatMsg.put("senderName", username);
        chatMsg.put("content", content);
        chatMsg.put("timestamp", System.currentTimeMillis());

        broadcastToRoom(room, MessageType.CHAT, chatMsg);
    }

    // ========== Helper Methods ==========

    private void broadcastToRoom(Room room, String type, Object data) {
        Map<String, Object> message = Map.of("type", type, "data", data);
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化消息失败", e);
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        for (Room.PlayerSlot player : room.getPlayers()) {
            WebSocketSession ws = sessionRegistry.getSession(player.getUserId());
            if (ws != null && ws.isOpen()) {
                try {
                    ws.sendMessage(textMessage);
                } catch (Exception e) {
                    log.error("发送消息给用户 {} 失败", player.getUserId(), e);
                }
            }
        }
    }

    private void sendMessage(WebSocketSession session, String type, Object data) {
        try {
            Map<String, Object> message = Map.of("type", type, "data", data);
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("发送消息失败", e);
        }
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            Map<String, Object> errorMsg = Map.of("type", MessageType.ERROR, "data", Map.of("message", message));
            String json = objectMapper.writeValueAsString(errorMsg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception ignored) {}
    }

    private Map<String, Object> roomToBriefMap(Room room) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", room.getId());
        map.put("name", room.getName());
        map.put("ownerName", room.getOwnerName());
        map.put("hasPassword", room.getPassword() != null && !room.getPassword().isEmpty());
        map.put("playerCount", room.getPlayers().size());
        map.put("maxPlayers", room.getMaxPlayers());
        map.put("status", room.getStatus());
        return map;
    }

    private Map<String, Object> roomToDetailMap(Room room) {
        Map<String, Object> map = roomToBriefMap(room);
        List<Map<String, Object>> playerList = room.getPlayers().stream().map(p -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("userId", p.getUserId());
            pm.put("username", p.getUsername());
            pm.put("ready", p.isReady());
            pm.put("slotIndex", p.getSlotIndex());
            return pm;
        }).collect(Collectors.toList());
        map.put("players", playerList);
        return map;
    }
}