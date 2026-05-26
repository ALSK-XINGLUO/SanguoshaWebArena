package com.sanguosha.websocket.message;

public interface MessageType {
    // Room
    String CREATE_ROOM = "CREATE_ROOM";
    String JOIN_ROOM = "JOIN_ROOM";
    String LEAVE_ROOM = "LEAVE_ROOM";
    String ROOM_LIST = "ROOM_LIST";
    String PLAYER_READY = "PLAYER_READY";
    String ROOM_UPDATE = "ROOM_UPDATE";
    String CHAT = "CHAT";

    // Game
    String GAME_START = "GAME_START";
    String DRAW_CARD = "DRAW_CARD";
    String PLAY_CARD = "PLAY_CARD";
    String DISCARD_CARD = "DISCARD_CARD";
    String PENDING_ACTION = "PENDING_ACTION";
    String PENDING_RESPONSE = "PENDING_RESPONSE";
    String TURN_CHANGE = "TURN_CHANGE";
    String GAME_OVER = "GAME_OVER";
    String GAME_STATE = "GAME_STATE";

    // Error
    String ERROR = "ERROR";
}