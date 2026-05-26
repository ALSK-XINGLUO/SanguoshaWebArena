package com.sanguosha.room.entity;

import lombok.Data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class Room {
    private String id;
    private String name;
    private Long ownerId;
    private String ownerName;
    private int maxPlayers;
    private String password; // null means public room
    private String status;   // WAITING, PLAYING
    private List<PlayerSlot> players;

    public static Room create(String id, String name, Long ownerId, String ownerName, String password) {
        Room room = new Room();
        room.id = id;
        room.name = name;
        room.ownerId = ownerId;
        room.ownerName = ownerName;
        room.maxPlayers = 2;
        room.password = password;
        room.status = "WAITING";
        room.players = new CopyOnWriteArrayList<>();

        PlayerSlot owner = new PlayerSlot();
        owner.setUserId(ownerId);
        owner.setUsername(ownerName);
        owner.setReady(false);
        owner.setSlotIndex(0);
        room.players.add(owner);

        return room;
    }

    @Data
    public static class PlayerSlot {
        private Long userId;
        private String username;
        private boolean ready;
        private int slotIndex;
    }
}