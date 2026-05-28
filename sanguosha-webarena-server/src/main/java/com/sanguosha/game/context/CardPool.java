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
 * 四种花色各40张，按 ♥ ♦ ♣ ♠ 顺序排列
 */
public class CardPool {

    /**
     * 初始化标准牌堆
     */
    public static List<GameCard> initDeck() {
        List<GameCard> cards = new ArrayList<>();
        int id = 0;

        // ==================== ♥ 红桃 (40张) ====================
        // A: 桃园结义, 万箭齐发, 无懈可击
        add(cards, ++id, CardType.TAO_YUAN, 1, Suit.HEART);
        add(cards, ++id, CardType.WAN_JIAN, 1, Suit.HEART);
        add(cards, ++id, CardType.WU_XIE, 1, Suit.HEART);
        // 2: 闪, 闪, 火攻
        add(cards, ++id, CardType.SHAN, 2, Suit.HEART);
        add(cards, ++id, CardType.SHAN, 2, Suit.HEART);
        add(cards, ++id, CardType.HUO_GONG, 2, Suit.HEART);
        // 3: 桃, 五谷丰登, 火攻
        add(cards, ++id, CardType.TAO, 3, Suit.HEART);
        add(cards, ++id, CardType.WU_GU, 3, Suit.HEART);
        add(cards, ++id, CardType.HUO_GONG, 3, Suit.HEART);
        // 4: 桃, 五谷丰登, 火杀
        add(cards, ++id, CardType.TAO, 4, Suit.HEART);
        add(cards, ++id, CardType.WU_GU, 4, Suit.HEART);
        add(cards, ++id, CardType.SHA, 4, Suit.HEART, Nature.FIRE);
        // 5: 赤兔, 麒麟弓, 桃
        add(cards, ++id, CardType.CHI_TU, 5, Suit.HEART);
        add(cards, ++id, CardType.QI_LIN, 5, Suit.HEART);
        add(cards, ++id, CardType.TAO, 5, Suit.HEART);
        // 6: 桃, 乐不思蜀, 桃
        add(cards, ++id, CardType.TAO, 6, Suit.HEART);
        add(cards, ++id, CardType.LE_BU, 6, Suit.HEART);
        add(cards, ++id, CardType.TAO, 6, Suit.HEART);
        // 7: 桃, 无中生有, 火杀
        add(cards, ++id, CardType.TAO, 7, Suit.HEART);
        add(cards, ++id, CardType.WU_ZHONG, 7, Suit.HEART);
        add(cards, ++id, CardType.SHA, 7, Suit.HEART, Nature.FIRE);
        // 8: 桃, 无中生有, 闪
        add(cards, ++id, CardType.TAO, 8, Suit.HEART);
        add(cards, ++id, CardType.WU_ZHONG, 8, Suit.HEART);
        add(cards, ++id, CardType.SHAN, 8, Suit.HEART);
        // 9: 桃, 无中生有, 闪
        add(cards, ++id, CardType.TAO, 9, Suit.HEART);
        add(cards, ++id, CardType.WU_ZHONG, 9, Suit.HEART);
        add(cards, ++id, CardType.SHAN, 9, Suit.HEART);
        // 10: 普通杀, 普通杀, 火杀
        add(cards, ++id, CardType.SHA, 10, Suit.HEART);
        add(cards, ++id, CardType.SHA, 10, Suit.HEART);
        add(cards, ++id, CardType.SHA, 10, Suit.HEART, Nature.FIRE);
        // J(11): 普通杀, 无中生有, 闪
        add(cards, ++id, CardType.SHA, 11, Suit.HEART);
        add(cards, ++id, CardType.WU_ZHONG, 11, Suit.HEART);
        add(cards, ++id, CardType.SHAN, 11, Suit.HEART);
        // Q(12): 桃, 过河拆桥, 闪电(EX), 闪
        add(cards, ++id, CardType.TAO, 12, Suit.HEART);
        add(cards, ++id, CardType.GUO_HE, 12, Suit.HEART);
        add(cards, ++id, CardType.SHAN_DIAN, 12, Suit.HEART);
        add(cards, ++id, CardType.SHAN, 12, Suit.HEART);
        // K(13): 闪, 爪黄飞电, 无懈可击
        add(cards, ++id, CardType.SHAN, 13, Suit.HEART);
        add(cards, ++id, CardType.ZHAO_YUN, 13, Suit.HEART);
        add(cards, ++id, CardType.WU_XIE, 13, Suit.HEART);

        // ==================== ♦ 方片 (40张) ====================
        // A: 诸葛连弩, 决斗, 朱雀羽扇
        add(cards, ++id, CardType.ZHUGE_LIAN_NU, 1, Suit.DIAMOND);
        add(cards, ++id, CardType.JUE_DOU, 1, Suit.DIAMOND);
        add(cards, ++id, CardType.ZHU_QUE, 1, Suit.DIAMOND);
        // 2: 闪, 闪, 桃
        add(cards, ++id, CardType.SHAN, 2, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 2, Suit.DIAMOND);
        add(cards, ++id, CardType.TAO, 2, Suit.DIAMOND);
        // 3: 闪, 顺手牵羊, 桃
        add(cards, ++id, CardType.SHAN, 3, Suit.DIAMOND);
        add(cards, ++id, CardType.SHUN_SHOU, 3, Suit.DIAMOND);
        add(cards, ++id, CardType.TAO, 3, Suit.DIAMOND);
        // 4: 闪, 顺手牵羊, 火杀
        add(cards, ++id, CardType.SHAN, 4, Suit.DIAMOND);
        add(cards, ++id, CardType.SHUN_SHOU, 4, Suit.DIAMOND);
        add(cards, ++id, CardType.SHA, 4, Suit.DIAMOND, Nature.FIRE);
        // 5: 闪, 贯石斧, 火杀
        add(cards, ++id, CardType.SHAN, 5, Suit.DIAMOND);
        add(cards, ++id, CardType.GUAN_SHI, 5, Suit.DIAMOND);
        add(cards, ++id, CardType.SHA, 5, Suit.DIAMOND, Nature.FIRE);
        // 6: 普通杀, 闪, 闪
        add(cards, ++id, CardType.SHA, 6, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 6, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 6, Suit.DIAMOND);
        // 7: 普通杀, 闪, 闪
        add(cards, ++id, CardType.SHA, 7, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 7, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 7, Suit.DIAMOND);
        // 8: 普通杀, 闪, 闪
        add(cards, ++id, CardType.SHA, 8, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 8, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 8, Suit.DIAMOND);
        // 9: 普通杀, 闪, 酒
        add(cards, ++id, CardType.SHA, 9, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 9, Suit.DIAMOND);
        add(cards, ++id, CardType.JIU, 9, Suit.DIAMOND);
        // 10: 普通杀, 闪, 闪
        add(cards, ++id, CardType.SHA, 10, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 10, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 10, Suit.DIAMOND);
        // J(11): 闪, 闪, 闪
        add(cards, ++id, CardType.SHAN, 11, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 11, Suit.DIAMOND);
        add(cards, ++id, CardType.SHAN, 11, Suit.DIAMOND);
        // Q(12): 桃, 方天画戟, 无懈可击(EX), 火攻
        add(cards, ++id, CardType.TAO, 12, Suit.DIAMOND);
        add(cards, ++id, CardType.FANG_TIAN, 12, Suit.DIAMOND);
        add(cards, ++id, CardType.WU_XIE, 12, Suit.DIAMOND);
        add(cards, ++id, CardType.HUO_GONG, 12, Suit.DIAMOND);
        // K(13): 普通杀, 紫骍, 骅骝
        add(cards, ++id, CardType.SHA, 13, Suit.DIAMOND);
        add(cards, ++id, CardType.ZI_XING, 13, Suit.DIAMOND);
        add(cards, ++id, CardType.HUA_LIU, 13, Suit.DIAMOND);

        // ==================== ♣ 梅花 (40张) ====================
        // A: 诸葛连弩, 决斗, 白银狮子
        add(cards, ++id, CardType.ZHUGE_LIAN_NU, 1, Suit.CLUB);
        add(cards, ++id, CardType.JUE_DOU, 1, Suit.CLUB);
        add(cards, ++id, CardType.BAI_YIN, 1, Suit.CLUB);
        // 2: 普通杀, 八卦阵, 仁王盾(EX), 藤甲
        add(cards, ++id, CardType.SHA, 2, Suit.CLUB);
        add(cards, ++id, CardType.BA_GUA, 2, Suit.CLUB);
        add(cards, ++id, CardType.REN_WANG, 2, Suit.CLUB);
        add(cards, ++id, CardType.TENG_JIA, 2, Suit.CLUB);
        // 3: 普通杀, 过河拆桥, 酒
        add(cards, ++id, CardType.SHA, 3, Suit.CLUB);
        add(cards, ++id, CardType.GUO_HE, 3, Suit.CLUB);
        add(cards, ++id, CardType.JIU, 3, Suit.CLUB);
        // 4: 普通杀, 过河拆桥, 兵粮寸断
        add(cards, ++id, CardType.SHA, 4, Suit.CLUB);
        add(cards, ++id, CardType.GUO_HE, 4, Suit.CLUB);
        add(cards, ++id, CardType.BING_LIANG, 4, Suit.CLUB);
        // 5: 的卢, 普通杀, 雷杀
        add(cards, ++id, CardType.DI_LU, 5, Suit.CLUB);
        add(cards, ++id, CardType.SHA, 5, Suit.CLUB);
        add(cards, ++id, CardType.SHA, 5, Suit.CLUB, Nature.THUNDER);
        // 6: 普通杀, 乐不思蜀, 雷杀
        add(cards, ++id, CardType.SHA, 6, Suit.CLUB);
        add(cards, ++id, CardType.LE_BU, 6, Suit.CLUB);
        add(cards, ++id, CardType.SHA, 6, Suit.CLUB, Nature.THUNDER);
        // 7: 普通杀, 南蛮入侵, 雷杀
        add(cards, ++id, CardType.SHA, 7, Suit.CLUB);
        add(cards, ++id, CardType.NAN_MAN, 7, Suit.CLUB);
        add(cards, ++id, CardType.SHA, 7, Suit.CLUB, Nature.THUNDER);
        // 8: 普通杀, 普通杀, 雷杀
        add(cards, ++id, CardType.SHA, 8, Suit.CLUB);
        add(cards, ++id, CardType.SHA, 8, Suit.CLUB);
        add(cards, ++id, CardType.SHA, 8, Suit.CLUB, Nature.THUNDER);
        // 9: 普通杀, 普通杀, 酒
        add(cards, ++id, CardType.SHA, 9, Suit.CLUB);
        add(cards, ++id, CardType.SHA, 9, Suit.CLUB);
        add(cards, ++id, CardType.JIU, 9, Suit.CLUB);
        // 10: 普通杀, 普通杀, 铁索连环
        add(cards, ++id, CardType.SHA, 10, Suit.CLUB);
        add(cards, ++id, CardType.SHA, 10, Suit.CLUB);
        add(cards, ++id, CardType.TIE_SUO, 10, Suit.CLUB);
        // J(11): 普通杀, 普通杀, 铁索连环
        add(cards, ++id, CardType.SHA, 11, Suit.CLUB);
        add(cards, ++id, CardType.SHA, 11, Suit.CLUB);
        add(cards, ++id, CardType.TIE_SUO, 11, Suit.CLUB);
        // Q(12): 借刀杀人, 无懈可击, 铁索连环
        add(cards, ++id, CardType.JIE_DAO, 12, Suit.CLUB);
        add(cards, ++id, CardType.WU_XIE, 12, Suit.CLUB);
        add(cards, ++id, CardType.TIE_SUO, 12, Suit.CLUB);
        // K(13): 借刀杀人, 无懈可击, 铁索连环
        add(cards, ++id, CardType.JIE_DAO, 13, Suit.CLUB);
        add(cards, ++id, CardType.WU_XIE, 13, Suit.CLUB);
        add(cards, ++id, CardType.TIE_SUO, 13, Suit.CLUB);

        // ==================== ♠ 黑桃 (40张) ====================
        // A: 闪电, 决斗, 古锭刀
        add(cards, ++id, CardType.SHAN_DIAN, 1, Suit.SPADE);
        add(cards, ++id, CardType.JUE_DOU, 1, Suit.SPADE);
        add(cards, ++id, CardType.GU_DING_DAO, 1, Suit.SPADE);
        // 2: 雌雄双股剑, 八卦阵, 寒冰剑(EX), 藤甲
        add(cards, ++id, CardType.CI_XIONG, 2, Suit.SPADE);
        add(cards, ++id, CardType.BA_GUA, 2, Suit.SPADE);
        add(cards, ++id, CardType.HAN_BING, 2, Suit.SPADE);
        add(cards, ++id, CardType.TENG_JIA, 2, Suit.SPADE);
        // 3: 顺手牵羊, 过河拆桥, 酒
        add(cards, ++id, CardType.SHUN_SHOU, 3, Suit.SPADE);
        add(cards, ++id, CardType.GUO_HE, 3, Suit.SPADE);
        add(cards, ++id, CardType.JIU, 3, Suit.SPADE);
        // 4: 顺手牵羊, 过河拆桥, 雷杀
        add(cards, ++id, CardType.SHUN_SHOU, 4, Suit.SPADE);
        add(cards, ++id, CardType.GUO_HE, 4, Suit.SPADE);
        add(cards, ++id, CardType.SHA, 4, Suit.SPADE, Nature.THUNDER);
        // 5: 绝影, 青龙偃月刀, 雷杀
        add(cards, ++id, CardType.JUE_YING, 5, Suit.SPADE);
        add(cards, ++id, CardType.QING_LONG, 5, Suit.SPADE);
        add(cards, ++id, CardType.SHA, 5, Suit.SPADE, Nature.THUNDER);
        // 6: 青釭剑, 乐不思蜀, 雷杀
        add(cards, ++id, CardType.QING_GANG, 6, Suit.SPADE);
        add(cards, ++id, CardType.LE_BU, 6, Suit.SPADE);
        add(cards, ++id, CardType.SHA, 6, Suit.SPADE, Nature.THUNDER);
        // 7: 普通杀, 南蛮入侵, 雷杀
        add(cards, ++id, CardType.SHA, 7, Suit.SPADE);
        add(cards, ++id, CardType.NAN_MAN, 7, Suit.SPADE);
        add(cards, ++id, CardType.SHA, 7, Suit.SPADE, Nature.THUNDER);
        // 8: 普通杀, 普通杀, 雷杀
        add(cards, ++id, CardType.SHA, 8, Suit.SPADE);
        add(cards, ++id, CardType.SHA, 8, Suit.SPADE);
        add(cards, ++id, CardType.SHA, 8, Suit.SPADE, Nature.THUNDER);
        // 9: 普通杀, 普通杀, 酒
        add(cards, ++id, CardType.SHA, 9, Suit.SPADE);
        add(cards, ++id, CardType.SHA, 9, Suit.SPADE);
        add(cards, ++id, CardType.JIU, 9, Suit.SPADE);
        // 10: 普通杀, 普通杀, 兵粮寸断
        add(cards, ++id, CardType.SHA, 10, Suit.SPADE);
        add(cards, ++id, CardType.SHA, 10, Suit.SPADE);
        add(cards, ++id, CardType.BING_LIANG, 10, Suit.SPADE);
        // J(11): 顺手牵羊, 无懈可击, 铁索连环
        add(cards, ++id, CardType.SHUN_SHOU, 11, Suit.SPADE);
        add(cards, ++id, CardType.WU_XIE, 11, Suit.SPADE);
        add(cards, ++id, CardType.TIE_SUO, 11, Suit.SPADE);
        // Q(12): 丈八蛇矛, 过河拆桥, 铁索连环
        add(cards, ++id, CardType.ZHANG_BA, 12, Suit.SPADE);
        add(cards, ++id, CardType.GUO_HE, 12, Suit.SPADE);
        add(cards, ++id, CardType.TIE_SUO, 12, Suit.SPADE);
        // K(13): 南蛮入侵, 大宛, 无懈可击
        add(cards, ++id, CardType.NAN_MAN, 13, Suit.SPADE);
        add(cards, ++id, CardType.DA_WAN, 13, Suit.SPADE);
        add(cards, ++id, CardType.WU_XIE, 13, Suit.SPADE);

        logDeckStats(cards);
        return cards;
    }

