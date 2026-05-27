package com.sanguosha.game.card;

import java.util.List;

/**
 * 虚拟卡牌 — 由技能转化产生的非实物卡牌
 * 不进入牌堆，只用于临时替代真实卡牌进入结算流程
 * （如丈八蛇矛将两张手牌当杀使用时创建的虚拟杀）
 */
public class VirtualCard extends GameCard {

    /** 创建此虚拟牌的技能码 */
    private final String skillCode;

    /** 作为素材的真实卡牌ID列表 */
    private final List<String> sourceCardIds;

    public VirtualCard(String id, CardType cardType, GameCard.Suit suit, int number,
                       String skillCode, List<String> sourceCardIds) {
        super(id, cardType, suit, number);
        this.skillCode = skillCode;
        this.sourceCardIds = sourceCardIds;
    }

    public String getSkillCode() {
        return skillCode;
    }

    public List<String> getSourceCardIds() {
        return sourceCardIds;
    }

    @Override
    public String getDisplayName() {
        return "【技能】" + super.getDisplayName();
    }
}
