package com.sanguosha.game.card.effect;

import com.sanguosha.game.card.GameCard;
import com.sanguosha.game.engine.GameEngine.ActionResult;
import com.sanguosha.game.player.GamePlayer;
import com.sanguosha.game.state.GameState;

/**
 * 卡牌效果策略接口
 * 每种卡牌类型实现自己的效果逻辑
 */
@FunctionalInterface
public interface CardEffect {

    /**
     * 执行卡牌效果
     * @param state 游戏状态
     * @param player 使用卡牌的玩家
     * @param card 使用的卡牌
     * @param targetUserId 目标用户ID（可选）
     * @param targetCardId 目标卡牌ID（可选）
     * @return 执行结果
     */
    ActionResult execute(GameState state, GamePlayer player, GameCard card,
                         String targetUserId, String targetCardId);
}
