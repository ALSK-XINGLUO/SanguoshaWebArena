package com.sanguosha.game.context;

import com.sanguosha.game.card.CardType;
import com.sanguosha.game.card.GameCard;
import com.sanguosha.game.card.GameCard.Nature;
import com.sanguosha.game.card.GameCard.Suit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 三国杀标准版+EX+军争篇 完整牌池（160张）
 *
 * 构成：标准版104张 + EX 4张 + 军争篇52张 = 160张
 */
public class CardPool {

    /**
     * 初始化标准牌堆
     */
    public static List<GameCard> initDeck() {
        List<GameCard> cards = new ArrayList<>();
        int id = 0;

        // ==================== 基本牌 (85张) ====================

        // ---- 普通杀 (30张) ----
        // 黑桃: 7,8,8,9,9,10,10 (7张)
        cards.addAll(createCards(++id, CardType.SHA, 7, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHA, 8, 2, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHA, 9, 2, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHA, 10, 2, Suit.SPADE));
        // 红桃: 10,10 (2张)
        cards.addAll(createCards(++id, CardType.SHA, 10, 2, Suit.HEART));
        // 草花: 2,3,4,5,6,7,8,9,10,J,J (11张)
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
        // 方块: 6,7,8,9,10,K (6张)
        cards.addAll(createCards(++id, CardType.SHA, 6, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHA, 7, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHA, 8, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHA, 9, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHA, 10, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.SHA, 13, 1, Suit.DIAMOND));
        // 补充4张黑桃A/黑桃6/红桃Q/方块A
        cards.addAll(createCards(++id, CardType.SHA, 1, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHA, 6, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHA, 12, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.SHA, 1, 1, Suit.DIAMOND));

        // ---- 火杀 (5张) ----
        cards.addAll(createCards(++id, CardType.SHA, 4, 1, Suit.HEART, Nature.FIRE));
        cards.addAll(createCards(++id, CardType.SHA, 7, 1, Suit.HEART, Nature.FIRE));
        cards.addAll(createCards(++id, CardType.SHA, 10, 1, Suit.HEART, Nature.FIRE));
        cards.addAll(createCards(++id, CardType.SHA, 4, 1, Suit.DIAMOND, Nature.FIRE));
        cards.addAll(createCards(++id, CardType.SHA, 5, 1, Suit.DIAMOND, Nature.FIRE));

        // ---- 雷杀 (9张) ----
        cards.addAll(createCards(++id, CardType.SHA, 4, 1, Suit.SPADE, Nature.THUNDER));
        cards.addAll(createCards(++id, CardType.SHA, 5, 1, Suit.SPADE, Nature.THUNDER));
        cards.addAll(createCards(++id, CardType.SHA, 6, 1, Suit.SPADE, Nature.THUNDER));
        cards.addAll(createCards(++id, CardType.SHA, 7, 1, Suit.SPADE, Nature.THUNDER));
        cards.addAll(createCards(++id, CardType.SHA, 8, 1, Suit.SPADE, Nature.THUNDER));
        cards.addAll(createCards(++id, CardType.SHA, 5, 1, Suit.CLUB, Nature.THUNDER));
        cards.addAll(createCards(++id, CardType.SHA, 6, 1, Suit.CLUB, Nature.THUNDER));
        cards.addAll(createCards(++id, CardType.SHA, 7, 1, Suit.CLUB, Nature.THUNDER));
        cards.addAll(createCards(++id, CardType.SHA, 8, 1, Suit.CLUB, Nature.THUNDER));

        // ---- 闪 (24张) ----
        // 标准版: 红桃2,2 (2张)
        cards.addAll(createCards(++id, CardType.SHAN, 2, 2, Suit.HEART));
        // 标准版: 方块2,3,4,5,6,7,8,9,10,J,Q,K (12张)
        for (int n = 2; n <= 13; n++) {
            cards.addAll(createCards(++id, CardType.SHAN, n, 1, Suit.DIAMOND));
        }
        // 军争篇: 红桃8,9,J,Q,K (5张)
        cards.addAll(createCards(++id, CardType.SHAN, 8, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.SHAN, 9, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.SHAN, 11, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.SHAN, 12, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.SHAN, 13, 1, Suit.HEART));
        // 军争篇: 草花8,9,10,J,Q (5张)
        cards.addAll(createCards(++id, CardType.SHAN, 8, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHAN, 9, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHAN, 10, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHAN, 11, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.SHAN, 12, 1, Suit.CLUB));

        // ---- 桃 (12张) ----
        // 标准版: 红桃3,4,5,6,7,8,9,Q (8张)
        cards.addAll(createCards(++id, CardType.TAO, 3, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 4, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 5, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 6, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 7, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 8, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 9, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 12, 1, Suit.HEART));
        // 军争篇: 红桃A, 方块2,3 (3张)
        cards.addAll(createCards(++id, CardType.TAO, 1, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TAO, 2, 1, Suit.DIAMOND));
        cards.addAll(createCards(++id, CardType.TAO, 3, 1, Suit.DIAMOND));
        // 军争篇: 草花A (1张)
        cards.addAll(createCards(++id, CardType.TAO, 1, 1, Suit.CLUB));

        // ---- 酒 (5张) ----
        cards.addAll(createCards(++id, CardType.JIU, 3, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.JIU, 9, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.JIU, 3, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.JIU, 9, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.JIU, 9, 1, Suit.DIAMOND));

        // ==================== 锦囊牌 (50张) ====================

        // 决斗 (3张): 黑桃A, 草花A, 红桃A
        cards.addAll(createCards(++id, CardType.JUE_DOU, 1, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.JUE_DOU, 1, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.JUE_DOU, 1, 1, Suit.HEART));

        // 南蛮入侵 (3张): 黑桃7, 黑桃K, 草花7
        cards.addAll(createCards(++id, CardType.NAN_MAN, 7, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.NAN_MAN, 13, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.NAN_MAN, 7, 1, Suit.CLUB));

        // 万箭齐发 (1张): 红桃A
        cards.addAll(createCards(++id, CardType.WAN_JIAN, 1, 1, Suit.HEART));

        // 桃园结义 (1张): 红桃A(标准版)
        cards.addAll(createCards(++id, CardType.TAO_YUAN, 1, 1, Suit.HEART));

        // 五谷丰登 (2张): 草花3, 方块3
        cards.addAll(createCards(++id, CardType.WU_GU, 3, 1, Suit.CLUB));
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

        // 无中生有 (4张): 红桃7,8,9,J
        cards.addAll(createCards(++id, CardType.WU_ZHONG, 7, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.WU_ZHONG, 8, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.WU_ZHONG, 9, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.WU_ZHONG, 11, 1, Suit.HEART));

        // 借刀杀人 (2张): 草花Q, 方块Q
        cards.addAll(createCards(++id, CardType.JIE_DAO, 12, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.JIE_DAO, 12, 1, Suit.DIAMOND));

        // 无懈可击 (7张): 标准版3张 + 军争篇4张
        // 标准版: 黑桃J, 草花J, 方块K
        cards.addAll(createCards(++id, CardType.WU_XIE, 11, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.WU_XIE, 11, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.WU_XIE, 13, 1, Suit.DIAMOND));
        // 军争篇: 黑桃Q, 草花Q, 红桃J, 红桃Q
        cards.addAll(createCards(++id, CardType.WU_XIE, 12, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.WU_XIE, 12, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.WU_XIE, 11, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.WU_XIE, 12, 1, Suit.HEART));

        // 火攻 (3张): 红桃2, 红桃3, 方块Q (TODO: 效果未实现)
        cards.addAll(createCards(++id, CardType.HUO_GONG, 2, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.HUO_GONG, 3, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.HUO_GONG, 12, 1, Suit.DIAMOND));

        // 铁索连环 (6张): 黑桃J, 草花J, 草花Q, 黑桃Q, 红桃Q, 方块Q
        cards.addAll(createCards(++id, CardType.TIE_SUO, 11, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.TIE_SUO, 11, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.TIE_SUO, 12, 1, Suit.CLUB));
        cards.addAll(createCards(++id, CardType.TIE_SUO, 12, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.TIE_SUO, 12, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.TIE_SUO, 12, 1, Suit.DIAMOND));

        // ==================== 延时锦囊 (7张) ====================

        // 乐不思蜀 (3张): 黑桃6, 红桃6, 草花6
        cards.addAll(createCards(++id, CardType.LE_BU, 6, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.LE_BU, 6, 1, Suit.HEART));
        cards.addAll(createCards(++id, CardType.LE_BU, 6, 1, Suit.CLUB));

        // 兵粮寸断 (2张): 黑桃10, 草花10
        cards.addAll(createCards(++id, CardType.BING_LIANG, 10, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.BING_LIANG, 10, 1, Suit.CLUB));

        // 闪电 (2张): 黑桃A, 红桃A
        cards.addAll(createCards(++id, CardType.SHAN_DIAN, 1, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.SHAN_DIAN, 1, 1, Suit.HEART));

        // ==================== 装备牌 (25张) ====================

        // ---- 武器 (12张) ----
        // 诸葛连弩 (2张): 方块A
        cards.addAll(createCards(++id, CardType.ZHUGE_LIAN_NU, 1, 2, Suit.DIAMOND));
        // 雌雄双股剑 (1张): 黑桃2
        cards.addAll(createCards(++id, CardType.CI_XIONG, 2, 1, Suit.SPADE));
        // 寒冰剑 (1张): 黑桃2
        cards.addAll(createCards(++id, CardType.HAN_BING, 2, 1, Suit.SPADE));
        // 青釭剑 (1张): 黑桃6
        cards.addAll(createCards(++id, CardType.QING_GANG, 6, 1, Suit.SPADE));
        // 古锭刀 (1张): 黑桃A (TODO: 效果未实现)
        cards.addAll(createCards(++id, CardType.GU_DING_DAO, 1, 1, Suit.SPADE));
        // 贯石斧 (1张): 方块5
        cards.addAll(createCards(++id, CardType.GUAN_SHI, 5, 1, Suit.DIAMOND));
        // 青龙偃月刀 (1张): 黑桃5
        cards.addAll(createCards(++id, CardType.QING_LONG, 5, 1, Suit.SPADE));
        // 丈八蛇矛 (1张): 黑桃Q
        cards.addAll(createCards(++id, CardType.ZHANG_BA, 12, 1, Suit.SPADE));
        // 朱雀羽扇 (1张): 方块A
        cards.addAll(createCards(++id, CardType.ZHU_QUE, 1, 1, Suit.DIAMOND));
        // 方天画戟 (1张): 方块Q
        cards.addAll(createCards(++id, CardType.FANG_TIAN, 12, 1, Suit.DIAMOND));
        // 麒麟弓 (1张): 红桃5
        cards.addAll(createCards(++id, CardType.QI_LIN, 5, 1, Suit.HEART));

        // ---- 防具 (6张) ----
        // 八卦阵 (2张): 黑桃2, 草花2
        cards.addAll(createCards(++id, CardType.BA_GUA, 2, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.BA_GUA, 2, 1, Suit.CLUB));
        // 仁王盾 (1张): 草花2
        cards.addAll(createCards(++id, CardType.REN_WANG, 2, 1, Suit.CLUB));
        // 藤甲 (2张): 黑桃2, 草花2
        cards.addAll(createCards(++id, CardType.TENG_JIA, 2, 1, Suit.SPADE));
        cards.addAll(createCards(++id, CardType.TENG_JIA, 2, 1, Suit.CLUB));
        // 白银狮子 (1张): 草花A
        cards.addAll(createCards(++id, CardType.BAI_YIN, 1, 1, Suit.CLUB));

        // ---- -1马 (3张) ----
        // 赤兔: 红桃5
        cards.addAll(createCards(++id, CardType.CHI_TU, 5, 1, Suit.HEART));
        // 大宛: 黑桃5
        cards.addAll(createCards(++id, CardType.DA_WAN, 5, 1, Suit.SPADE));
        // 紫骍: 方块13
        cards.addAll(createCards(++id, CardType.ZI_XING, 13, 1, Suit.DIAMOND));

        // ---- +1马 (4张) ----
        // 的卢: 草花5
        cards.addAll(createCards(++id, CardType.DI_LU, 5, 1, Suit.CLUB));
        // 绝影: 黑桃5
        cards.addAll(createCards(++id, CardType.JUE_YING, 5, 1, Suit.SPADE));
        // 爪黄飞电: 黑桃13
        cards.addAll(createCards(++id, CardType.ZHAO_YUN, 13, 1, Suit.SPADE));
        // 骅骝: 红桃13
        cards.addAll(createCards(++id, CardType.HUA_LIU, 13, 1, Suit.HEART));

        logDeckStats(cards);
        return cards;
    }

