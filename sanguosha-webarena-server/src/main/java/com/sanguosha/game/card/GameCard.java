package com.sanguosha.game.card;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一张具体的游戏卡牌（包含花色、点数、类型）
 */
@Data
@AllArgsConstructor
public class GameCard {
    private String id;           // 唯一标识
    private CardType cardType;   // 卡牌类型
    private Suit suit;           // 花色
    private int number;          // 点数 (1-13, A=1, J=11, Q=12, K=13)

    public enum Suit {
        SPADE("♠"),     // 黑桃
        HEART("♥"),     // 红桃
        CLUB("♣"),      // 草花
        DIAMOND("♦");   // 方块

        private final String symbol;
        Suit(String symbol) { this.symbol = symbol; }
        public String getSymbol() { return symbol; }
    }

    /**
     * 获取点数显示
     */
    public String getNumberDisplay() {
        return switch (number) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> String.valueOf(number);
        };
    }

    /**
     * 获取显示名称（含花色和点数）
     */
    public String getDisplayName() {
        return suit.getSymbol() + number + " " + cardType.getDisplayName();
    }

    /**
     * 判断是否为红色花色（红桃/方块）
     */
    public boolean isRed() {
        return suit == Suit.HEART || suit == Suit.DIAMOND;
    }

    /**
     * 判断是否为黑色花色（黑桃/草花）
     */
    public boolean isBlack() {
        return suit == Suit.SPADE || suit == Suit.CLUB;
    }
}