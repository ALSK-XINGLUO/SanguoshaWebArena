package com.sanguosha.game.state;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 待玩家响应的动作（如需要出闪、出杀、选择目标等）
 */
@Data
public class GameAction {
    /** 预留的动作类型常量 */
    public static final String WAIT_WUXIE_RESPONSE = "WAIT_WUXIE_RESPONSE";
    public static final String WAIT_EQUIP_TRIGGER = "WAIT_EQUIP_TRIGGER";
    public static final String WAIT_SKILL_RESPONSE = "WAIT_SKILL_RESPONSE";
    public static final String WAIT_CONVERT_CARD = "WAIT_CONVERT_CARD";

    private String actionId = UUID.randomUUID().toString();  // 唯一动作ID，防止重复消费
    private String actionType;         // 动作类型: RESPOND_SHA, RESPOND_SHAN, CHOOSE_TARGET, DISCARD, DYING_REQUIRE_TAO, WAIT_WUXIE_RESPONSE, WAIT_EQUIP_TRIGGER, etc.
    private String sourceCardId;       // 触发此动作的卡牌ID
    private Long sourcePlayerId;       // 触发此动作的玩家
    private List<String> optionalCardIds; // 可选使用的卡牌ID列表
    private List<Long> optionalTargetIds; // 可选目标玩家ID列表
    private int discardCount;          // 需要弃牌的数量
    private String message;            // 提示消息
    private Object extraData;          // 额外数据
    private List<Map<String, Object>> optionalCards; // 可选卡牌的展示信息（名称、花色等）

    public Map<String, Object> toClientMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("actionId", actionId);
        map.put("actionType", actionType);
        map.put("sourceCardId", sourceCardId);
        map.put("sourcePlayerId", sourcePlayerId);
        map.put("optionalCardIds", optionalCardIds);
        map.put("optionalTargetIds", optionalTargetIds);
        map.put("discardCount", discardCount);
        map.put("message", message);
        map.put("extraData", extraData);
        map.put("optionalCards", optionalCards != null ? optionalCards : new ArrayList<>());
        return map;
    }
}