    /**
     * 打印牌堆统计信息
     */
    public static void logDeckStats(List<GameCard> cards) {
        System.out.println("========== 牌堆统计 ==========");
        System.out.println("总张数: " + cards.size());

        // 按类型分组统计
        Map<String, Long> categoryCounts = cards.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCardType().getCategory(), TreeMap::new, Collectors.counting()));
        System.out.println("--- 按类别 ---");
        categoryCounts.forEach((cat, cnt) -> System.out.println("  " + cat + ": " + cnt));

        // 按牌名统计
        Map<String, Long> nameCounts = cards.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCardType().getDisplayName(), TreeMap::new, Collectors.counting()));
        System.out.println("--- 按牌名 ---");
        nameCounts.forEach((name, cnt) -> System.out.println("  " + name + ": " + cnt));

        // 属性杀统计
        long fireSha = cards.stream()
                .filter(c -> c.getCardType() == CardType.SHA && c.getNature() == Nature.FIRE)
                .count();
        long thunderSha = cards.stream()
                .filter(c -> c.getCardType() == CardType.SHA && c.getNature() == Nature.THUNDER)
                .count();
        long normalSha = cards.stream()
                .filter(c -> c.getCardType() == CardType.SHA && c.getNature() == Nature.NORMAL)
                .count();
        System.out.println("--- 杀明细 ---");
        System.out.println("  普通杀: " + normalSha);
        System.out.println("  火杀: " + fireSha);
        System.out.println("  雷杀: " + thunderSha);
        System.out.println("  总杀: " + (normalSha + fireSha + thunderSha));

        System.out.println("================================");
    }

    /**
     * 创建多张同类型同花色的卡牌（用于复数张数的牌）
     */
    private static List<GameCard> createCards(int baseId, CardType type, int number, int count, Suit suit) {
        List<GameCard> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new GameCard(baseId + "_" + i, type, suit, number));
        }
        return cards;
    }

    /**
     * 创建多张带属性的卡牌（用于火杀/雷杀）
     */
    private static List<GameCard> createCards(int baseId, CardType type, int number, int count, Suit suit, Nature nature) {
        List<GameCard> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new GameCard(baseId + "_" + i, type, suit, number, nature));
        }
        return cards;
    }
}
