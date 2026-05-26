package com.sanguosha.room.service;

import com.sanguosha.room.entity.Room;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间管理器，初版使用 ConcurrentHashMap 存储房间
 */
@Slf4j
@Component
public class RoomManager {

    private final ConcurrentHashMap<String, Room> roomMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("RoomManager initialized - all rooms cleared");
    }

    public Room createRoom(String name, Long ownerId, String ownerName, String password) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Room room = Room.create(id, name, ownerId, ownerName, password);
        roomMap.put(id, room);
        return room;
    }

    public Room getRoom(String roomId) {
        return roomMap.get(roomId);
    }

    public Room findRoomByUserId(Long userId) {
        return roomMap.values().stream()
                .filter(room -> room.getPlayers().stream().anyMatch(p -> p.getUserId().equals(userId)))
                .findFirst().orElse(null);
    }

    public List<Room> getRoomList() {
        return List.copyOf(roomMap.values());
    }

    public void removeRoom(String roomId) {
        roomMap.remove(roomId);
    }

    public void clearAllRooms() {
        roomMap.clear();
    }
}