package com.sanguosha.game.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 技能发动请求 — 从前端发送的 USE_SKILL / SKILL_RESPONSE 消息解析而来
 */
@Data
@Builder
@AllArgsConstructor
public class SkillUseRequest {
    /** 技能标识码 */
    private String skillCode;

    /** 玩家选择的卡牌ID列表（如丈八蛇矛选的两张手牌） */
    private List<String> selectedCardIds;

    /** 目标玩家ID */
    private String targetUserId;

    /** 目标卡牌ID（如麒麟弓选择弃哪匹马） */
    private String targetCardId;

    /** 是否为响应模式（在 pendingAction 期间使用） */
    private boolean isResponse;

    /** 响应的 pending action 类型 */
    private String respondToActionType;

    /** 额外数据 */
    private Map<String, Object> extraData;

    public SkillUseRequest(String skillCode) {
        this.skillCode = skillCode;
    }

    public SkillUseRequest(String skillCode, List<String> selectedCardIds) {
        this.skillCode = skillCode;
        this.selectedCardIds = selectedCardIds;
    }
}
