package com.sanguosha.game.skill;

import com.sanguosha.game.engine.GameEngine;
import com.sanguosha.game.player.GamePlayer;
import com.sanguosha.game.state.GameState;

/**
 * 主动技能效果接口
 * 装备或武将提供的主动技能，玩家可以选择发动（如丈八蛇矛转化杀）
 */
public interface SkillEffect {

    /** 技能唯一标识码，与前端约定一致 */
    String getSkillCode();

    /** 判断玩家当前是否能发动此技能 */
    boolean canUse(GameState state, GamePlayer player, SkillUseRequest request);

    /** 执行技能效果 */
    GameEngine.ActionResult execute(GameState state, GamePlayer player, SkillUseRequest request);
}
