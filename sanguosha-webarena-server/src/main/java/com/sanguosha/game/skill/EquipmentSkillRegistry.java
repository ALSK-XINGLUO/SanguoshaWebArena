package com.sanguosha.game.skill;

import com.sanguosha.game.event.GameEventType;
import lombok.Getter;

import java.util.*;

/**
 * 装备技能注册表
 * 管理所有 SkillEffect（主动技能）和 TriggerEffect（触发技能）的注册与查询
 */
public class EquipmentSkillRegistry {

    /** 主动技能表：skillCode -> SkillEffect */
    private final Map<String, SkillEffect> skills = new HashMap<>();

    /** 触发技能表：eventType -> [TriggerEffect, ...] */
    private final EnumMap<GameEventType, List<TriggerEffect>> triggers = new EnumMap<>(GameEventType.class);

    /** 获取所有已注册的技能码 */
    @Getter
    private final Set<String> registeredSkillCodes = new HashSet<>();

    /**
     * 注册主动技能
     */
    public void registerSkill(SkillEffect skill) {
        skills.put(skill.getSkillCode(), skill);
        registeredSkillCodes.add(skill.getSkillCode());
    }

    /**
     * 注册触发技能
     */
    public void registerTrigger(GameEventType eventType, TriggerEffect trigger) {
        triggers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(trigger);
        registeredSkillCodes.add(trigger.getSkillCode());
    }

    /**
     * 获取主动技能
     */
    public SkillEffect getSkill(String skillCode) {
        return skills.get(skillCode);
    }

    /**
     * 获取某事件类型的所有触发效果
     */
    public List<TriggerEffect> getTriggers(GameEventType eventType) {
        return triggers.getOrDefault(eventType, Collections.emptyList());
    }

    /**
     * 是否有对应事件类型的触发效果
     */
    public boolean hasTriggers(GameEventType eventType) {
        List<TriggerEffect> list = triggers.get(eventType);
        return list != null && !list.isEmpty();
    }

    /**
     * 清空所有注册（用于测试）
     */
    public void clear() {
        skills.clear();
        triggers.clear();
        registeredSkillCodes.clear();
    }
}
