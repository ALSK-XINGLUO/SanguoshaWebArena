package com.sanguosha.game.event;

import com.sanguosha.game.card.GameCard;
import com.sanguosha.game.player.GamePlayer;
import com.sanguosha.game.state.GameState;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 游戏事件 — 承载事件发生的上下文，传递给 TriggerEffect 判定和触发
 */
@Data
@AllArgsConstructor
public class GameEvent {
    private GameEventType type;
    private GameState state;
    private GamePlayer source;
    private GamePlayer target;
    private GameCard card;
    private int damage;
    private Map<String, Object> extra;
}