    private static void add(List<GameCard> list, int id, CardType type, int number, Suit suit) {
        list.add(new GameCard(String.valueOf(id), type, suit, number));
    }

    private static void add(List<GameCard> list, int id, CardType type, int number, Suit suit, Nature nature) {
        list.add(new GameCard(String.valueOf(id), type, suit, number, nature));
    }

    /**
     * 打印牌堆统计信息
     */
    public static void logDeckStats(List<GameCard> cards) {
        System.out.println("========== 牌堆统计 ==========");
        System.out.println("总张数: " + cards.size());

        // 按花色统计
        Map<Suit, Long> suitCounts = cards.stream()
                .collect(Collectors.groupingBy(GameCard::getSuit, Collectors.counting()));
        System.out.println("--- 按花色 ---");
        suitCounts.forEach((suit, cnt) -> {
            String sym = suit.getSymbol();
            System.out.println("  " + sym + " " + cnt + "张");
        });

        // 按类别统计
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

        // 基本牌明细
        long shanCount = cards.stream().filter(c -> c.getCardType() == CardType.SHAN).count();
        long taoCount = cards.stream().filter(c -> c.getCardType() == CardType.TAO).count();
        long jiuCount = cards.stream().filter(c -> c.getCardType() == CardType.JIU).count();
        System.out.println("--- 基本牌明细 ---");
        System.out.println("  闪: " + shanCount);
        System.out.println("  桃: " + taoCount);
        System.out.println("  酒: " + jiuCount);

        // 每张牌 + 校验
        System.out.println("--- 每张牌(id 花色 点数 牌名 CardType Nature) ---");
        Set<String> seenIds = new HashSet<>();
        boolean dupId = false;
        for (int i = 0; i < cards.size(); i++) {
            GameCard c = cards.get(i);
            String natureStr = "NORMAL";
            if (c.getNature() == Nature.FIRE) natureStr = "FIRE";
            else if (c.getNature() == Nature.THUNDER) natureStr = "THUNDER";
            System.out.printf("  %3s %s%-2s %-8s %-15s %s%n",
                    c.getId(), c.getSuit().getSymbol(), numDisplay(c.getNumber()),
                    c.getCardType().getDisplayName(), c.getCardType(), natureStr);
            if (!seenIds.add(c.getId())) {
                System.out.println("  ⚠ 重复id: " + c.getId());
                dupId = true;
            }
        }

        // 校验
        System.out.println("--- 校验 ---");
        long total = cards.size();
        System.out.println("  总数160: " + (total == 160 ? "✓" : "✗ (" + total + ")"));
        System.out.println("  各花色40: " + (suitCounts.values().stream().allMatch(c -> c == 40) ? "✓" : "✗"));
        suitCounts.forEach((s, c) -> System.out.println("    " + s.getSymbol() + ": " + c + (c == 40 ? " ✓" : " ✗")));

        long basicCount = cards.stream().filter(c -> c.getCardType().isBasic()).count();
        long trickCount = cards.stream().filter(c -> c.getCardType().isTrick() || c.getCardType().isDelayTrick()).count();
        long equipCount = cards.stream().filter(c -> c.getCardType().isEquipment()).count();
        System.out.println("  基本牌85: " + (basicCount == 85 ? "✓" : "✗ (" + basicCount + ")"));
        System.out.println("  锦囊牌50: " + (trickCount == 50 ? "✓" : "✗ (" + trickCount + ")"));
        System.out.println("  装备牌25: " + (equipCount == 25 ? "✓" : "✗ (" + equipCount + ")"));

        System.out.println("  普通杀30: " + (normalSha == 30 ? "✓" : "✗ (" + normalSha + ")"));
        System.out.println("  火杀5: " + (fireSha == 5 ? "✓" : "✗ (" + fireSha + ")"));
        System.out.println("  雷杀9: " + (thunderSha == 9 ? "✓" : "✗ (" + thunderSha + ")"));
        System.out.println("  闪24: " + (shanCount == 24 ? "✓" : "✗ (" + shanCount + ")"));
        System.out.println("  桃12: " + (taoCount == 12 ? "✓" : "✗ (" + taoCount + ")"));
        System.out.println("  酒5: " + (jiuCount == 5 ? "✓" : "✗ (" + jiuCount + ")"));

        System.out.println("  重复id: " + (dupId ? "⚠ 有重复" : "✓ 无重复"));

        System.out.println("--- 判定规则检查 ---");
        // 八卦阵: 红色视为闪（牌本身花色不受影响，检查有没有八卦阵卡牌）
        boolean bgHeart = cards.stream().anyMatch(c -> c.getCardType() == CardType.BA_GUA && c.getSuit() == Suit.HEART);
        boolean bgDiamond = cards.stream().anyMatch(c -> c.getCardType() == CardType.BA_GUA && c.getSuit() == Suit.DIAMOND);
        // 八卦阵应该在黑色花色（♣2 ♠2），而非红色
        System.out.println("  八卦阵♣/♠（正确应为黑色）: 梅花=" + cards.stream().anyMatch(c -> c.getCardType() == CardType.BA_GUA && c.getSuit() == Suit.CLUB)
                + " 黑桃=" + cards.stream().anyMatch(c -> c.getCardType() == CardType.BA_GUA && c.getSuit() == Suit.SPADE)
                + (bgHeart || bgDiamond ? " ⚠ 有红色八卦阵!" : " ✓"));
        // 仁王盾♣2
        System.out.println("  仁王盾♣2: " + (cards.stream().anyMatch(c -> c.getCardType() == CardType.REN_WANG && c.getSuit() == Suit.CLUB && c.getNumber() == 2) ? "✓" : "✗"));
        // 火攻♥2 ♥3 ♦Q
        System.out.println("  火攻于♥2/♥3/♦Q: " + (
                cards.stream().anyMatch(c -> c.getCardType() == CardType.HUO_GONG && c.getSuit() == Suit.HEART && c.getNumber() == 2) &&
                cards.stream().anyMatch(c -> c.getCardType() == CardType.HUO_GONG && c.getSuit() == Suit.HEART && c.getNumber() == 3) &&
                cards.stream().anyMatch(c -> c.getCardType() == CardType.HUO_GONG && c.getSuit() == Suit.DIAMOND && c.getNumber() == 12)
                ? "✓" : "✗"));
        // 闪电♠A
        System.out.println("  闪电♠A: " + (cards.stream().anyMatch(c -> c.getCardType() == CardType.SHAN_DIAN && c.getSuit() == Suit.SPADE && c.getNumber() == 1) ? "✓" : "✗"));
        // 乐不思蜀♥6 ♠6 ♣6
        System.out.println("  乐不思蜀♥6/♠6/♣6: " + (
                cards.stream().anyMatch(c -> c.getCardType() == CardType.LE_BU && c.getSuit() == Suit.HEART && c.getNumber() == 6) &&
                cards.stream().anyMatch(c -> c.getCardType() == CardType.LE_BU && c.getSuit() == Suit.SPADE && c.getNumber() == 6) &&
                cards.stream().anyMatch(c -> c.getCardType() == CardType.LE_BU && c.getSuit() == Suit.CLUB && c.getNumber() == 6)
                ? "✓" : "✗"));
        // 兵粮寸断♣4 ♠10
        System.out.println("  兵粮寸断♣4/♠10: " + (
                cards.stream().anyMatch(c -> c.getCardType() == CardType.BING_LIANG && c.getSuit() == Suit.CLUB && c.getNumber() == 4) &&
                cards.stream().anyMatch(c -> c.getCardType() == CardType.BING_LIANG && c.getSuit() == Suit.SPADE && c.getNumber() == 10)
                ? "✓" : "✗"));
        // 火杀nature校验：5张火杀都必须Nature.FIRE
        long fireShaWithFireNature = cards.stream()
                .filter(c -> c.getCardType() == CardType.SHA && c.getNature() == Nature.FIRE).count();
        System.out.println("  火杀Nature.FIRE: " + (fireShaWithFireNature == 5 ? "✓" : "✗ (" + fireShaWithFireNature + ")"));
        // 雷杀nature校验
        long thunderShaWithThunderNature = cards.stream()
                .filter(c -> c.getCardType() == CardType.SHA && c.getNature() == Nature.THUNDER).count();
        System.out.println("  雷杀Nature.THUNDER: " + (thunderShaWithThunderNature == 9 ? "✓" : "✗ (" + thunderShaWithThunderNature + ")"));
        // isShaLike：火杀/雷杀都是CardType.SHA
        long totalSha = cards.stream().filter(c -> c.getCardType() == CardType.SHA).count();
        System.out.println("  isShaLike(所有杀CardType.SHA): " + (totalSha == 44 ? "✓" : "✗ (" + totalSha + ")"));

        System.out.println("================================");
    }

    private static String numDisplay(int n) {
        return switch (n) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> String.valueOf(n);
        };
    }

}
