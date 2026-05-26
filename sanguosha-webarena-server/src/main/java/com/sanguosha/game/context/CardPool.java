package com.sanguosha.game.context;

import com.sanguosha.game.card.CardType;
import com.sanguosha.game.card.GameCard;
import com.sanguosha.game.card.GameCard.Suit;

import java.util.*;

/**
 * 标准三国杀牌池（标准版+风林火山扩展精简版）
 * 共约108张牌
 */
public class CardPool {

    /**
     * 初始化标准牌堆
     */
    public static List<GameCard> initDeck() {
        List<GameCard> cards = new ArrayList<>();
        int id = 0;

        // ==================== 基本牌 ====================
        // 杀 (30张)
        // 黑桃: 7,8,8,9,9,10,10 (7张)
        cards.addAll(createCards(++id, CardType.SHA, 7, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHA, 8, 2, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHA, 9, 2, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHA, 10, 2, Suit.SPADE));
        // 红桃: 10,10 (2张)
        cards.addAll(createCards(++id, CardType.SHA, 10, 2, Suit.HEART));
        // 草花: 2,3,4,5,6,7,8,9,10,11,11 (11张)
        cards.addAll(createCards(++id, CardType.SHA, 2, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHA, 3, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHA, 4, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHA, 5, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHA, 6, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHA, 7, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHA, 8, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHA, 9, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHA, 10, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHA, 11, 2, Suit.CLUB));
        // 方块: 6,7,8,9,10,13 (6张)
        cards.addAll(createCards(++id, CardType.SHA, 6, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHA, 7, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHA, 8, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHA, 9, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHA, 10, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHA, 13, 1, Suit.DIAMOND));

        // 闪 (15张)
        // 红桃: 2,2 (2张)
        cards.addAll(createCards(++id, CardType.SHAN, 2, 2, Suit.HEART));
        // 方块: 2,3,4,5,6,7,8,9,10,11,12,13 (12张 - 经典版)
        for (int n = 2; n <= 11; n++) {
            cards.addAll(createCards(++id, CardType.SHAN, n, 1, Suit.DIAMOND));
        }
        cards.addAll(createCards(++id, CardType.SHAN, 12, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHAN, 13, 1, Suit.DIAMOND));

        // 桃 (8张)
        // 红桃: 3,4,5,6,7,8,9,12 (8张)
        cards.addAll(createCards(++id, CardType.TAO, 3, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 4, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 5, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 6, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 7, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 8, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 9, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 12, 1, Suit.HEART));

        // 酒 (5张)
        // 黑桃: 3,9; 草花: 3,9; 方块: 9
        cards.addAll(createCards(++id, CardType.JIU, 3, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.JIU, 9, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.JIU, 3, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.JIU, 9, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.JIU, 9, 1, Suit.DIAMOND));

        // ==================== 锦囊牌 ====================
        // 决斗 (3张): 黑桃A, 草花A, 红桃A(风)
        cards.addAll(createCards(++id, CardType.JUE_DOU, 1, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.JUE_DOU, 1, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.JUE_DOU, 1, 1, Suit.HEART));

        // 南蛮入侵 (3张): 黑桃7, 黑桃13, 草花7
        cards.addAll(createCards(++id, CardType.NAN_MAN, 7, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.NAN_MAN, 13, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.NAN_MAN, 7, 1, Suit.CLUB));

        // 万箭齐发 (1张): 红桃A
        cards.addAll(createCards(++id, CardType.WAN_JIAN, 1, 1, Suit.HEART));

        // 桃园结义 (1张): 红桃A(标准版)
        cards.addAll(createCards(++id, CardType.TAO_YUAN, 1, 1, Suit.HEART));

        // 五谷丰登 (2张): 草花3, 方块3
        cards.addAll(createCards(++id, CardType.WU_GU, 3, 2, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.WU_GU, 3, 1, Suit.DIAMOND));

        // 过河拆桥 (6张): 黑桃3,4; 红桃3; 草花3,4; 方块3
        cards.addAll(createCards(++id, CardType.GUO_HE, 3, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.GUO_HE, 4, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.GUO_HE, 3, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.GUO_HE, 3, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.GUO_HE, 4, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.GUO_HE, 3, 1, Suit.DIAMOND));

        // 顺手牵羊 (5张): 黑桃3,4; 红桃3; 草花3; 方块3
        cards.addAll(createCards(++id, CardType.SHUN_SHOU, 3, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHUN_SHOU, 4, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHUN_SHOU, 3, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.SHUN_SHOU, 3, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHUN_SHOU, 3, 1, Suit.DIAMOND));

        // 无中生有 (4张): 红桃7,8,9,11
        cards.addAll(createCards(++id, CardType.WU_ZHONG, 7, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.WU_ZHONG, 8, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.WU_ZHONG, 9, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.WU_ZHONG, 11, 1, Suit.HEART));

        // 借刀杀人 (2张): 草花12, 方块12
        cards.addAll(createCards(++id, CardType.JIE_DAO, 12, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.JIE_DAO, 12, 1, Suit.DIAMOND));

        // 无懈可击 (3张): 黑桃11, 草花11, 方块13
        cards.addAll(createCards(++id, CardType.WU_XIE, 11, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.WU_XIE, 11, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.WU_XIE, 13, 1, Suit.DIAMOND));

        // ==================== 延时锦囊 ====================
        // 乐不思蜀 (3张): 黑桃6, 红桃6, 草花6
        cards.addAll(createCards(++id, CardType.LE_BU, 6, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.LE_BU, 6, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.LE_BU, 6, 1, Suit.CLUB));

        // 兵粮寸断 (2张): 黑桃10, 草花10
        cards.addAll(createCards(++id, CardType.BING_LIANG, 10, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.BING_LIANG, 10, 1, Suit.CLUB));

        // 闪电 (2张): 黑桃A, 红桃A(标准版)
        cards.addAll(createCards(++id, CardType.SHAN_DIAN, 1, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHAN_DIAN, 1, 1, Suit.HEART));

        // ==================== 装备牌 ====================
        // 青龙偃月刀: 黑桃5, 草花5
        cards.addAll(createCards(++id, CardType.QING_LONG, 5, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.QING_LONG, 5, 1, Suit.CLUB));

        // 丈八蛇矛: 黑桃12, 草花12
        cards.addAll(createCards(++id, CardType.ZHANG_BA, 12, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.ZHANG_BA, 12, 1, Suit.CLUB));

        // 贯石斧: 方块5
        cards.addAll(createCards(++id, CardType.GUAN_SHI, 5, 1, Suit.DIAMOND));

        // 方天画戟: 方块12
        cards.addAll(createCards(++id, CardType.FANG_TIAN, 12, 1, Suit.DIAMOND));

        // 麒麟弓: 红桃5
        cards.addAll(createCards(++id, CardType.QI_LIN, 5, 1, Suit.HEART));

        // 寒冰剑: 黑桃2
        cards.addAll(createCards(++id, CardType.HAN_BING, 2, 1, Suit.SPADE));

        // 青釭剑: 黑桃6
        cards.addAll(createCards(++id, CardType.QING_GANG, 6, 1, Suit.SPADE));

        // 雌雄双股剑: 黑桃2
        cards.addAll(createCards(++id, CardType.CI_XIONG, 2, 1, Suit.SPADE));

        // 朱雀羽扇: 方块A
        cards.addAll(createCards(++id, CardType.ZHU_QUE, 1, 1, Suit.DIAMOND));

        // 八卦阵: 黑桃2, 草花2
        cards.addAll(createCards(++id, CardType.BA_GUA, 2, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.BA_GUA, 2, 1, Suit.CLUB));

        // 仁王盾: 草花2
        cards.addAll(createCards(++id, CardType.REN_WANG, 2, 1, Suit.CLUB));

        // 藤甲: 黑桃2, 草花2
        cards.addAll(createCards(++id, CardType.TENG_JIA, 2, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.TENG_JIA, 2, 1, Suit.CLUB));

        // 白银狮子: 草花A
        cards.addAll(createCards(++id, CardType.BAI_YIN, 1, 1, Suit.CLUB));

        // 赤兔(+1): 红桃5
        cards.addAll(createCards(++id, CardType.CHI_TU, 5, 1, Suit.HEART));
        // 大宛(+1): 黑桃5
        cards.addAll(createCards(++id, CardType.DA_WAN, 5, 1, Suit.SPADE));
        // 紫骍(+1): 方块13
        cards.addAll(createCards(++id, CardType.ZI_XING, 13, 1, Suit.DIAMOND));
        // 的卢(+1): 草花5
        cards.addAll(createCards(++id, CardType.DI_LU, 5, 1, Suit.CLUB));

        // 华骝(-1): 红桃13
        cards.addAll(createCards(++id, CardType.HUA_LIU, 13, 1, Suit.HEART));
        // 卢马(-1): 黑桃13
        cards.addAll(createCards(++id, CardType.LU_MA, 13, 1, Suit.SPADE));
        // 节印(-1): 方块13
        cards.addAll(createCards(++id, CardType.JIE_YIN, 13, 1, Suit.DIAMOND));

        return cards;
    }

    /**
     * 创建多张同类型同花色的卡牌（用于复数张数的牌，如8张黑桃杀）
     */
    private static List<GameCard> createCards(int baseId, CardType type, int number, int count, Suit suit) {
        List<GameCard> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new GameCard(baseId + "_" + i, type, suit, number));
        }
        return cards;
    }
}