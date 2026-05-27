package com.sanguosha.game.card;

/**
 * 卡牌类型枚举
 */
public enum CardType {
    // 基本牌
    SHA("杀", "基本牌", "对一名角色造成1点伤害"),
    SHAN("闪", "基本牌", "抵消杀"),
    TAO("桃", "基本牌", "回复1点体力"),
    JIU("酒", "基本牌", "下一张杀伤害+1"),

    // 锦囊牌
    JUE_DOU("决斗", "锦囊牌", "与目标角色决斗"),
    NAN_MAN("南蛮入侵", "锦囊牌", "所有其他角色需打出杀"),
    WAN_JIAN("万箭齐发", "锦囊牌", "所有其他角色需打出闪"),
    TAO_YUAN("桃园结义", "锦囊牌", "所有角色回复1点体力"),
    WU_GU("五谷丰登", "锦囊牌", "展示牌堆顶等同于角色数的牌"),
    GUO_HE("过河拆桥", "锦囊牌", "弃置目标一张牌"),
    SHUN_SHOU("顺手牵羊", "锦囊牌", "获得目标一张牌"),
    WU_ZHONG("无中生有", "锦囊牌", "摸两张牌"),
    JIE_DAO("借刀杀人", "锦囊牌", "令目标使用杀"),
    WU_XIE("无懈可击", "锦囊牌", "抵消一张锦囊牌"),
    HUO_GONG("火攻", "锦囊牌", "目标展示一张手牌，花色相同则受1点火伤害"),
    TIE_SUO("铁索连环", "锦囊牌", "横置或重置一名角色"),
    WU_GU_SHU("五谷丰登·束", "锦囊牌", "五谷丰登的牌"),

    // 延时锦囊
    LE_BU("乐不思蜀", "延时锦囊", "判定不为红桃则跳过出牌阶段"),
    BING_LIANG("兵粮寸断", "延时锦囊", "判定不为草花则跳过摸牌阶段"),
    SHAN_DIAN("闪电", "延时锦囊", "判定为黑桃2-9则受到3点雷电伤害"),

    // 装备牌 - 武器
    QING_LONG("青龙偃月刀", "武器", "攻击范围3, 杀被闪后可追刀"),
    ZHANG_BA("丈八蛇矛", "武器", "攻击范围3, 可用两张牌当杀"),
    GUAN_SHI("贯石斧", "武器", "攻击范围3, 杀被闪后可强制命中"),
    FANG_TIAN("方天画戟", "武器", "攻击范围4, 最后一手牌杀可额外指定目标"),
    QI_LIN("麒麟弓", "武器", "攻击范围5, 杀中马目标弃马"),
    HAN_BING("寒冰剑", "武器", "攻击范围2, 杀中可改为弃牌"),
    QING_GANG("青釭剑", "武器", "攻击范围2, 无视防具"),
    CI_XIONG("雌雄双股剑", "武器", "攻击范围2, 杀异性目标可令其弃牌或摸牌"),
    GU_DING_DAO("古锭刀", "武器", "攻击范围2, 目标无手牌时伤害+1"),
    ZHU_QUE("朱雀羽扇", "武器", "攻击范围4, 可将普通杀转为火杀"),
    ZHUGE_LIAN_NU("诸葛连弩", "武器", "攻击范围1, 可使用任意张杀"),

    // 装备牌 - 防具
    BA_GUA("八卦阵", "防具", "判定为红色视为闪"),
    REN_WANG("仁王盾", "防具", "黑色杀无效"),
    TENG_JIA("藤甲", "防具", "普通杀和南蛮万箭无效, 火伤+1"),
    BAI_YIN("白银狮子", "防具", "受到伤害最多为1, 失去时回血"),

    // 装备牌 - 坐骑
    CHI_TU("赤兔", "-1马", "计算其他角色距离-1"),
    DA_WAN("大宛", "-1马", "计算其他角色距离-1"),
    ZI_XING("紫骍", "-1马", "计算其他角色距离-1"),
    DI_LU("的卢", "+1马", "其他角色计算距离+1"),
    ZHAO_YUN("爪黄飞电", "+1马", "其他角色计算距离+1"),
    JUE_YING("绝影", "+1马", "其他角色计算距离+1"),
    HUA_LIU("骅骝", "+1马", "其他角色计算距离+1");

    private final String displayName;
    private final String category;
    private final String description;

    CardType(String displayName, String category, String description) {
        this.displayName = displayName;
        this.category = category;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }

    public boolean isBasic() { return "基本牌".equals(category); }
    public boolean isTrick() { return "锦囊牌".equals(category); }
    public boolean isDelayTrick() { return "延时锦囊".equals(category); }
    public boolean isWeapon() { return "武器".equals(category); }
    public boolean isArmor() { return "防具".equals(category); }
    public boolean isPlusHorse() { return "+1马".equals(category); }
    public boolean isMinusHorse() { return "-1马".equals(category); }
    public boolean isEquipment() {
        return isWeapon() || isArmor() || isPlusHorse() || isMinusHorse();
    }
}
