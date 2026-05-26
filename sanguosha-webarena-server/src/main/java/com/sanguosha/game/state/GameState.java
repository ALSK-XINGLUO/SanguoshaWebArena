package com.sanguosha.game.state;

import com.sanguosha.game.card.GameCard;
import com.sanguosha.game.player.GamePlayer;
import lombok.Data;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 游戏状态 - 持有完整的1v1游戏状态
 */
@Data
public class GameState {
    private String gameId;                    // 游戏唯一ID
    private String roomId;                    // 关联的房间ID
    private List<GamePlayer> players;         // 玩家列表（2人）
    private int currentTurnIndex;             // 当前回合索引（0或1）
    private int turnNumber;                   // 回合数
    private String phase;                     // 当前阶段: JUDGE, DRAW, PLAY, DISCARD, END

    // 牌堆
    private List<GameCard> drawPile;          // 摸牌堆
    private List<GameCard> discardPile;       // 弃牌堆

    // 游戏状态
    private boolean started;
    private boolean finished;
    private Long winnerId;
    private String winnerName;

    // 当前待处理事件（用于玩家交互）
    private GameAction pendingAction;         // 等待玩家响应的动作

    // 日志
    private List<String> gameLog;

    public GameState(String gameId, String roomId) {
        this.gameId = gameId;
        this.roomId = roomId;
        this.players = new CopyOnWriteArrayList<>();
        this.drawPile = Collections.synchronizedList(new ArrayList<>());
        this.discardPile = Collections.synchronizedList(new ArrayList<>());
        this.gameLog = new CopyOnWriteArrayList<>();
        this.currentTurnIndex = 0;
        this.turnNumber = 1;
        this.phase = "PREPARE";
        this.started = false;
        this.finished = false;
    }

    /**
     * 获取当前回合玩家
     */
    public GamePlayer getCurrentPlayer() {
        return players.get(currentTurnIndex);
    }

    /**
     * 获取非当前回合玩家（对手）
     */
    public GamePlayer getOpponent() {
        return players.get(1 - currentTurnIndex);
    }

    /**
     * 获取存活玩家列表
     */
    public List<GamePlayer> getAlivePlayers() {
        return players.stream().filter(GamePlayer::isAlive).toList();
    }

    /**
     * 摸牌
     */
    public GameCard drawCard() {
        if (drawPile.isEmpty()) {
            reshuffleDiscardPile();
        }
        if (drawPile.isEmpty()) return null;
        return drawPile.remove(drawPile.size() - 1);
    }

    /**
     * 摸多张牌
     */
    public List<GameCard> drawCards(int count) {
        List<GameCard> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GameCard card = drawCard();
            if (card != null) drawn.add(card);
        }
        return drawn;
    }

    /**
     * 弃牌堆洗回牌堆
     */
    private void reshuffleDiscardPile() {
        if (discardPile.isEmpty()) return;
        drawPile.addAll(discardPile);
        discardPile.clear();
        Collections.shuffle(drawPile);
        addLog("弃牌堆已洗入牌堆");
    }

    /**
     * 弃牌到弃牌堆
     */
    public void discardCard(GameCard card) {
        if (card != null) {
            discardPile.add(card);
        }
    }

    /**
     * 移除手牌并弃掉
     */
    public void discardHandCard(GamePlayer player, String cardId) {
        player.getHandCards().stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .ifPresent(this::discardCard);
        player.removeHandCard(cardId);
    }

    /**
     * 切换到下个阶段
     */
    public void nextPhase() {
        phase = switch (phase) {
            case "PREPARE" -> "JUDGE";
            case "JUDGE" -> "DRAW";
            case "DRAW" -> "PLAY";
            case "PLAY" -> "DISCARD";
            case "DISCARD" -> "END";
            default -> "PREPARE";
        };
    }

    /**
     * 切换到下个玩家回合
     */
    public void nextTurn() {
        currentTurnIndex = 1 - currentTurnIndex;
        if (currentTurnIndex == 0) {
            turnNumber++;
        }
        phase = "PREPARE";
        addLog("---- 第 " + turnNumber + " 回合结束，第 " + (turnNumber + 1) + " 回合开始 ----");
    }

    /**
     * 添加日志
     */
    public void addLog(String log) {
        gameLog.add(log);
    }

    /**
     * 检查游戏是否结束
     */
    public void checkGameOver() {
        List<GamePlayer> alive = getAlivePlayers();
        if (alive.size() <= 1) {
            finished = true;
            if (alive.size() == 1) {
                winnerId = alive.get(0).getUserId();
                winnerName = alive.get(0).getUsername();
            }
            addLog("游戏结束！" + (winnerName != null ? winnerName + " 获胜！" : "平局！"));
        }
    }

    /**
     * 初始化牌堆
     */
    public void initDeck(List<GameCard> cards) {
        drawPile.clear();
        discardPile.clear();
        drawPile.addAll(cards);
        Collections.shuffle(drawPile);
    }

    /**
     * 序列化游戏状态为客户端数据
     */
    public Map<String, Object> toClientMap(Long selfUserId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("gameId", gameId);
        map.put("roomId", roomId);
        map.put("currentTurnIndex", currentTurnIndex);
        map.put("phase", phase);
        map.put("turnNumber", turnNumber);
        map.put("started", started);
        map.put("finished", finished);
        map.put("winnerId", winnerId);
        map.put("winnerName", winnerName);

        List<Map<String, Object>> playerList = players.stream()
                .map(p -> p.toClientMap(p.getUserId().equals(selfUserId)))
                .toList();
        map.put("players", playerList);

        map.put("drawPileCount", drawPile.size());
        map.put("discardPileCount", discardPile.size());

        if (pendingAction != null) {
            map.put("pendingAction", pendingAction.toClientMap());
        } else {
            map.put("pendingAction", null);
        }

        int logSize = gameLog.size();
        map.put("log", logSize > 20 ? gameLog.subList(logSize - 20, logSize) : new ArrayList<>(gameLog));

        return map;
    }

    /**
     * 发送给旁观者的状态（隐藏所有手牌）
     */
    public Map<String, Object> toSpectatorMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("gameId", gameId);
        map.put("roomId", roomId);
        map.put("currentTurnIndex", currentTurnIndex);
        map.put("phase", phase);
        map.put("turnNumber", turnNumber);
        map.put("started", started);
        map.put("finished", finished);
        map.put("winnerId", winnerId);
        map.put("winnerName", winnerName);

        List<Map<String, Object>> playerList = players.stream()
                .map(p -> p.toClientMap(false))
                .toList();
        map.put("players", playerList);
        map.put("drawPileCount", drawPile.size());
        map.put("discardPileCount", discardPile.size());
        map.put("pendingAction", null);

        int logSize = gameLog.size();
        map.put("log", logSize > 20 ? gameLog.subList(logSize - 20, logSize) : new ArrayList<>(gameLog));

        return map;
    }
}