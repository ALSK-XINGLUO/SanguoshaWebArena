package com.sanguosha.game.state;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待玩家响应的动作（如需要出闪、出杀、选择目标等）
 */
@Data
public class GameAction {
    private String actionType;         // 动作类型: RESPOND_SHA, RESPOND_SHAN, RESPOND_WUXIE, CHOOSE_TARGET, DISCARD, etc.
    private String sourceCardId;       // 触发此动作的卡牌ID
    private Long sourcePlayerId;       // 触发此动作的玩家
    private List<String> optionalCardIds; // 可选使用的卡牌ID列表
    private List<Long> optionalTargetIds; // 可选目标玩家ID列表
    private int discardCount;          // 需要弃牌的数量
    private String message;            // 提示消息
    private Object extraData;          // 额外数据

    public Map<String, Object> toClientMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("actionType", actionType);
        map.put("sourceCardId", sourceCardId);
        map.put("sourcePlayerId", sourcePlayerId);
        map.put("optionalCardIds", optionalCardIds);
        map.put("optionalTargetIds", optionalTargetIds);
        map.put("discardCount", discardCount);
        map.put("message", message);
        map.put("extraData", extraData);
        return map;
    }
}