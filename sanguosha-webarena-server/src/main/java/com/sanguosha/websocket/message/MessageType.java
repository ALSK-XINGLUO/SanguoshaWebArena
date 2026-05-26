package com.sanguosha.websocket.message;

public class MessageType {
    // Room
    public static final String ROOM_LIST = "ROOM_LIST";
    public static final String CREATE_ROOM = "CREATE_ROOM";
    public static final String JOIN_ROOM = "JOIN_ROOM";
    public static final String LEAVE_ROOM = "LEAVE_ROOM";
    public static final String PLAYER_READY = "PLAYER_READY";
    public static final String CHAT = "CHAT";
    public static final String ROOM_UPDATE = "ROOM_UPDATE";

    // Game
    public static final String GAME_START = "GAME_START";
    public static final String GAME_STATE = "GAME_STATE";
    public static final String GAME_OVER = "GAME_OVER";
    public static final String PLAY_CARD = "PLAY_CARD";
    public static final String DISCARD_CARD = "DISCARD_CARD";
    public static final String PENDING_RESPONSE = "PENDING_RESPONSE";
    public static final String FETCH_GAME_STATE = "FETCH_GAME_STATE";
    public static final String END_TURN = "END_TURN";

    // Surrender
    public static final String SURRENDER = "SURRENDER";
    public static final String GAME_SURRENDER = "GAME_SURRENDER";

    // System
    public static final String ERROR = "ERROR";
    public static final String RECONNECT_ROOM = "RECONNECT_ROOM";

    private MessageType() {}
}