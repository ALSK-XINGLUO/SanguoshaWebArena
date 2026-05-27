package com.sanguosha.game.player;

import com.sanguosha.game.card.CardType;
import com.sanguosha.game.card.GameCard;
import lombok.Data;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 游戏中的玩家状态
 */
@Data
public class GamePlayer {
    private Long userId;
    private String username;
    private int slotIndex;       // 0 or 1
    private int maxHp;
    private int currentHp;
    private boolean alive;

    // 卡牌区域
    private List<GameCard> handCards;          // 手牌
    private GameCard weapon;                   // 武器
    private GameCard armor;                    // 防具
    private GameCard plusHorse;                // +1马
    private GameCard minusHorse;               // -1马

    // 判定区（延时锦囊）
    private List<GameCard> judgeArea;

    // 回合状态
    private boolean usedShaThisTurn;           // 本回合是否使用过杀
    private int shaCountThisTurn;              // 本回合已用杀次数
    private boolean usedAlcoholThisTurn;       // 本回合是否使用过酒

    // 铁索连环状态
    private boolean chained;

    // 判定阶段跳过标记（由乐不思蜀/兵粮寸断/闪电等设置）
    private boolean skipDrawPhase;             // 跳过摸牌阶段
    private boolean skipPlayPhase;             // 跳过出牌阶段

    public GamePlayer(Long userId, String username, int slotIndex) {
        this.userId = userId;
        this.username = username;
        this.slotIndex = slotIndex;
        this.maxHp = 4;
        this.currentHp = 4;
        this.alive = true;
        this.handCards = new CopyOnWriteArrayList<>();
        this.judgeArea = new CopyOnWriteArrayList<>();
        this.usedShaThisTurn = false;
        this.shaCountThisTurn = 0;
        this.usedAlcoholThisTurn = false;
    }

    /**
     * 摸牌
     */
    public void drawCards(List<GameCard> cards) {
        handCards.addAll(cards);
    }

    /**
     * 出牌（从手牌中移除）
     */
    public boolean removeHandCard(String cardId) {
        return handCards.removeIf(c -> c.getId().equals(cardId));
    }

    /**
     * 弃置手牌
     */
    public void discardHandCard(String cardId) {
        removeHandCard(cardId);
    }

    /**
     * 装备装备牌
     */
    public void equip(GameCard card) {
        handCards.removeIf(c -> c.getId().equals(card.getId()));
        if (card.getCardType().isWeapon()) {
            weapon = card;
        } else if (card.getCardType().isArmor()) {
            armor = card;
        } else if (card.getCardType().isPlusHorse()) {
            plusHorse = card;
        } else if (card.getCardType().isMinusHorse()) {
            minusHorse = card;
        }
    }

    /**
     * 卸下装备（返回被替换的装备）
     */
    public GameCard unequip(CardType type) {
        GameCard old = null;
        if (type.isWeapon() && weapon != null) {
            old = weapon;
            weapon = null;
        } else if (type.isArmor() && armor != null) {
            old = armor;
            armor = null;
        } else if (type.isPlusHorse() && plusHorse != null) {
            old = plusHorse;
            plusHorse = null;
        } else if (type.isMinusHorse() && minusHorse != null) {
            old = minusHorse;
            minusHorse = null;
        }
        return old;
    }

    /**
     * 受到伤害
     */
    public int takeDamage(int damage) {
        currentHp -= damage;
        if (currentHp <= 0) {
            currentHp = 0;
            alive = false;
        }
        return damage;
    }

    /**
     * 回复体力
     */
    public int heal(int amount) {
        int before = currentHp;
        currentHp = Math.min(maxHp, currentHp + amount);
        return currentHp - before;
    }

    /**
     * 弃牌至手牌上限
     */
    public int getDiscardCount() {
        int maxHand = currentHp; // 手牌上限等于当前体力值
        return Math.max(0, handCards.size() - maxHand);
    }

    /**
     * 判断是否在攻击距离内
     */
    public boolean canAttack(GamePlayer target, boolean hasQingGang) {
        if (this.userId.equals(target.getUserId())) return false;
        int distance = calculateDistanceTo(target);
        int attackRange = getAttackRange(hasQingGang);
        return distance <= attackRange;
    }

    /**
     * 计算到目标的距离
     */
    public int calculateDistanceTo(GamePlayer target) {
        // 1v1中，距离为1
        // -1马减少距离，+1马增加距离
        int distance = 1;
        if (minusHorse != null) distance--;   // 自己的-1马
        if (target.getPlusHorse() != null) distance++; // 目标的+1马
        return Math.max(1, distance);
    }

    /**
     * 获取攻击范围
     */
    public int getAttackRange(boolean hasQingGang) {
        if (weapon == null) return 1;
        if (hasQingGang) return 1; // 青釭剑无视防具但攻击范围仍用武器
        return switch (weapon.getCardType()) {
            case QING_LONG, ZHANG_BA, GUAN_SHI -> 3;
            case FANG_TIAN, ZHU_QUE -> 4;
            case GU_DING_DAO -> 2;
            case ZHUGE_LIAN_NU -> 1;
            case QI_LIN -> 5;
            case HAN_BING, QING_GANG, CI_XIONG -> 2;
            default -> 1;
        };
    }

    /**
     * 获取手牌数量
     */
    public int getHandCardCount() {
        return handCards.size();
    }

    /**
     * 重置回合状态
     */
    public void resetTurnState() {
        usedShaThisTurn = false;
        shaCountThisTurn = 0;
        usedAlcoholThisTurn = false;
        skipDrawPhase = false;
        skipPlayPhase = false;
    }

    /**
     * 判断是否为自己
     */
    public boolean isSelf(Long userId) {
        return this.userId.equals(userId);
    }

    /**
     * 获取对方角色（1v1）
     */
    public GamePlayer getOpponent(List<GamePlayer> players) {
        return players.stream()
                .filter(p -> !p.getUserId().equals(this.userId))
                .findFirst().orElse(null);
    }

    /**
     * 序列化为客户端可读的Map（隐藏对手手牌）
     */
    public Map<String, Object> toClientMap(boolean isSelf) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", userId);
        map.put("username", username);
        map.put("slotIndex", slotIndex);
        map.put("maxHp", maxHp);
        map.put("currentHp", currentHp);
        map.put("alive", alive);
        map.put("handCardCount", handCards.size());

        if (chained) {
            map.put("chained", true);
        }

        if (isSelf) {
            List<Map<String, Object>> handList = handCards.stream().map(this::cardToMap).toList();
            map.put("handCards", handList);
        }

        map.put("weapon", weapon != null ? cardToMap(weapon) : null);
        map.put("armor", armor != null ? cardToMap(armor) : null);
        map.put("plusHorse", plusHorse != null ? cardToMap(plusHorse) : null);
        map.put("minusHorse", minusHorse != null ? cardToMap(minusHorse) : null);

        List<Map<String, Object>> judgeList = judgeArea.stream().map(this::cardToMap).toList();
        map.put("judgeArea", judgeList);

        return map;
    }

    private Map<String, Object> cardToMap(GameCard card) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", card.getId());
        m.put("type", card.getCardType().name());
        m.put("displayName", card.getCardType().getDisplayName());
        m.put("category", card.getCardType().getCategory());
        m.put("suit", card.getSuit().getSymbol());
        m.put("suitName", card.getSuit().name());
        m.put("number", card.getNumber());
        m.put("numberDisplay", card.getNumberDisplay());
        m.put("nature", card.getNature().name());
        return m;
    }
}