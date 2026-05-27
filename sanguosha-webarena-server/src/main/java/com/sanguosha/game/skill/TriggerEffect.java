package com.sanguosha.game.skill;

import com.sanguosha.game.engine.GameEngine;
import com.sanguosha.game.event.GameEvent;
import com.sanguosha.game.event.GameEventType;
import com.sanguosha.game.state.GameState;

/**
 * 触发效果接口
 * 在特定游戏事件发生时自动或可选触发的效果（如麒麟弓在伤害后弃马）
 */
public interface TriggerEffect {

    /** 技能唯一标识码 */
    String getSkillCode();

    /** 判断是否支持该事件类型 */
    boolean supports(GameEventType eventType);

    /** 判断是否确实应该触发（对事件内容做进一步检查） */
    boolean canTrigger(GameState state, GameEvent event);

    /** 执行触发效果 */
    GameEngine.ActionResult trigger(GameState state, GameEvent event);
}
