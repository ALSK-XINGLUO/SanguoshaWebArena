package com.sanguosha.game.event;

/**
 * 游戏事件类型 — 用于技能触发系统的关键动作抽象
 * 每种类型代表游戏中一个可被技能拦截或响应的时刻
 */
public enum GameEventType {
    /** 卡牌正在使用（结算前） */
    CARD_USING,
    /** 卡牌已使用（结算后） */
    CARD_USED,
    /** 正在指定目标 */
    CARD_TARGETING,
    /** 目标已指定 */
    CARD_TARGETED,
    /** 即将造成伤害 */
    DAMAGE_BEFORE,
    /** 伤害已造成 */
    DAMAGE_DONE,
    /** 玩家进入濒死状态 */
    PLAYER_DYING,
    /** 玩家死亡 */
    PLAYER_DEAD,
    /** 阶段开始 */
    PHASE_START,
    /** 阶段结束 */
    PHASE_END,
    /** 技能已发动 */
    SKILL_USED,
}
