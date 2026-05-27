package com.sanguosha.game.engine;

import com.sanguosha.game.card.CardType;
import com.sanguosha.game.card.GameCard;
import com.sanguosha.game.card.effect.CardEffect;
import com.sanguosha.game.context.CardPool;
import com.sanguosha.game.event.GameEvent;
import com.sanguosha.game.event.GameEventType;
import com.sanguosha.game.skill.EquipmentSkillRegistry;
import com.sanguosha.game.player.GamePlayer;
import com.sanguosha.game.skill.EquipmentSkillRegistry;
import com.sanguosha.game.skill.SkillEffect;
import com.sanguosha.game.skill.SkillUseRequest;
import com.sanguosha.game.skill.TriggerEffect;
import com.sanguosha.game.state.GameAction;
import com.sanguosha.game.state.GameState;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1v1 游戏引擎 - 核心游戏逻辑
 */
@Slf4j
@Component
public class GameEngine {

    private final ConcurrentHashMap<String, GameState> games = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> roomGameIndex = new ConcurrentHashMap<>();

    // 卡牌效果注册表（策略模式）
    private final Map<CardType, CardEffect> basicEffects = new EnumMap<>(CardType.class);
    private final Map<CardType, CardEffect> trickEffects = new EnumMap<>(CardType.class);

    // 装备/技能系统
    private final EquipmentSkillRegistry skillRegistry = new EquipmentSkillRegistry();

    @PostConstruct
    private void initCardEffects() {
        // 基本牌
        basicEffects.put(CardType.SHA, this::useSha);
        basicEffects.put(CardType.TAO, this::useTao);
        basicEffects.put(CardType.JIU, this::useJiu);

        // 锦囊牌
        trickEffects.put(CardType.WU_ZHONG, this::useWuZhong);
        trickEffects.put(CardType.GUO_HE, this::useGuoHe);
        trickEffects.put(CardType.SHUN_SHOU, this::useShunShou);
        trickEffects.put(CardType.JUE_DOU, this::useJueDou);
        trickEffects.put(CardType.NAN_MAN, this::useNanMan);
        trickEffects.put(CardType.WAN_JIAN, this::useWanJian);
        trickEffects.put(CardType.TAO_YUAN, this::useTaoYuan);
        trickEffects.put(CardType.WU_GU, this::useWuGu);
        trickEffects.put(CardType.JIE_DAO, this::useJieDao);
        trickEffects.put(CardType.HUO_GONG, this::useHuoGong);
        trickEffects.put(CardType.TIE_SUO, this::useTieSuo);

        // 装备技能注册
        registerEquipmentSkills();
    }

    /**
     * 注册装备主动技能
     */
    private void registerEquipmentSkills() {
        // 丈八蛇矛：将两张手牌当杀使用或打出
        skillRegistry.registerSkill(new SkillEffect() {
            @Override
            public String getSkillCode() {
                return "ZHANG_BA_SHE_MAO";
            }

            @Override
            public boolean canUse(GameState state, GamePlayer player, SkillUseRequest request) {
                // 必须装备丈八蛇矛
                if (player.getWeapon() == null || player.getWeapon().getCardType() != CardType.ZHANG_BA) {
                    return false;
                }
                // 至少需要两张手牌
                if (player.getHandCards().size() < 2) {
                    return false;
                }
                if (request.isResponse()) {
                    // 响应模式：必须在 RESPOND_SHA 等待中
                    GameAction pending = state.getPendingAction();
                    if (pending == null) return false;
                    if (!"RESPOND_SHA".equals(pending.getActionType())) return false;
                    return pending.getOptionalTargetIds().contains(player.getUserId());
                } else {
                    // 出牌阶段主动使用
                    if (!"PLAY".equals(state.getPhase())) return false;
                    if (!state.getCurrentPlayer().getUserId().equals(player.getUserId())) return false;
                    if (request.getTargetUserId() == null) return false;
                    GamePlayer target = findPlayerById(state, Long.valueOf(request.getTargetUserId()));
                    return target != null && target.isAlive();
                }
            }

            @Override
            public GameEngine.ActionResult execute(GameState state, GamePlayer player, SkillUseRequest request) {
                return useZhangBaSkill(state, player, request);
            }
        });

        // 麒麟弓：杀命中后可选弃马
        skillRegistry.registerTrigger(GameEventType.DAMAGE_DONE, new TriggerEffect() {
            @Override
            public String getSkillCode() {
                return "QI_LIN_GONG";
            }

            @Override
            public boolean supports(GameEventType eventType) {
                return eventType == GameEventType.DAMAGE_DONE;
            }

            @Override
            public boolean canTrigger(GameState state, GameEvent event) {
                String effectType = event.getExtra() != null ? (String) event.getExtra().get("effectType") : null;
                if (!"SHA".equals(effectType)) return false;
                GamePlayer attacker = event.getSource();
                if (attacker == null || attacker.getWeapon() == null) return false;
                if (attacker.getWeapon().getCardType() != CardType.QI_LIN) return false;
                GamePlayer target = event.getTarget();
                if (target == null || !target.isAlive()) return false;
                return target.getPlusHorse() != null || target.getMinusHorse() != null;
            }

            @Override
            public GameEngine.ActionResult trigger(GameState state, GameEvent event) {
                GamePlayer attacker = event.getSource();
                GamePlayer target = event.getTarget();

                List<String> horseIds = new ArrayList<>();
                List<Map<String, Object>> horseInfos = new ArrayList<>();
                if (target.getPlusHorse() != null) {
                    horseIds.add(target.getPlusHorse().getId());
                    horseInfos.add(cardToClientMap(target.getPlusHorse()));
                }
                if (target.getMinusHorse() != null) {
                    horseIds.add(target.getMinusHorse().getId());
                    horseInfos.add(cardToClientMap(target.getMinusHorse()));
                }

                GameAction action = new GameAction();
                action.setActionType("WAIT_EQUIP_TRIGGER");
                action.setSourcePlayerId(attacker.getUserId());
                action.setOptionalCardIds(horseIds);
                action.setOptionalCards(horseInfos);
                action.setOptionalTargetIds(Collections.singletonList(attacker.getUserId()));
                action.setMessage("是否发动麒麟弓弃置 " + target.getUsername() + " 的一匹马？");
                action.setExtraData(Map.of(
                        "skillCode", "QI_LIN_GONG",
                        "targetId", target.getUserId()
                ));

                state.setPendingAction(action);
                return new GameEngine.ActionResult(true, "麒麟弓触发", action);
            }
        });
    }

    /**
     * 获取游戏状态
     */
    public GameState getGame(String gameId) {
        return games.get(gameId);
    }

    /**
     * 通过房间ID获取游戏
     */
    public GameState getGameByRoomId(String roomId) {
        String gameId = roomGameIndex.get(roomId);
        return gameId != null ? games.get(gameId) : null;
    }

    /**
     * 通过用户ID查找正在进行的游戏
     */
    public GameState findGameByUserId(Long userId) {
        for (GameState state : games.values()) {
            if (state.isFinished()) continue;
            for (var player : state.getPlayers()) {
                if (player.getUserId().equals(userId)) {
                    return state;
                }
            }
        }
        return null;
    }

    /**
     * 从游戏中移除指定用户（投降/断线超时）
     * @return 被移除用户的对手ID，如果双方都已不在则返回 null
     */
    public Long removePlayerFromGame(String gameId, Long userId) {
        GameState state = games.get(gameId);
        if (state == null) return null;

        var opponent = state.getPlayers().stream()
                .filter(p -> !p.getUserId().equals(userId))
                .findFirst().orElse(null);
        return opponent != null ? opponent.getUserId() : null;
    }

    /**
     * 移除游戏并清理关联索引
     */
    public void removeGame(String gameId) {
        GameState state = games.remove(gameId);
        if (state != null && state.getRoomId() != null) {
            roomGameIndex.remove(state.getRoomId());
            log.info("游戏 {} (房间 {}) 已从引擎中移除", gameId, state.getRoomId());
        }
    }

    /**
     * 获取装备技能注册表（用于外部注册技能）
     */
    public EquipmentSkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    /**
     * 创建并启动游戏
     */
    public GameState startGame(String roomId, String gameId,
                               Long player1Id, String player1Name,
                               Long player2Id, String player2Name) {
        GameState state = new GameState(gameId, roomId);

        GamePlayer p1 = new GamePlayer(player1Id, player1Name, 0);
        GamePlayer p2 = new GamePlayer(player2Id, player2Name, 1);

        state.getPlayers().add(p1);
        state.getPlayers().add(p2);

        // 初始化牌堆
        state.initDeck(CardPool.initDeck());
        state.addLog("牌堆已初始化，共 " + state.getDrawPile().size() + " 张牌");

        // 先手摸4张，后手摸4张
        p1.drawCards(state.drawCards(4));
        p2.drawCards(state.drawCards(4));
        state.addLog("双方各摸4张牌");

        state.setStarted(true);
        state.setPhase("PREPARE");
        state.addLog("游戏开始！" + player1Name + " vs " + player2Name);
        state.addLog(player1Name + " 先手");

        games.put(gameId, state);
        roomGameIndex.put(roomId, gameId);
        return state;
    }

    /**
     * 执行回合阶段流程
     * 返回需要发送给客户端的action（如果有），否则返回null
     */
    public GameAction processPhase(GameState state) {
        GamePlayer current = state.getCurrentPlayer();
        String phase = state.getPhase();

        // 触发阶段开始事件
        fireEvent(new GameEvent(GameEventType.PHASE_START, state, current, null, null, 0,
                Map.of("phase", phase)));

        switch (phase) {
            case "PREPARE" -> {
                state.addLog(current.getUsername() + " 的准备阶段");
                state.nextPhase();
                return processPhase(state);
            }
            case "JUDGE" -> {
                state.addLog(current.getUsername() + " 的判定阶段");
                // 处理延时锦囊判定：逐个判定
                while (!current.getJudgeArea().isEmpty()) {
                    GameCard judgeCard = current.getJudgeArea().get(0);
                    current.getJudgeArea().remove(0);
                    state.getTempCards().clear();
                    state.getTempCards().add(judgeCard);

                    // 判定前打开无懈可击响应窗口
                    boolean anyHasWuxie = state.getAlivePlayers().stream()
                            .anyMatch(p -> p.getHandCards().stream().anyMatch(c -> c.getCardType() == CardType.WU_XIE));

                    if (anyHasWuxie) {
                        GameAction action = new GameAction();
                        action.setActionType("WAIT_WUXIE_RESPONSE");
                        action.setSourcePlayerId(current.getUserId());
                        action.setOptionalCardIds(Collections.emptyList());
                        action.setOptionalCards(Collections.emptyList());
                        action.setOptionalTargetIds(state.getAlivePlayers().stream()
                                .map(GamePlayer::getUserId).toList());
                        action.setMessage("是否使用无懈可击抵消" + judgeCard.getCardType().getDisplayName() + "？");

                        Map<String, Object> extraData = new HashMap<>();
                        extraData.put("trickCardId", judgeCard.getId());
                        extraData.put("trickCardType", judgeCard.getCardType().name());
                        extraData.put("sourcePlayerId", current.getUserId());
                        extraData.put("originalTargetUserId", String.valueOf(current.getUserId()));
                        extraData.put("originalTargetCardId", null);
                        extraData.put("wuxieStack", new ArrayList<String>());
                        extraData.put("respondedSkipIds", new ArrayList<Long>());
                        extraData.put("isDelayTrickJudgment", true);
                        action.setExtraData(extraData);

                        return action;
                    }

                    // 无无懈可击，直接判定
                    applyDelayTrickEffect(state, current, judgeCard);
                    state.getTempCards().clear();

                    // 如果濒死pending action已设置，暂停继续判定
                    if (state.getPendingAction() != null) return null;
                }
                state.nextPhase();
                return processPhase(state);
            }
            case "DRAW" -> {
                // 兵粮寸断判定：跳过摸牌阶段
                if (current.isSkipDrawPhase()) {
                    current.setSkipDrawPhase(false);
                    state.addLog("兵粮寸断生效，" + current.getUsername() + "跳过摸牌阶段");
                } else {
                    // 摸牌阶段 - 摸2张
                    List<GameCard> drawn = state.drawCards(2);
                    if (drawn.isEmpty()) {
                        state.addLog("牌堆已空，" + current.getUsername() + " 无法摸牌");
                    } else {
                        current.drawCards(drawn);
                        state.addLog(current.getUsername() + " 摸了 " + drawn.size() + " 张牌");
                    }
                }
                state.nextPhase();
                return processPhase(state);
            }
            case "PLAY" -> {
                // 乐不思蜀判定：跳过出牌阶段
                if (current.isSkipPlayPhase()) {
                    current.setSkipPlayPhase(false);
                    state.addLog("乐不思蜀生效，" + current.getUsername() + "跳过出牌阶段");
                    state.nextPhase();
                    return processPhase(state);
                }
                state.addLog(current.getUsername() + " 的出牌阶段");
                current.resetTurnState();
                return null;
            }
            case "DISCARD" -> {
                // 弃牌阶段
                int discardCount = current.getDiscardCount();
                if (discardCount > 0) {
                    state.addLog(current.getUsername() + " 需要弃 " + discardCount + " 张牌");

                    GameAction action = new GameAction();
                    action.setActionType("DISCARD");
                    action.setSourcePlayerId(current.getUserId());
                    action.setDiscardCount(discardCount);
                    action.setMessage("请弃置 " + discardCount + " 张手牌");

                    List<String> cardIds = current.getHandCards().stream()
                            .map(GameCard::getId).toList();
                    action.setOptionalCardIds(cardIds);
                    action.setOptionalCards(cardIdsToClientMap(state, cardIds));
                    action.setOptionalTargetIds(Collections.singletonList(current.getUserId()));

                    state.setPendingAction(action);
                    return action;
                }
                state.nextPhase();
                return processPhase(state);
            }
            case "END" -> {
                state.addLog(current.getUsername() + " 的回合结束");
                state.nextTurn();
                // 新回合从PREPARE开始
                return processPhase(state);
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * 玩家出牌 - 使用卡牌效果注册表分发
     */
    public ActionResult playCard(GameState state, Long userId, String cardId,
                                  String targetUserId, String targetCardId) {
        GamePlayer player = findPlayer(state, userId);
        if (player == null) return failure("未找到玩家");

        if (!state.getCurrentPlayer().getUserId().equals(userId)) {
            return failure("不是你的回合");
        }

        if (!"PLAY".equals(state.getPhase())) {
            return failure("不在出牌阶段");
        }

        GameCard card = findCard(player, cardId);
        if (card == null) return failure("未找到该卡牌");

        CardType type = card.getCardType();

        // 对敌方使用的牌不能以自己为目标
        if (targetUserId != null && isOffensiveCard(type)) {
            if (String.valueOf(userId).equals(targetUserId)) {
                return failure("不能对自己使用该牌");
            }
        }

        // 策略模式分发
        CardEffect effect = null;
        if (type.isBasic()) {
            effect = basicEffects.get(type);
        } else if (type.isTrick()) {
            effect = trickEffects.get(type);
        } else if (type.isEquipment()) {
            effect = this::playEquipmentCard;
        } else if (type.isDelayTrick()) {
            effect = this::playDelayTrick;
        }

        if (effect == null) {
            return failure("不支持的卡牌类型");
        }

        // 铁索连环重铸（无目标时）：不触发无懈可击
        if (type == CardType.TIE_SUO && (targetUserId == null || targetUserId.isEmpty())) {
            player.removeHandCard(card.getId());
            state.discardCard(card);
            List<GameCard> drawn = state.drawCards(1);
            if (!drawn.isEmpty()) {
                player.drawCards(drawn);
                state.addLog(player.getUsername() + " 重铸铁索连环，摸了1张牌");
            }
            return success("铁索连环重铸成功");
        }

        // 无懈可击拦截：锦囊牌使用时触发响应链
        if (type.isTrick()) {
            return playTrickWithWuxieCheck(state, player, card, type, effect, targetUserId, targetCardId);
        }

        ActionResult result = effect.execute(state, player, card, targetUserId, targetCardId);
        if (result.success()) {
            fireEvent(new GameEvent(GameEventType.CARD_USED, state, player, null, card, 0, null));
        }
        return result;
    }

    /**
     * 使用杀
     */
    private ActionResult useSha(GameState state, GamePlayer player, GameCard card,
                                String targetUserId, String targetCardId) {
        // 检查本回合是否已使用过杀（诸葛连弩不受限制）
        boolean hasZhugeNu = player.getWeapon() != null &&
                player.getWeapon().getCardType() == CardType.ZHUGE_LIAN_NU;
        if (!hasZhugeNu && player.isUsedShaThisTurn()) {
            return failure("本回合已使用过杀");
        }

        // 检查酒效果
        boolean hasJiuEffect = player.isUsedAlcoholThisTurn();

        // 获取目标
        GamePlayer target;
        if (targetUserId != null) {
            target = findPlayerById(state, Long.valueOf(targetUserId));
        } else {
            target = player.getOpponent(state.getPlayers());
        }

        if (target == null || !target.isAlive()) {
            return failure("目标无效");
        }

        // 检查攻击距离 - 青釭剑
        boolean hasQingGang = player.getWeapon() != null &&
                player.getWeapon().getCardType() == CardType.QING_GANG;
        if (!player.canAttack(target, hasQingGang)) {
            return failure("攻击距离不足");
        }

        // 移除手牌中的杀
        player.removeHandCard(card.getId());

        // 设置pendingAction, 让目标出闪
        GameAction action = new GameAction();
        action.setActionType("RESPOND_SHAN");
        action.setSourceCardId(card.getId());
        action.setSourcePlayerId(player.getUserId());

        List<String> shanCards = target.getHandCards().stream()
                .filter(c -> c.getCardType() == CardType.SHAN)
                .map(GameCard::getId)
                .toList();
        action.setOptionalCardIds(shanCards);
        action.setOptionalCards(cardIdsToClientMap(state, shanCards));
        action.setOptionalTargetIds(Collections.singletonList(target.getUserId()));
        action.setMessage(target.getUsername() + " 请出闪");
        Map<String, Object> shaExtra = new HashMap<>();
        shaExtra.put("hasJiuEffect", hasJiuEffect);
        shaExtra.put("damage", 1);
        shaExtra.put("nature", card.getNature().name());
        shaExtra.put("isBlack", card.isBlack());
        action.setExtraData(shaExtra);

        state.setPendingAction(action);

        state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了" + card.getCardType().getDisplayName());
        // 诸葛连弩不限制出杀次数
        if (!hasZhugeNu) {
            player.setUsedShaThisTurn(true);
        }
        player.setShaCountThisTurn(player.getShaCountThisTurn() + 1);

        return success("杀已使用,等待对手响应", action);
    }

    /**
     * 使用桃
     */
    private ActionResult useTao(GameState state, GamePlayer player, GameCard card,
                                String targetUserId, String targetCardId) {
        if (player.getCurrentHp() >= player.getMaxHp()) {
            return failure("体力已满，不能使用桃");
        }

        player.removeHandCard(card.getId());
        int before = player.getCurrentHp();
        player.heal(1);
        state.addLog(player.getUsername() + " 使用了桃，体力 " + before + "→" + player.getCurrentHp());
        state.discardCard(card);

        return success("使用桃成功");
    }

    /**
     * 使用酒
     */
    private ActionResult useJiu(GameState state, GamePlayer player, GameCard card,
                                String targetUserId, String targetCardId) {
        if (player.isUsedAlcoholThisTurn()) {
            return failure("本回合已使用过酒");
        }
        player.removeHandCard(card.getId());
        player.setUsedAlcoholThisTurn(true);
        state.addLog(player.getUsername() + " 使用了酒");
        state.discardCard(card);

        return success("使用酒成功");
    }

    /**
     * 无中生有
     */
    private ActionResult useWuZhong(GameState state, GamePlayer player, GameCard card,
                                    String targetUserId, String targetCardId) {
        player.removeHandCard(card.getId());
        List<GameCard> drawn = state.drawCards(2);
        player.drawCards(drawn);
        state.discardCard(card);
        state.addLog(player.getUsername() + " 使用了无中生有，摸了2张牌");
        return success("摸2张牌");
    }

    /**
     * 过河拆桥 — 简化实现：不能对自己使用，不暴露对手手牌。
     * 如果目标有装备，使用者可以选择弃置公开装备；否则随机弃置目标一张手牌。
     */
    private ActionResult useGuoHe(GameState state, GamePlayer player, GameCard card,
                                  String targetUserId, String targetCardId) {
        GamePlayer target = findPlayerById(state, targetUserId != null ?
                Long.valueOf(targetUserId) : state.getOpponent().getUserId());
        if (target == null || !target.isAlive()) return failure("目标无效");
        // 不能对自己使用
        if (target.getUserId().equals(player.getUserId())) return failure("不能对自己使用过河拆桥");

        boolean hasHandCards = !target.getHandCards().isEmpty();
        boolean hasEquipment = target.getWeapon() != null || target.getArmor() != null
                || target.getPlusHorse() != null || target.getMinusHorse() != null;
        if (!hasHandCards && !hasEquipment) return failure("目标没有可拆的牌");

        player.removeHandCard(card.getId());
        state.discardCard(card);

        if (hasEquipment) {
            // 有装备：让使用者选择弃置哪件装备（公开信息）
            List<String> equipIds = new ArrayList<>();
            List<Map<String, Object>> equipInfos = new ArrayList<>();
            if (target.getWeapon() != null) {
                equipIds.add(target.getWeapon().getId());
                equipInfos.add(cardToClientMap(target.getWeapon()));
            }
            if (target.getArmor() != null) {
                equipIds.add(target.getArmor().getId());
                equipInfos.add(cardToClientMap(target.getArmor()));
            }
            if (target.getPlusHorse() != null) {
                equipIds.add(target.getPlusHorse().getId());
                equipInfos.add(cardToClientMap(target.getPlusHorse()));
            }
            if (target.getMinusHorse() != null) {
                equipIds.add(target.getMinusHorse().getId());
                equipInfos.add(cardToClientMap(target.getMinusHorse()));
            }
            // 添加一个随机弃手牌的选项（ID用特殊标记）
            equipIds.add("__RANDOM_HAND__");
            equipInfos.add(Map.of("id", "__RANDOM_HAND__", "displayName", "随机一张手牌",
                    "category", "特殊", "type", "RANDOM"));

            GameAction action = new GameAction();
            action.setActionType("CHOOSE_TARGET_CARD");
            action.setSourceCardId(card.getId());
            action.setSourcePlayerId(player.getUserId());
            action.setOptionalCardIds(equipIds);
            action.setOptionalCards(equipInfos);
            action.setOptionalTargetIds(Collections.singletonList(player.getUserId()));
            action.setMessage("请选择要弃置的装备，或选择随机手牌（过河拆桥）");
            action.setExtraData(Map.of("effectType", "GUO_HE", "targetId", target.getUserId()));
            state.setPendingAction(action);
            state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了过河拆桥");
            return success("过河拆桥已使用，请选择要弃置的牌", action);
        }

        // 无装备：随机弃一张手牌
        if (hasHandCards) {
            int idx = new Random().nextInt(target.getHandCards().size());
            GameCard discardCard = target.getHandCards().get(idx);
            target.removeHandCard(discardCard.getId());
            state.discardCard(discardCard);
            state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用过河拆桥，弃置其一张手牌");
        }
        return success("过河拆桥成功");
    }

    /**
     * 顺手牵羊 — 简化实现：不能对自己使用，不暴露对手手牌。
     * 如果目标有装备，使用者可以选择获得公开装备；否则随机获得目标一张手牌。
     */
    private ActionResult useShunShou(GameState state, GamePlayer player, GameCard card,
                                     String targetUserId, String targetCardId) {
        GamePlayer target = findPlayerById(state, targetUserId != null ?
                Long.valueOf(targetUserId) : state.getOpponent().getUserId());
        if (target == null || !target.isAlive()) return failure("目标无效");
        // 不能对自己使用
        if (target.getUserId().equals(player.getUserId())) return failure("不能对自己使用顺手牵羊");

        int dist = player.calculateDistanceTo(target);
        if (dist > 1) return failure("距离不足");

        boolean hasHandCards = !target.getHandCards().isEmpty();
        boolean hasEquipment = target.getWeapon() != null || target.getArmor() != null
                || target.getPlusHorse() != null || target.getMinusHorse() != null;
        if (!hasHandCards && !hasEquipment) return failure("目标没有可顺的牌");

        player.removeHandCard(card.getId());
        state.discardCard(card);

        if (hasEquipment) {
            // 有装备：让使用者选择获得哪件装备（公开信息）
            List<String> equipIds = new ArrayList<>();
            List<Map<String, Object>> equipInfos = new ArrayList<>();
            if (target.getWeapon() != null) {
                equipIds.add(target.getWeapon().getId());
                equipInfos.add(cardToClientMap(target.getWeapon()));
            }
            if (target.getArmor() != null) {
                equipIds.add(target.getArmor().getId());
                equipInfos.add(cardToClientMap(target.getArmor()));
            }
            if (target.getPlusHorse() != null) {
                equipIds.add(target.getPlusHorse().getId());
                equipInfos.add(cardToClientMap(target.getPlusHorse()));
            }
            if (target.getMinusHorse() != null) {
                equipIds.add(target.getMinusHorse().getId());
                equipInfos.add(cardToClientMap(target.getMinusHorse()));
            }
            // 添加一个随机获得手牌的选项
            equipIds.add("__RANDOM_HAND__");
            equipInfos.add(Map.of("id", "__RANDOM_HAND__", "displayName", "随机一张手牌",
                    "category", "特殊", "type", "RANDOM"));

            GameAction action = new GameAction();
            action.setActionType("CHOOSE_TARGET_CARD");
            action.setSourceCardId(card.getId());
            action.setSourcePlayerId(player.getUserId());
            action.setOptionalCardIds(equipIds);
            action.setOptionalCards(equipInfos);
            action.setOptionalTargetIds(Collections.singletonList(player.getUserId()));
            action.setMessage("请选择要获得的装备，或选择随机手牌（顺手牵羊）");
            action.setExtraData(Map.of("effectType", "SHUN_SHOU", "targetId", target.getUserId()));
            state.setPendingAction(action);
            state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了顺手牵羊");
            return success("顺手牵羊已使用，请选择要获得的牌", action);
        }

        // 无装备：随机获得一张手牌
        if (hasHandCards) {
            int idx = new Random().nextInt(target.getHandCards().size());
            GameCard stealCard = target.getHandCards().get(idx);
            target.removeHandCard(stealCard.getId());
            player.drawCards(Collections.singletonList(stealCard));
            state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用顺手牵羊，获得其一张手牌");
        }
        return success("顺手牵羊成功");
    }

    /**
     * 决斗
     */
    private ActionResult useJueDou(GameState state, GamePlayer player, GameCard card,
                                   String targetUserId, String targetCardId) {
        GamePlayer target = findPlayerById(state, targetUserId != null ?
                Long.valueOf(targetUserId) : state.getOpponent().getUserId());
        if (target == null || !target.isAlive()) return failure("目标无效");

        player.removeHandCard(card.getId());
        state.discardCard(card);

        List<String> shaCards = target.getHandCards().stream()
                .filter(c -> c.getCardType() == CardType.SHA)
                .map(GameCard::getId)
                .toList();

        GameAction action = new GameAction();
        action.setActionType("RESPOND_SHA");
        action.setSourceCardId(card.getId());
        action.setSourcePlayerId(player.getUserId());
        action.setOptionalCardIds(shaCards);
        action.setOptionalCards(cardIdsToClientMap(state, shaCards));
        action.setOptionalTargetIds(Collections.singletonList(target.getUserId()));
        action.setMessage("请出杀响应决斗");
        action.setExtraData(Map.of("effectType", "JUE_DOU", "initiatorId", player.getUserId()));

        state.setPendingAction(action);
        state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了决斗");
        return success("决斗已使用", action);
    }

    /**
     * 南蛮入侵
     */
    private ActionResult useNanMan(GameState state, GamePlayer player, GameCard card,
                                   String targetUserId, String targetCardId) {
        player.removeHandCard(card.getId());
        state.discardCard(card);

        GamePlayer target = state.getOpponent();
        if (!target.isAlive()) return success("南蛮入侵已使用，无存活目标");

        // 藤甲免疫
        if (target.getArmor() != null && target.getArmor().getCardType() == CardType.TENG_JIA) {
            state.addLog(target.getUsername() + " 的藤甲免疫了南蛮入侵");
            return success("南蛮入侵被藤甲免疫");
        }

        List<String> shaCards = target.getHandCards().stream()
                .filter(c -> c.getCardType() == CardType.SHA)
                .map(GameCard::getId)
                .toList();

        GameAction action = new GameAction();
        action.setActionType("RESPOND_SHA");
        action.setSourceCardId(card.getId());
        action.setSourcePlayerId(player.getUserId());
        action.setOptionalCardIds(shaCards);
        action.setOptionalCards(cardIdsToClientMap(state, shaCards));
        action.setOptionalTargetIds(Collections.singletonList(target.getUserId()));
        action.setMessage("请出杀响应南蛮入侵");
        action.setExtraData(Map.of("effectType", "NAN_MAN", "damage", 1));

        state.setPendingAction(action);
        state.addLog(player.getUsername() + " 使用了南蛮入侵");
        return success("南蛮入侵已使用", action);
    }

    /**
     * 万箭齐发
     */
    private ActionResult useWanJian(GameState state, GamePlayer player, GameCard card,
                                    String targetUserId, String targetCardId) {
        player.removeHandCard(card.getId());
        state.discardCard(card);

        GamePlayer target = state.getOpponent();
        if (!target.isAlive()) return success("万箭齐发已使用，无存活目标");

        List<String> shanCards = target.getHandCards().stream()
                .filter(c -> c.getCardType() == CardType.SHAN)
                .map(GameCard::getId)
                .toList();

        GameAction action = new GameAction();
        action.setActionType("RESPOND_SHAN");
        action.setSourceCardId(card.getId());
        action.setSourcePlayerId(player.getUserId());
        action.setOptionalCardIds(shanCards);
        action.setOptionalCards(cardIdsToClientMap(state, shanCards));
        action.setOptionalTargetIds(Collections.singletonList(target.getUserId()));
        action.setMessage("请出闪响应万箭齐发");
        action.setExtraData(Map.of("effectType", "WAN_JIAN", "damage", 1));

        state.setPendingAction(action);
        state.addLog(player.getUsername() + " 使用了万箭齐发");
        return success("万箭齐发已使用", action);
    }

    /**
     * 桃园结义
     */
    private ActionResult useTaoYuan(GameState state, GamePlayer player, GameCard card,
                                    String targetUserId, String targetCardId) {
        player.removeHandCard(card.getId());
        state.discardCard(card);

        for (GamePlayer p : state.getAlivePlayers()) {
            int healed = p.heal(1);
            if (healed > 0) {
                state.addLog(p.getUsername() + " 回复了1点体力");
            }
        }
        state.addLog(player.getUsername() + " 使用了桃园结义");
        return success("桃园结义已使用，全员回复1点体力");
    }

    /**
     * 五谷丰登
     */
    private ActionResult useWuGu(GameState state, GamePlayer player, GameCard card,
                                 String targetUserId, String targetCardId) {
        player.removeHandCard(card.getId());
        state.discardCard(card);

        int count = state.getAlivePlayers().size();
        List<GameCard> shown = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GameCard c = state.drawCard();
            if (c != null) shown.add(c);
        }

        if (shown.isEmpty()) {
            state.addLog(player.getUsername() + " 使用了五谷丰登，但牌堆已空，无牌可展示");
            return success("无牌可展示");
        }

        // 将亮出的牌存入 tempCards
        state.getTempCards().clear();
        state.getTempCards().addAll(shown);

        List<String> shownIds = shown.stream().map(GameCard::getId).toList();
        state.addLog(player.getUsername() + " 使用了五谷丰登，展示" + shown.size() + "张牌");

        List<Map<String, Object>> wuguCardInfos = shown.stream().map(this::cardToClientMap).toList();

        // 记录选牌顺序（按存活玩家顺序）
        List<Long> pickerOrder = state.getAlivePlayers().stream()
                .map(GamePlayer::getUserId).toList();

        GameAction action = new GameAction();
        action.setActionType("CHOOSE_WUGU_CARD");
        action.setSourceCardId(card.getId());
        action.setSourcePlayerId(player.getUserId());
        action.setOptionalCardIds(shownIds);
        action.setOptionalCards(wuguCardInfos);
        action.setMessage("请选择一张牌（五谷丰登）");
        action.setOptionalTargetIds(Collections.singletonList(player.getUserId()));
        action.setExtraData(Map.of(
                "pickerOrder", pickerOrder,
                "pickerIndex", 0
        ));

        state.setPendingAction(action);
        return success("五谷丰登已使用", action);
    }

    /**
     * 借刀杀人
     */
    private ActionResult useJieDao(GameState state, GamePlayer player, GameCard card,
                                   String targetUserId, String targetCardId) {
        GamePlayer target = state.getOpponent();
        if (target == null || !target.isAlive() || target.getWeapon() == null) {
            return failure("借刀杀人：目标没有武器");
        }

        player.removeHandCard(card.getId());
        state.discardCard(card);

        List<String> shaCards = target.getHandCards().stream()
                .filter(c -> c.getCardType() == CardType.SHA)
                .map(GameCard::getId)
                .toList();

        GameAction action = new GameAction();
        action.setActionType("RESPOND_SHA");
        action.setSourceCardId(card.getId());
        action.setSourcePlayerId(player.getUserId());
        action.setOptionalCardIds(shaCards);
        action.setOptionalCards(cardIdsToClientMap(state, shaCards));
        action.setOptionalTargetIds(Collections.singletonList(target.getUserId()));
        action.setMessage("请出杀，否则失去武器（借刀杀人）");
        action.setExtraData(Map.of("effectType", "JIE_DAO", "weaponId", target.getWeapon().getId()));

        state.setPendingAction(action);
        state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了借刀杀人");
        return success("借刀杀人已使用", action);
    }

    /**
     * 使用装备牌
     */
    private ActionResult playEquipmentCard(GameState state, GamePlayer player, GameCard card,
                                           String targetUserId, String targetCardId) {
        player.removeHandCard(card.getId());

        // 如果有同类型装备，先卸下
        CardType type = card.getCardType();
        GameCard old = player.unequip(type);
        if (old != null) {
            state.discardCard(old);
            state.addLog(player.getUsername() + " 替换了" + old.getCardType().getDisplayName());
        }

        player.equip(card);
        state.addLog(player.getUsername() + " 装备了" + card.getCardType().getDisplayName());

        return success("装备成功");
    }

    /**
     * 火攻
     */
    private ActionResult useHuoGong(GameState state, GamePlayer player, GameCard card,
                                    String targetUserId, String targetCardId) {
        GamePlayer target = findPlayerById(state, targetUserId != null ?
                Long.valueOf(targetUserId) : state.getOpponent().getUserId());
        if (target == null || !target.isAlive()) return failure("目标无效");
        if (target.getHandCards().isEmpty()) return failure("目标没有手牌，无法火攻");

        player.removeHandCard(card.getId());
        state.discardCard(card);

        // 随机展示目标一张手牌
        Random random = new Random();
        int idx = random.nextInt(target.getHandCards().size());
        GameCard revealedCard = target.getHandCards().get(idx);

        // 让使用者弃一张手牌
        List<String> attackerCardIds = player.getHandCards().stream()
                .map(GameCard::getId).toList();

        GameAction action = new GameAction();
        action.setActionType("HUO_GONG_DISCARD");
        action.setSourcePlayerId(player.getUserId());
        action.setOptionalCardIds(attackerCardIds);
        action.setOptionalCards(cardIdsToClientMap(state, attackerCardIds));
        action.setOptionalTargetIds(Collections.singletonList(player.getUserId()));
        action.setMessage("火攻：" + target.getUsername() + " 展示了 " + revealedCard.getDisplayName() +
                "，请弃置一张相同花色的手牌");
        action.setExtraData(Map.of(
                "effectType", "HUO_GONG",
                "targetId", target.getUserId(),
                "revealedSuit", revealedCard.getSuit().name()
        ));

        state.setPendingAction(action);
        state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了火攻");
        return success("火攻已使用，请弃置一张手牌", action);
    }

    /**
     * 铁索连环
     */
    private ActionResult useTieSuo(GameState state, GamePlayer player, GameCard card,
                                   String targetUserId, String targetCardId) {
        if (targetUserId == null) return failure("请选择目标");

        GamePlayer target = findPlayerById(state, Long.valueOf(targetUserId));
        if (target == null || !target.isAlive()) return failure("目标无效");

        player.removeHandCard(card.getId());
        state.discardCard(card);

        target.setChained(!target.isChained());
        String actionStr = target.isChained() ? "横置" : "重置";
        state.addLog(player.getUsername() +
                (target.getUserId().equals(player.getUserId()) ? "将自身" : "将" + target.getUsername()) +
                actionStr);
        return success("铁索连环已使用");
    }

    /**
     * 处理火攻弃牌响应
     */
    @SuppressWarnings("unchecked")
    private ActionResult handleHuoGongDiscard(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer player = findPlayerById(state, userId);
        if (player == null) return failure("未找到玩家");

        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        String revealedSuit = (String) extra.get("revealedSuit");
        Long targetId = Long.valueOf(extra.get("targetId").toString());
        GamePlayer target = findPlayerById(state, targetId);
        if (target == null) return failure("未找到目标");

        if (cardId != null) {
            GameCard discardCard = findCard(player, cardId);
            if (discardCard == null) return failure("无效的卡牌");
            player.removeHandCard(cardId);
            state.discardCard(discardCard);

            if (discardCard.getSuit().name().equals(revealedSuit)) {
                // 花色相同，成功
                target.takeDamage(1);
                state.addLog("火攻成功！" + target.getUsername() + "受到1点火伤害");
                fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, player, target, null, 1,
                        Map.of("effectType", "HUO_GONG")));
                ActionResult dying = checkDying(state, target);
                if (dying != null) return dying;
                state.checkGameOver();
            } else {
                state.addLog("火攻失败，" + discardCard.getSuit().getSymbol() + "≠已展示花色");
            }
        } else {
            state.addLog(player.getUsername() + " 跳过了火攻");
        }

        state.setPendingAction(null);
        return success("火攻完成");
    }

    /**
     * 占位方法 — 尚未实现的卡牌效果（保留作参考）
     */
    private ActionResult notImplementedYet(GameState state, GamePlayer player, GameCard card,
                                           String targetUserId, String targetCardId) {
        return failure("【" + card.getCardType().getDisplayName() + "】效果尚未实现");
    }

    /**
     * 执行延时锦囊的效果（乐不思蜀/兵粮寸断/闪电）
     * 由 JUDGE 阶段或无懈可击结算后调用。
     * 注意：此方法可能通过 checkDying 设置 pendingAction（濒死求桃）
     */
    private void applyDelayTrickEffect(GameState state, GamePlayer player, GameCard trickCard) {
        if (trickCard == null) return;

        GameCard judgeCard = state.drawCard();
        if (judgeCard == null) {
            state.addLog("牌堆已空，无法判定" + trickCard.getCardType().getDisplayName());
            state.discardCard(trickCard);
            return;
        }

        CardType type = trickCard.getCardType();
        state.addLog(player.getUsername() + " 判定" + type.getDisplayName() + ": " + judgeCard.getDisplayName());

        switch (type) {
            case LE_BU -> {
                // 判定结果不为红桃时跳过出牌阶段
                if (judgeCard.getSuit() != GameCard.Suit.HEART) {
                    player.setSkipPlayPhase(true);
                    state.addLog("乐不思蜀生效，" + player.getUsername() + "将跳过出牌阶段");
                } else {
                    state.addLog("乐不思蜀失效（判定为红桃）");
                }
                state.discardCard(trickCard);
                state.discardCard(judgeCard);
            }
            case BING_LIANG -> {
                // 判定结果不为草花时跳过摸牌阶段
                if (judgeCard.getSuit() != GameCard.Suit.CLUB) {
                    player.setSkipDrawPhase(true);
                    state.addLog("兵粮寸断生效，" + player.getUsername() + "将跳过摸牌阶段");
                } else {
                    state.addLog("兵粮寸断失效（判定为草花）");
                }
                state.discardCard(trickCard);
                state.discardCard(judgeCard);
            }
            case SHAN_DIAN -> {
                // 黑桃2-9时触发3点雷电伤害
                if (judgeCard.getSuit() == GameCard.Suit.SPADE && judgeCard.getNumber() >= 2 && judgeCard.getNumber() <= 9) {
                    state.discardCard(trickCard);
                    state.discardCard(judgeCard);
                    player.takeDamage(3);
                    state.addLog("⚡闪电判定生效！" + player.getUsername() + "受到3点雷电伤害");
                    fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, null, player, null, 3,
                            Map.of("effectType", "SHAN_DIAN")));
                    ActionResult dying = checkDying(state, player);
                    if (dying != null) return; // pending action set by checkDying
                    state.checkGameOver();
                } else {
                    state.addLog("闪电判定不生效，留在判定区");
                    state.discardCard(judgeCard);
                    // 闪电移到对手判定区首位（下次判定由对手承受）
                    GamePlayer opponent = player.getOpponent(state.getPlayers());
                    if (opponent != null) {
                        opponent.getJudgeArea().add(0, trickCard);
                    }
                }
            }
            default -> {
                state.discardCard(trickCard);
                state.discardCard(judgeCard);
            }
        }
    }

    /**
     * 使用延时锦囊
     */
    private ActionResult playDelayTrick(GameState state, GamePlayer player, GameCard card,
                                        String targetUserId, String targetCardId) {
        GamePlayer target;
        if (targetUserId != null) {
            target = findPlayerById(state, Long.valueOf(targetUserId));
        } else {
            target = state.getOpponent();
        }
        if (target == null || !target.isAlive()) return failure("目标无效");

        // 检查判定区是否已满（最多3张）
        if (target.getJudgeArea().size() >= 3) {
            return failure("目标判定区已满");
        }

        player.removeHandCard(card.getId());
        target.getJudgeArea().add(card);
        state.addLog(player.getUsername() + " 对 " + target.getUsername() +
                " 使用了" + card.getCardType().getDisplayName());

        return success("延时锦囊已放置");
    }

    // ============ 无懈可击响应链 ============

    /**
     * 锦囊牌使用时打开无懈可击响应窗口给所有存活玩家。
     * 所有玩家可同时响应，先到先得。
     */
    @SuppressWarnings("unchecked")
    private ActionResult playTrickWithWuxieCheck(GameState state, GamePlayer player, GameCard card,
                                                  CardType type, CardEffect effect,
                                                  String targetUserId, String targetCardId) {
        // 先将锦囊牌从手牌移除，暂存到 tempCards
        player.removeHandCard(card.getId());
        state.getTempCards().clear();
        state.getTempCards().add(card);

        // 检查是否有存活玩家持无懈可击
        boolean anyHasWuxie = state.getAlivePlayers().stream()
                .anyMatch(p -> p.getHandCards().stream().anyMatch(c -> c.getCardType() == CardType.WU_XIE));

        if (!anyHasWuxie) {
            // 无人有无懈可击，直接结算
            state.getTempCards().clear();
            ActionResult result = effect.execute(state, player, card, targetUserId, targetCardId);
            fireEvent(new GameEvent(GameEventType.CARD_USED, state, player, null, card, 0, null));
            return result;
        }

        // 打开无懈可击响应窗口给所有存活玩家
        List<Long> allAliveIds = state.getAlivePlayers().stream()
                .map(GamePlayer::getUserId).toList();

        GameAction action = new GameAction();
        action.setActionType("WAIT_WUXIE_RESPONSE");
        action.setSourcePlayerId(player.getUserId());
        action.setOptionalCardIds(Collections.emptyList());  // 各玩家从自己手牌选
        action.setOptionalCards(Collections.emptyList());
        action.setOptionalTargetIds(allAliveIds);  // 所有存活玩家均可响应
        action.setMessage("是否使用无懈可击？");

        Map<String, Object> extraData = new HashMap<>();
        extraData.put("trickCardId", card.getId());
        extraData.put("trickCardType", type.name());
        extraData.put("sourcePlayerId", player.getUserId());
        extraData.put("originalTargetUserId", targetUserId);
        extraData.put("originalTargetCardId", targetCardId);
        extraData.put("wuxieStack", new ArrayList<String>());       // 已使用无懈的玩家（顺序）
        extraData.put("respondedSkipIds", new ArrayList<Long>());   // 已跳过响应的玩家
        action.setExtraData(extraData);

        state.setPendingAction(action);
        state.addLog(player.getUsername() + " 使用了" + type.getDisplayName());
        return success("等待无懈可击响应", action);
    }

    /**
     * 处理无懈可击响应 — 同时响应模式。
     * 锦囊使用时所有存活玩家可同时选择打出/跳过。
     * 先到先得：第一个打出者成功，其他人可见此时不能再出；
     * 打出后打开新窗口仅给另一方，另一方也可选择打出或跳过。
     *
     * cardId != null → 使用无懈可击；null → 跳过
     */
    @SuppressWarnings("unchecked")
    private ActionResult handleWuxieResponse(GameState state, Long userId, String cardId, GameAction pending) {
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        if (extra == null) return failure("无懈可击数据异常");

        String trickCardTypeStr = (String) extra.get("trickCardType");
        CardType trickCardType = CardType.valueOf(trickCardTypeStr);
        Long sourcePlayerId = Long.valueOf(extra.get("sourcePlayerId").toString());
        String originalTargetUserId = (String) extra.get("originalTargetUserId");
        String originalTargetCardId = (String) extra.get("originalTargetCardId");
        List<String> wuxieStack = (List<String>) extra.get("wuxieStack");
        List<Long> respondedSkipIds = (List<Long>) extra.get("respondedSkipIds");

        // 检查玩家是否已响应过
        if (respondedSkipIds.contains(userId) || wuxieStack.contains(String.valueOf(userId))) {
            return failure("你已做出响应");
        }

        GamePlayer currentPlayer = findPlayerById(state, userId);
        if (currentPlayer == null) return failure("未找到玩家");

        if (cardId != null) {
            // === 打出一张无懈可击 ===
            GameCard wuxieCard = findCard(currentPlayer, cardId);
            if (wuxieCard == null || wuxieCard.getCardType() != CardType.WU_XIE) {
                return failure("无效的无懈可击");
            }

            currentPlayer.removeHandCard(cardId);
            state.discardCard(wuxieCard);
            wuxieStack.add(String.valueOf(userId));
            state.addLog(currentPlayer.getUsername() + " 使用了无懈可击");

            // 新窗口仅给另一方：已重置 respondedSkipIds，被锁定的人挡在外面
            Long otherUserId = state.getAlivePlayers().stream()
                    .filter(p -> !p.getUserId().equals(userId))
                    .findFirst()
                    .map(GamePlayer::getUserId)
                    .orElse(null);

            if (otherUserId == null) {
                return resolveWuxieChain(state, trickCardType, sourcePlayerId,
                        originalTargetUserId, originalTargetCardId, wuxieStack, extra);
            }

            // 检查对方是否有 WUXIE
            GamePlayer otherPlayer = findPlayerById(state, otherUserId);
            boolean otherHasWuxie = otherPlayer != null && otherPlayer.isAlive() &&
                    otherPlayer.getHandCards().stream().anyMatch(c -> c.getCardType() == CardType.WU_XIE);

            if (!otherHasWuxie) {
                // 对方无 WUXIE，结算链
                return resolveWuxieChain(state, trickCardType, sourcePlayerId,
                        originalTargetUserId, originalTargetCardId, wuxieStack, extra);
            }

            // 新窗口仅给另一方，同时锁定打出方（不在 optionalTargetIds 中）且归零 respondedSkipIds
            GameAction newAction = new GameAction();
            newAction.setActionType("WAIT_WUXIE_RESPONSE");
            newAction.setSourcePlayerId(pending.getSourcePlayerId());
            newAction.setOptionalCardIds(Collections.emptyList());
            newAction.setOptionalCards(Collections.emptyList());
            newAction.setOptionalTargetIds(Collections.singletonList(otherUserId));
            newAction.setMessage("是否使用无懈可击？");

            Map<String, Object> newExtra = new HashMap<>(extra);
            newExtra.put("respondedSkipIds", new ArrayList<Long>());  // 新回合归零
            newAction.setExtraData(newExtra);

            state.setPendingAction(newAction);
            return success("无懈可击已使用，等待对方响应", newAction);
        } else {
            // === 跳过 ===
            respondedSkipIds.add(userId);

            // 确定还有谁未响应
            List<Long> remainingIds = state.getAlivePlayers().stream()
                    .map(GamePlayer::getUserId)
                    .filter(id -> !respondedSkipIds.contains(id))
                    .filter(id -> !wuxieStack.contains(String.valueOf(id)))
                    .toList();

            if (remainingIds.isEmpty()) {
                // 所有人都已响应（跳过/使用过无懈），结算链
                return resolveWuxieChain(state, trickCardType, sourcePlayerId,
                        originalTargetUserId, originalTargetCardId, wuxieStack, extra);
            }

            // 还有人未响应，更新 pending action 的 target 列表
            pending.setOptionalTargetIds(remainingIds);
            return success("已跳过，等待其他玩家响应", pending);
        }
    }

    /**
     * 结算无懈可击链
     * wuxieStack.size() % 2 == 1 → 锦囊被抵消
     * wuxieStack.size() % 2 == 0 → 锦囊正常结算
     * extra 中包含 isDelayTrickJudgment 标志时按延时锦囊判定处理
     */
    @SuppressWarnings("unchecked")
    private ActionResult resolveWuxieChain(GameState state, CardType trickCardType,
                                            Long sourcePlayerId, String originalTargetUserId,
                                            String originalTargetCardId, List<String> wuxieStack,
                                            Map<String, Object> extra) {
        boolean isJudgment = extra != null && Boolean.TRUE.equals(extra.get("isDelayTrickJudgment"));

        if (isJudgment) {
            GamePlayer current = state.getCurrentPlayer();
            GameCard judgeCard = state.getTempCards().isEmpty() ? null : state.getTempCards().get(0);

            if (wuxieStack.size() % 2 == 1) {
                // 奇数 → 被抵消
                if (judgeCard != null) {
                    state.discardCard(judgeCard);
                    state.addLog(judgeCard.getCardType().getDisplayName() + " 被无懈可击抵消");
                }
                state.getTempCards().clear();
                state.setPendingAction(null);
                return success("延时锦囊被无懈可击抵消");
            } else {
                // 偶数 → 未被抵消，正常判定
                if (judgeCard != null) {
                    applyDelayTrickEffect(state, current, judgeCard);
                }
                state.getTempCards().clear();
                // applyDelayTrickEffect 可能设置了濒死求桃 pending action
                if (state.getPendingAction() == null) {
                    state.setPendingAction(null);
                }
                return success("延时锦囊判定完成");
            }
        }

        // === 普通锦囊无懈可击链结算 ===
        GameCard trickCard = state.getTempCards().isEmpty() ? null : state.getTempCards().get(0);

        if (wuxieStack.size() % 2 == 1) {
            // 奇数 → 锦囊被抵消
            if (trickCard != null) {
                state.discardCard(trickCard);
            }
            state.getTempCards().clear();
            state.setPendingAction(null);
            state.addLog("锦囊被无懈可击抵消");
            return success("锦囊被无懈可击抵消");
        } else {
            // 偶数 → 锦囊正常结算
            state.getTempCards().clear();
            GamePlayer sourcePlayer = findPlayerById(state, sourcePlayerId);
            if (sourcePlayer == null) return failure("未找到发起者");

            CardEffect effect = trickEffects.get(trickCardType);
            if (effect == null) return failure("未知锦囊效果");

            // 结算原始锦囊效果（卡牌已从手牌移除，effect 中的 removeHandCard 是无操作）
            ActionResult result = effect.execute(state, sourcePlayer, trickCard,
                    originalTargetUserId, originalTargetCardId);

            // 触发 CARD_USED 事件
            if (result.success()) {
                fireEvent(new GameEvent(GameEventType.CARD_USED, state, sourcePlayer, null, trickCard, 0, null));
            }

            return result;
        }
    }

    /**
     * 处理玩家响应（出闪/出杀等）
     */
    public ActionResult handleResponse(GameState state, Long userId, String cardId, String targetUserId) {
        GameAction pending = state.getPendingAction();
        if (pending == null) return failure("没有待处理的响应");

        // 验证响应者
        if (!pending.getOptionalTargetIds().contains(userId)) {
            return failure("不是你需要响应");
        }

        String actionType = pending.getActionType();

        return switch (actionType) {
            case "RESPOND_SHAN" -> handleRespondShan(state, userId, cardId, pending);
            case "RESPOND_SHA" -> handleRespondSha(state, userId, cardId, pending);
            case "CHOOSE_TARGET_CARD" -> handleChooseTargetCard(state, userId, cardId, pending);
            case "CHOOSE_WUGU_CARD" -> handleChooseWuguCard(state, userId, cardId, pending);
            case "DISCARD" -> handleDiscardPhase(state, userId, cardId, pending);
            case "DYING_REQUIRE_TAO" -> handleDying(state, userId, cardId, pending);
            case "HUO_GONG_DISCARD" -> handleHuoGongDiscard(state, userId, cardId, pending);
            case "WAIT_EQUIP_TRIGGER" -> handleEquipTrigger(state, userId, cardId, pending);
            case "WAIT_WUXIE_RESPONSE" -> handleWuxieResponse(state, userId, cardId, pending);
            default -> failure("未知响应类型");
        };
    }

    /**
     * 处理出闪响应
     */
    private ActionResult handleRespondShan(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer responder = findPlayerById(state, userId);
        if (responder == null) return failure("未找到玩家");

        GamePlayer attacker = findPlayerById(state, pending.getSourcePlayerId());
        if (attacker == null) return failure("未找到攻击者");

        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        boolean hasJiuEffect = extra != null && Boolean.TRUE.equals(extra.get("hasJiuEffect"));
        int baseDamage = extra != null && extra.get("damage") instanceof Integer ?
                (int) extra.get("damage") : 1;
        String effectType = extra != null ? (String) extra.get("effectType") : null;

        if (cardId != null) {
            // 出了闪
            GameCard shanCard = findCard(responder, cardId);
            if (shanCard == null || shanCard.getCardType() != CardType.SHAN) {
                return failure("无效的闪");
            }

            // 八卦阵判定
            boolean needShan = true;
            if (responder.getArmor() != null && responder.getArmor().getCardType() == CardType.BA_GUA) {
                GameCard judgeCard = state.drawCard();
                if (judgeCard != null) {
                    state.discardCard(judgeCard);
                    if (judgeCard.isRed()) {
                        needShan = false;
                        state.addLog(responder.getUsername() + " 的八卦阵判定成功，视为出闪");
                    } else {
                        state.addLog(responder.getUsername() + " 的八卦阵判定失败");
                    }
                }
            }

            if (needShan) {
                responder.removeHandCard(cardId);
                state.discardCard(shanCard);
            }

            // 武器效果：贯石斧 / 青龙偃月刀（仅非AOE时生效）
            if (!"NAN_MAN".equals(effectType) && !"WAN_JIAN".equals(effectType)) {
                if (attacker != null && attacker.getWeapon() != null) {
                    CardType weaponType = attacker.getWeapon().getCardType();

                    // 贯石斧：杀被闪抵消后可弃两张牌强制命中
                    if (weaponType == CardType.GUAN_SHI && countDiscardableCards(attacker) >= 2) {
                        GameAction equipAction = new GameAction();
                        equipAction.setActionType("WAIT_EQUIP_TRIGGER");
                        equipAction.setSourcePlayerId(attacker.getUserId());
                        equipAction.setOptionalTargetIds(Collections.singletonList(attacker.getUserId()));
                        equipAction.setMessage("是否发动贯石斧？弃两张牌强制命中");
                        Map<String, Object> ed = new HashMap<>();
                        ed.put("skillCode", "GUAN_SHI");
                        ed.put("targetId", responder.getUserId());
                        ed.put("damage", baseDamage + (hasJiuEffect ? 1 : 0));
                        ed.put("nature", extra != null ? extra.get("nature") : "NORMAL");
                        ed.put("effectType", effectType);
                        ed.put("isBlackSha", extra != null && Boolean.TRUE.equals(extra.get("isBlack")));
                        equipAction.setExtraData(ed);
                        state.setPendingAction(equipAction);
                        state.addLog(attacker.getUsername() + " 可发动贯石斧");
                        return success("贯石斧触发", equipAction);
                    }

                    // 青龙偃月刀：杀被闪抵消后可追刀
                    if (weaponType == CardType.QING_LONG) {
                        List<String> shaCards = attacker.getHandCards().stream()
                                .filter(c -> c.getCardType() == CardType.SHA)
                                .map(GameCard::getId).toList();
                        if (!shaCards.isEmpty()) {
                            List<Map<String, Object>> shaInfos = cardIdsToClientMap(state, shaCards);
                            GameAction equipAction = new GameAction();
                            equipAction.setActionType("WAIT_EQUIP_TRIGGER");
                            equipAction.setSourcePlayerId(attacker.getUserId());
                            equipAction.setOptionalCardIds(shaCards);
                            equipAction.setOptionalCards(shaInfos);
                            equipAction.setOptionalTargetIds(Collections.singletonList(attacker.getUserId()));
                            equipAction.setMessage("请选择一张杀发动青龙偃月刀追刀，或跳过");
                            equipAction.setExtraData(Map.of("skillCode", "QING_LONG", "targetId", responder.getUserId()));
                            state.setPendingAction(equipAction);
                            state.addLog(attacker.getUsername() + " 可发动青龙偃月刀追刀");
                            return success("青龙偃月刀触发", equipAction);
                        }
                    }
                }
            }

            state.addLog(responder.getUsername() + " 出闪成功");
            state.setPendingAction(null);
            return success("出闪成功");
        } else {
            // 没有出闪 - 命中
            int damage = baseDamage;
            if (hasJiuEffect) {
                damage += 1;
                state.addLog("酒效果使伤害+1");
            }

            // 古锭刀：目标无手牌时伤害+1
            if (attacker.getWeapon() != null && attacker.getWeapon().getCardType() == CardType.GU_DING_DAO
                    && responder.getHandCards().isEmpty()) {
                damage += 1;
                state.addLog("古锭刀效果：目标无手牌，伤害+1");
            }

            // 寒冰剑：杀命中时可改为弃目标两张牌
            if (attacker.getWeapon() != null && attacker.getWeapon().getCardType() == CardType.HAN_BING
                    && !"NAN_MAN".equals(effectType) && !"WAN_JIAN".equals(effectType)
                    && countDiscardableCards(responder) >= 2) {
                GameAction equipAction = new GameAction();
                equipAction.setActionType("WAIT_EQUIP_TRIGGER");
                equipAction.setSourcePlayerId(attacker.getUserId());
                equipAction.setOptionalTargetIds(Collections.singletonList(attacker.getUserId()));
                equipAction.setMessage("是否发动寒冰剑？改为弃置" + responder.getUsername() + "两张牌");
                Map<String, Object> ed = new HashMap<>();
                ed.put("skillCode", "HAN_BING");
                ed.put("targetId", responder.getUserId());
                ed.put("effectType", effectType);
                ed.put("damage", baseDamage);
                ed.put("hasJiuEffect", hasJiuEffect);
                ed.put("nature", extra != null ? extra.get("nature") : "NORMAL");
                ed.put("isBlack", extra != null && Boolean.TRUE.equals(extra.get("isBlack")));
                equipAction.setExtraData(ed);
                state.setPendingAction(equipAction);
                state.addLog(attacker.getUsername() + " 可发动寒冰剑");
                return success("寒冰剑触发", equipAction);
            }

            // 检查防具
            boolean armorEffective = true;
            if (responder.getArmor() != null) {
                CardType armorType = responder.getArmor().getCardType();
                if (armorType == CardType.REN_WANG && extra != null && Boolean.TRUE.equals(extra.get("isBlack"))) {
                    armorEffective = false;
                    state.addLog(responder.getUsername() + " 的仁王盾使黑色杀无效");
                    state.setPendingAction(null);
                    return success("杀被仁王盾抵挡");
                }
                if (armorType == CardType.TENG_JIA && effectType == null) {
                    armorEffective = false;
                    state.addLog(responder.getUsername() + " 的藤甲使普通杀无效");
                    state.setPendingAction(null);
                    return success("杀被藤甲抵挡");
                }
            }

            if (!armorEffective) {
                state.setPendingAction(null);
                return success("杀被防具抵挡");
            }

            // 白银狮子
            boolean hasSilverLion = responder.getArmor() != null &&
                    responder.getArmor().getCardType() == CardType.BAI_YIN;
            if (hasSilverLion) {
                damage = Math.min(damage, 1);
                state.addLog("白银狮子将伤害降为1");
            }

            responder.takeDamage(damage);
            state.addLog(responder.getUsername() + " 受到" + damage + "点伤害");
            state.addLog(attacker.getUsername() + " 的杀命中");

            // 藤甲火伤+1检查（火属性杀或朱雀羽扇）
            if (responder.getArmor() != null && responder.getArmor().getCardType() == CardType.TENG_JIA) {
                String natureStr = extra != null ? (String) extra.get("nature") : null;
                boolean isFireDamage = "FIRE".equals(natureStr) ||
                        (attacker.getWeapon() != null && attacker.getWeapon().getCardType() == CardType.ZHU_QUE);
                if (isFireDamage) {
                    responder.takeDamage(1);
                    state.addLog("藤甲使火伤害+1");
                    fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, attacker, responder, null, 1,
                            Map.of("effectType", "TENG_JIA_FIRE")));
                }
            }

            // 濒死检查
            ActionResult dying = checkDying(state, responder);
            if (dying != null) return dying;

            state.checkGameOver();

            // 游戏未结束时触发伤害事件（麒麟弓等装备效果）
            if (!state.isFinished()) {
                fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, attacker, responder, null, damage,
                        Map.of("effectType", effectType != null ? effectType : "SHA")));
                // 如果触发器设置了 pending action，保留它
                if (state.getPendingAction() != null) {
                    return success("命中，" + damage + "点伤害，触发装备效果", state.getPendingAction());
                }
            }

            state.setPendingAction(null);

            return success("命中，" + damage + "点伤害");
        }
    }

    /**
     * 处理出杀响应（决斗/南蛮/借刀杀人）
     */
    private ActionResult handleRespondSha(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer responder = findPlayerById(state, userId);
        if (responder == null) return failure("未找到玩家");

        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        String effectType = extra != null ? (String) extra.get("effectType") : null;

        if (cardId != null) {
            // 出杀
            GameCard shaCard = findCard(responder, cardId);
            if (shaCard == null || shaCard.getCardType() != CardType.SHA) {
                return failure("无效的杀");
            }
            responder.removeHandCard(cardId);
            state.discardCard(shaCard);
            return onShaPlayed(state, responder, pending, responder.getUsername() + " 出杀响应");
        } else {
            // 没有出杀
            if ("JUE_DOU".equals(effectType)) {
                GamePlayer initiator = findPlayerById(state, pending.getSourcePlayerId());
                responder.takeDamage(1);
                state.addLog(responder.getUsername() + " 决斗失败，受到1点伤害");
                fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, initiator, responder, null, 1,
                        Map.of("effectType", "JUE_DOU")));
                // 濒死检查
                ActionResult dying = checkDying(state, responder);
                if (dying != null) return dying;
                state.checkGameOver();
                state.setPendingAction(null);
                return success("决斗结束，有人受伤");
            } else if ("NAN_MAN".equals(effectType)) {
                GamePlayer initiator = findPlayerById(state, pending.getSourcePlayerId());
                responder.takeDamage(1);
                state.addLog(responder.getUsername() + " 未出杀，受到南蛮入侵伤害");
                fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, initiator, responder, null, 1,
                        Map.of("effectType", "NAN_MAN")));
                ActionResult dying = checkDying(state, responder);
                if (dying != null) return dying;
                state.checkGameOver();
                state.setPendingAction(null);
                return success("南蛮入侵命中");
            } else if ("JIE_DAO".equals(effectType)) {
                // 借刀杀人：没出杀，失去武器
                String weaponId = extra != null ? (String) extra.get("weaponId") : null;
                if (weaponId != null && responder.getWeapon() != null &&
                        responder.getWeapon().getId().equals(weaponId)) {
                    GameCard weapon = responder.unequip(responder.getWeapon().getCardType());
                    if (weapon != null) {
                        // 武器给发起者
                        GamePlayer initiator = findPlayerById(state, pending.getSourcePlayerId());
                        if (initiator != null) {
                            initiator.drawCards(Collections.singletonList(weapon));
                            state.addLog(initiator.getUsername() + " 获得了" + weapon.getCardType().getDisplayName());
                        }
                    }
                }
                state.setPendingAction(null);
                return success("借刀杀人：获得武器");
            }

            state.setPendingAction(null);
            return success("未出杀");
        }
    }

    /**
     * 出杀响应后的公共流程（决斗交替/南蛮通过/借刀杀人保留武器）
     * 由 handleRespondSha 和技能（丈八蛇矛）共用
     */
    private ActionResult onShaPlayed(GameState state, GamePlayer responder, GameAction pending, String logMsg) {
        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        String effectType = extra != null ? (String) extra.get("effectType") : null;

        state.addLog(logMsg);

        if ("JUE_DOU".equals(effectType)) {
            state.setPendingAction(null);
            GamePlayer initiator = findPlayerById(state, pending.getSourcePlayerId());
            if (initiator == null) return failure("未找到发起者");

            List<String> shaCards = initiator.getHandCards().stream()
                    .filter(c -> c.getCardType() == CardType.SHA)
                    .map(GameCard::getId)
                    .toList();

            GameAction newAction = new GameAction();
            newAction.setActionType("RESPOND_SHA");
            newAction.setSourceCardId(pending.getSourceCardId());
            newAction.setSourcePlayerId(responder.getUserId());
            newAction.setOptionalCardIds(shaCards);
            newAction.setOptionalTargetIds(Collections.singletonList(initiator.getUserId()));
            newAction.setMessage("请出杀响应决斗");
            newAction.setExtraData(Map.of("effectType", "JUE_DOU", "initiatorId", initiator.getUserId()));

            state.setPendingAction(newAction);
            state.addLog("决斗继续，" + initiator.getUsername() + " 需要出杀");
            return success("出杀，等待对方继续响应", newAction);
        } else if ("NAN_MAN".equals(effectType)) {
            state.setPendingAction(null);
            return success("南蛮入侵被响应");
        } else if ("JIE_DAO".equals(effectType)) {
            state.setPendingAction(null);
            GamePlayer initiator = findPlayerById(state, pending.getSourcePlayerId());
            if (initiator != null) {
                state.addLog(responder.getUsername() + " 出杀响应借刀杀人，保留武器");
            }
            return success("借刀杀人：出杀成功");
        }

        state.setPendingAction(null);
        return success("响应成功");
    }

    /**
     * 处理装备触发响应（麒麟弓弃马 / 贯石斧 / 青龙偃月刀 / 寒冰剑）
     */
    private ActionResult handleEquipTrigger(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer player = findPlayerById(state, userId);
        if (player == null) return failure("未找到玩家");

        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        if (extra == null) return failure("触发数据异常");
        String skillCode = (String) extra.get("skillCode");

        return switch (skillCode) {
            case "QI_LIN_GONG" -> handleQiLinGong(state, userId, cardId, pending);
            case "GUAN_SHI" -> handleGuanShi(state, userId, cardId);
            case "QING_LONG" -> handleQingLong(state, userId, cardId, pending);
            case "HAN_BING" -> handleHanBing(state, userId, cardId, pending);
            default -> {
                state.setPendingAction(null);
                yield failure("未知技能码: " + skillCode);
            }
        };
    }

    /**
     * 麒麟弓：杀命中后弃马
     */
    private ActionResult handleQiLinGong(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer player = findPlayerById(state, userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        Object targetIdObj = extra.get("targetId");
        Long targetId = targetIdObj != null ? Long.valueOf(targetIdObj.toString()) : null;
        GamePlayer target = targetId != null ? findPlayerById(state, targetId) : null;
        if (target == null) {
            state.setPendingAction(null);
            return failure("麒麟弓触发数据异常");
        }
        if (cardId != null) {
            GameCard horse = findCard(target, cardId);
            if (horse == null || !(horse.getCardType().isPlusHorse() || horse.getCardType().isMinusHorse())) {
                return failure("无效的马牌");
            }
            target.unequip(horse.getCardType());
            state.discardCard(horse);
            state.addLog(player.getUsername() + " 发动麒麟弓，弃置了 " + target.getUsername() + " 的 " + horse.getCardType().getDisplayName());
        } else {
            state.addLog(player.getUsername() + " 选择不发动麒麟弓");
        }
        state.setPendingAction(null);
        return success(cardId != null ? "麒麟弓：已弃马" : "麒麟弓：不发动");
    }

    /**
     * 贯石斧：弃两张牌强制命中
     */
    private ActionResult handleGuanShi(GameState state, Long userId, String cardId) {
        if (cardId == null) {
            state.setPendingAction(null);
            state.addLog("不发动贯石斧");
            return success("不发动");
        }

        GamePlayer attacker = findPlayerById(state, userId);
        if (attacker == null) return failure("未找到玩家");

        // 从 pending 的 extraData 获取上下文
        @SuppressWarnings("unchecked")
        GameAction pending = state.getPendingAction();
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        Long targetId = Long.valueOf(extra.get("targetId").toString());
        GamePlayer target = findPlayerById(state, targetId);
        if (target == null) return failure("未找到目标");

        int damage = extra.get("damage") instanceof Integer ? (int) extra.get("damage") : 1;
        String natureStr = (String) extra.get("nature");
        String effectTypeStr = (String) extra.get("effectType");
        boolean isBlackSha = Boolean.TRUE.equals(extra.get("isBlackSha"));

        // 弃两张牌
        discardRandomCards(attacker, state, 2);
        state.addLog(attacker.getUsername() + " 发动贯石斧，弃两张牌强制命中");

        // 防具检查
        if (target.getArmor() != null) {
            CardType armorType = target.getArmor().getCardType();
            if (armorType == CardType.REN_WANG && isBlackSha) {
                state.addLog(target.getUsername() + " 的仁王盾使黑色杀无效");
                state.setPendingAction(null);
                return success("杀被仁王盾抵挡");
            }
            if (armorType == CardType.TENG_JIA && effectTypeStr == null) {
                state.addLog(target.getUsername() + " 的藤甲使普通杀无效");
                state.setPendingAction(null);
                return success("杀被藤甲抵挡");
            }
        }

        // 白银狮子
        if (target.getArmor() != null && target.getArmor().getCardType() == CardType.BAI_YIN) {
            damage = Math.min(damage, 1);
        }

        target.takeDamage(damage);
        state.addLog(target.getUsername() + " 受到" + damage + "点伤害（贯石斧）");

        // 藤甲火伤
        if (target.getArmor() != null && target.getArmor().getCardType() == CardType.TENG_JIA && "FIRE".equals(natureStr)) {
            target.takeDamage(1);
            state.addLog("藤甲使火伤害+1");
            fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, attacker, target, null, 1,
                    Map.of("effectType", "TENG_JIA_FIRE")));
        }

        ActionResult dying = checkDying(state, target);
        if (dying != null) return dying;

        state.checkGameOver();
        if (!state.isFinished()) {
            fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, attacker, target, null, damage,
                    Map.of("effectType", effectTypeStr != null ? effectTypeStr : "SHA")));
            if (state.getPendingAction() != null) {
                return success("命中，触发装备效果", state.getPendingAction());
            }
        }

        state.setPendingAction(null);
        return success("贯石斧命中，" + damage + "点伤害");
    }

    /**
     * 青龙偃月刀：追刀
     */
    private ActionResult handleQingLong(GameState state, Long userId, String cardId, GameAction pending) {
        if (cardId == null) {
            state.setPendingAction(null);
            state.addLog("不发动青龙偃月刀");
            return success("不发动");
        }

        GamePlayer attacker = findPlayerById(state, userId);
        if (attacker == null) return failure("未找到玩家");

        GameCard shaCard = findCard(attacker, cardId);
        if (shaCard == null || shaCard.getCardType() != CardType.SHA) {
            return failure("请选择一张杀");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        Long targetId = Long.valueOf(extra.get("targetId").toString());
        GamePlayer target = findPlayerById(state, targetId);
        if (target == null || !target.isAlive()) return failure("目标已死亡");

        attacker.removeHandCard(cardId);

        GameAction action = new GameAction();
        action.setActionType("RESPOND_SHAN");
        action.setSourcePlayerId(attacker.getUserId());
        List<String> shanCards = target.getHandCards().stream()
                .filter(c -> c.getCardType() == CardType.SHAN)
                .map(GameCard::getId).toList();
        action.setOptionalCardIds(shanCards);
        action.setOptionalCards(cardIdsToClientMap(state, shanCards));
        action.setOptionalTargetIds(Collections.singletonList(target.getUserId()));
        action.setMessage(target.getUsername() + " 请出闪（青龙偃月刀追刀）");
        action.setExtraData(Map.of("hasJiuEffect", false, "damage", 1, "nature", shaCard.getNature().name()));

        state.setPendingAction(action);
        state.addLog(attacker.getUsername() + " 发动青龙偃月刀追刀");
        return success("青龙偃月刀追刀", action);
    }

    /**
     * 寒冰剑：弃两张牌代替伤害
     */
    private ActionResult handleHanBing(GameState state, Long userId, String cardId, GameAction pending) {
        if (cardId == null) {
            // 不发动寒冰剑，正常造成伤害
            GamePlayer attacker = findPlayerById(state, userId);
            @SuppressWarnings("unchecked")
            Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
            GamePlayer target = findPlayerById(state, Long.valueOf(extra.get("targetId").toString()));
            if (target == null) return failure("未找到目标");

            int baseDmg = extra.get("damage") instanceof Integer ? (int) extra.get("damage") : 1;
            boolean hasJiu = Boolean.TRUE.equals(extra.get("hasJiuEffect"));
            String effectTypeStr = (String) extra.get("effectType");
            boolean isBlack = Boolean.TRUE.equals(extra.get("isBlack"));
            String natureStr = (String) extra.get("nature");

            int damage = baseDmg;
            if (hasJiu) {
                damage += 1;
                state.addLog("酒效果使伤害+1");
            }
            if (attacker.getWeapon() != null && attacker.getWeapon().getCardType() == CardType.GU_DING_DAO
                    && target.getHandCards().isEmpty()) {
                damage += 1;
                state.addLog("古锭刀效果：目标无手牌，伤害+1");
            }

            if (target.getArmor() != null) {
                CardType armorType = target.getArmor().getCardType();
                if (armorType == CardType.REN_WANG && isBlack) {
                    state.addLog(target.getUsername() + " 的仁王盾使黑色杀无效");
                    state.setPendingAction(null);
                    return success("杀被仁王盾抵挡");
                }
                if (armorType == CardType.TENG_JIA && effectTypeStr == null) {
                    state.addLog(target.getUsername() + " 的藤甲使普通杀无效");
                    state.setPendingAction(null);
                    return success("杀被藤甲抵挡");
                }
            }
            if (target.getArmor() != null && target.getArmor().getCardType() == CardType.BAI_YIN) {
                damage = Math.min(damage, 1);
            }

            target.takeDamage(damage);
            state.addLog(target.getUsername() + " 受到" + damage + "点伤害");

            if (target.getArmor() != null && target.getArmor().getCardType() == CardType.TENG_JIA && "FIRE".equals(natureStr)) {
                target.takeDamage(1);
                state.addLog("藤甲使火伤害+1");
                fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, attacker, target, null, 1,
                        Map.of("effectType", "TENG_JIA_FIRE")));
            }

            ActionResult dying = checkDying(state, target);
            if (dying != null) return dying;
            state.checkGameOver();

            if (!state.isFinished()) {
                fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, attacker, target, null, damage,
                        Map.of("effectType", effectTypeStr != null ? effectTypeStr : "SHA")));
            }

            state.setPendingAction(null);
            return success("命中，" + damage + "点伤害");
        }

        GamePlayer attacker = findPlayerById(state, userId);
        if (attacker == null) return failure("未找到玩家");

        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        Long targetId = Long.valueOf(extra.get("targetId").toString());
        GamePlayer target = findPlayerById(state, targetId);
        if (target == null) return failure("未找到目标");

        // 随机弃置目标两张牌
        discardRandomCards(target, state, 2);
        state.addLog(attacker.getUsername() + " 发动寒冰剑，弃置了" + target.getUsername() + "两张牌代替伤害");
        state.setPendingAction(null);
        return success("寒冰剑：弃牌代替伤害");
    }

    /**
     * 丈八蛇矛技能：将两张手牌当杀使用或打出
     */
    private ActionResult useZhangBaSkill(GameState state, GamePlayer player, SkillUseRequest req) {
        if (req.getSelectedCardIds() == null || req.getSelectedCardIds().size() != 2) {
            return failure("需要选择两张手牌");
        }
        // 验证两张牌都在手牌中
        List<GameCard> toRemove = new ArrayList<>();
        for (String cid : req.getSelectedCardIds()) {
            GameCard c = findCard(player, cid);
            if (c == null) return failure("卡牌不存在");
            toRemove.add(c);
        }
        // 移除并弃置两张牌
        for (GameCard c : toRemove) {
            player.removeHandCard(c.getId());
            state.discardCard(c);
        }
        state.addLog(player.getUsername() + " 发动丈八蛇矛，将两张牌当杀使用");

        if (req.isResponse()) {
            // 响应模式（RESPOND_SHA中作为杀打出）
            GameAction currentPending = state.getPendingAction();
            if (currentPending == null) return failure("没有待处理的响应");
            state.setPendingAction(null);
            return onShaPlayed(state, player, currentPending,
                    player.getUsername() + " 使用丈八蛇矛出杀");
        } else {
            // 主动出牌阶段使用
            if (req.getTargetUserId() == null) return failure("请选择目标");
            GamePlayer target = findPlayerById(state, Long.valueOf(req.getTargetUserId()));
            if (target == null || !target.isAlive()) return failure("目标无效");

            GameAction action = new GameAction();
            action.setActionType("RESPOND_SHAN");
            action.setSourcePlayerId(player.getUserId());

            List<String> shanCards = target.getHandCards().stream()
                    .filter(c -> c.getCardType() == CardType.SHAN)
                    .map(GameCard::getId)
                    .toList();
            action.setOptionalCardIds(shanCards);
            action.setOptionalCards(cardIdsToClientMap(state, shanCards));
            action.setOptionalTargetIds(Collections.singletonList(target.getUserId()));
            action.setMessage(target.getUsername() + " 请出闪");
            action.setExtraData(Map.of("hasJiuEffect", false, "damage", 1));

            state.setPendingAction(action);
            state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用丈八蛇矛转化的杀");
            return success("丈八蛇矛：杀已使用，等待响应", action);
        }
    }

    /**
     * 玩家使用主动技能（由 USE_SKILL 消息触发）
     */
    public ActionResult useSkill(GameState state, Long userId, SkillUseRequest request) {
        GamePlayer player = findPlayerById(state, userId);
        if (player == null) return failure("未找到玩家");

        SkillEffect skill = skillRegistry.getSkill(request.getSkillCode());
        if (skill == null) return failure("未知技能: " + request.getSkillCode());

        if (!skill.canUse(state, player, request)) {
            return failure("无法发动该技能");
        }

        return skill.execute(state, player, request);
    }

    /**
     * 处理选择目标卡牌（过河拆桥/顺手牵羊）
     * 现在由使用者选择公开装备，或随机处理目标手牌
     */
    @SuppressWarnings("unchecked")
    private ActionResult handleChooseTargetCard(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer player = findPlayerById(state, userId);
        if (player == null) return failure("未找到玩家");

        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        String effectType = extra != null ? (String) extra.get("effectType") : null;
        Object targetIdObj = extra != null ? extra.get("targetId") : null;
        Long targetId = targetIdObj != null ? Long.valueOf(targetIdObj.toString()) : null;
        GamePlayer target = targetId != null ? findPlayerById(state, targetId) : null;
        if (target == null) return failure("未找到目标玩家");

        if ("GUO_HE".equals(effectType)) {
            if (cardId != null && !"__RANDOM_HAND__".equals(cardId)) {
                // 使用者选择了指定装备弃置
                GameCard discardCard = findCard(target, cardId);
                if (discardCard != null && discardCard.getCardType().isEquipment()) {
                    target.unequip(discardCard.getCardType());
                    state.discardCard(discardCard);
                    state.addLog(player.getUsername() + " 弃置了" + target.getUsername() +
                            " 的 " + discardCard.getCardType().getDisplayName());
                }
            } else {
                // 随机弃置一张手牌
                if (!target.getHandCards().isEmpty()) {
                    int idx = new Random().nextInt(target.getHandCards().size());
                    GameCard discardCard = target.getHandCards().get(idx);
                    target.removeHandCard(discardCard.getId());
                    state.discardCard(discardCard);
                    state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用过河拆桥，弃置其一张手牌");
                }
            }
            state.setPendingAction(null);
            return success("过河拆桥成功");
        } else if ("SHUN_SHOU".equals(effectType)) {
            if (cardId != null && !"__RANDOM_HAND__".equals(cardId)) {
                // 使用者选择了指定装备获得
                GameCard stealCard = findCard(target, cardId);
                if (stealCard != null && stealCard.getCardType().isEquipment()) {
                    target.unequip(stealCard.getCardType());
                    player.drawCards(Collections.singletonList(stealCard));
                    state.addLog(player.getUsername() + " 获得了" + target.getUsername() +
                            " 的 " + stealCard.getCardType().getDisplayName());
                }
            } else {
                // 随机获得一张手牌
                if (!target.getHandCards().isEmpty()) {
                    int idx = new Random().nextInt(target.getHandCards().size());
                    GameCard stealCard = target.getHandCards().get(idx);
                    target.removeHandCard(stealCard.getId());
                    player.drawCards(Collections.singletonList(stealCard));
                    state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用顺手牵羊，获得其一张手牌");
                }
            }
            state.setPendingAction(null);
            return success("顺手牵羊成功");
        }

        state.setPendingAction(null);
        return failure("未知效果");
    }

    /**
     * 处理五谷丰登选牌
     * 先校验再执行，禁止非当前玩家选牌，禁止同一玩家重复选牌
     */
    @SuppressWarnings("unchecked")
    private ActionResult handleChooseWuguCard(GameState state, Long userId, String cardId, GameAction pending) {
        try {
            GamePlayer player = findPlayerById(state, userId);
            if (player == null) return failure("未找到玩家");

            Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
            if (extra == null) return failure("五谷丰登数据异常");

            // 1. 验证当前玩家是否是应选玩家
            List<?> pickerOrder = (List<?>) extra.get("pickerOrder");
            int currentIndex = extra.get("pickerIndex") instanceof Number
                    ? ((Number) extra.get("pickerIndex")).intValue() : 0;
            if (pickerOrder == null || currentIndex >= pickerOrder.size()) {
                return failure("五谷丰登选牌顺序数据异常");
            }
            Object expectedId = pickerOrder.get(currentIndex);
            Long expectedPickerId = expectedId instanceof Number ? ((Number) expectedId).longValue() : null;
            if (expectedPickerId == null || !expectedPickerId.equals(userId)) {
                return failure("当前不是您的选牌回合，请等待其他玩家选择");
            }

            // 2. 验证 cardId 在 tempCards 中是否存在
            GameCard selectedCard = null;
            if (cardId != null) {
                for (GameCard c : state.getTempCards()) {
                    if (c.getId().equals(cardId)) {
                        selectedCard = c;
                        break;
                    }
                }
            }
            if (selectedCard == null) {
                return failure("无效的选牌，找不到该卡牌");
            }

            // 3. 所有验证通过，执行状态变更
            state.getTempCards().remove(selectedCard);
            player.drawCards(Collections.singletonList(selectedCard));
            state.addLog(player.getUsername() + " 选择了 " + selectedCard.getCardType().getDisplayName());

            // 4. 切换到下一玩家或结束
            int nextIndex = currentIndex + 1;
            if (nextIndex < pickerOrder.size() && !state.getTempCards().isEmpty()) {
                Object nextId = pickerOrder.get(nextIndex);
                Long nextPickerId = nextId instanceof Number ? ((Number) nextId).longValue() : null;
                if (nextPickerId == null) {
                    return failure("选牌顺序数据异常");
                }
                List<String> remainingIds = state.getTempCards().stream().map(GameCard::getId).toList();
                List<Map<String, Object>> remainingInfos = state.getTempCards().stream().map(this::cardToClientMap).toList();

                GameAction newAction = new GameAction();
                newAction.setActionType("CHOOSE_WUGU_CARD");
                newAction.setSourceCardId(pending.getSourceCardId());
                newAction.setSourcePlayerId(pending.getSourcePlayerId());
                newAction.setOptionalCardIds(remainingIds);
                newAction.setOptionalCards(remainingInfos);
                newAction.setMessage("请选择一张牌（五谷丰登）");
                newAction.setOptionalTargetIds(Collections.singletonList(nextPickerId));
                newAction.setExtraData(Map.of(
                        "pickerOrder", pickerOrder,
                        "pickerIndex", nextIndex
                ));

                state.setPendingAction(newAction);
                return success("选牌成功，等待下一位玩家选牌", newAction);
            }

            // 5. 所有人选完，剩余牌放入弃牌堆
            for (GameCard remaining : state.getTempCards()) {
                state.discardCard(remaining);
                state.addLog(remaining.getCardType().getDisplayName() + " 进入弃牌堆");
            }
            state.getTempCards().clear();
            state.setPendingAction(null);
            return success("五谷丰登选牌完成");
        } catch (Exception e) {
            log.error("五谷丰登选牌异常", e);
            return failure("五谷丰登选牌异常，请重试");
        }
    }

    /**
     * 处理弃牌阶段
     */
    private ActionResult handleDiscardPhase(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer player = findPlayerById(state, userId);
        if (player == null) return failure("未找到玩家");

        // 简化：只弃一张，client需要多次调用
        if (cardId != null) {
            state.discardHandCard(player, cardId);
        }

        int remaining = player.getDiscardCount();
        if (remaining > 0) {
            // 还需要继续弃牌
            GameAction newAction = new GameAction();
            newAction.setActionType("DISCARD");
            newAction.setSourcePlayerId(player.getUserId());
            newAction.setDiscardCount(remaining);
            newAction.setMessage("还需弃置 " + remaining + " 张牌");
            newAction.setOptionalTargetIds(Collections.singletonList(player.getUserId()));

            List<String> cardIds = player.getHandCards().stream()
                    .map(GameCard::getId).toList();
            newAction.setOptionalCardIds(cardIds);

            state.setPendingAction(newAction);
            return success("已弃牌，还需弃" + remaining + "张", newAction);
        } else {
            state.nextPhase();
            state.setPendingAction(null);
            return success("弃牌完成");
        }
    }

    /**
     * 跳过待处理动作（例如玩家不想出闪）
     */
    public ActionResult skipResponse(GameState state, Long userId) {
        GameAction pending = state.getPendingAction();
        if (pending == null) return failure("没有待处理的响应");

        if (!pending.getOptionalTargetIds().contains(userId)) {
            return failure("不是你需要响应");
        }

        // 调用handleResponse with null cardId
        return handleResponse(state, userId, null, null);
    }

    /**
     * 结束当前回合（强制进入弃牌阶段）
     */
    public ActionResult endPlayPhase(GameState state, Long userId) {
        if (!state.getCurrentPlayer().getUserId().equals(userId)) {
            return failure("不是你的回合");
        }
        if (!"PLAY".equals(state.getPhase())) {
            return failure("不在出牌阶段");
        }
        state.setPhase("DISCARD");
        return processActionPhaseResult(state, state.getCurrentPlayer());
    }

    /**
     * 检查玩家是否濒死，如果是则设置 DYING_REQUIRE_TAO pending action
     * @return 如果有濒死action返回ActionResult，否则返回null（继续正常流程）
     */
    private ActionResult checkDying(GameState state, GamePlayer player) {
        if (player.getCurrentHp() > 0) return null;

        // 玩家处于濒死（takeDamage已设置alive=false），临时复活以便求桃
        player.setAlive(true);
        List<String> taoCards = player.getHandCards().stream()
                .filter(c -> c.getCardType() == CardType.TAO)
                .map(GameCard::getId)
                .toList();

        if (taoCards.isEmpty()) {
            // 没有桃，直接死亡
            player.setAlive(false);
            state.addLog(player.getUsername() + " 没有桃，死亡");
            return null;
        }

        GameAction action = new GameAction();
        action.setActionType("DYING_REQUIRE_TAO");
        action.setSourcePlayerId(player.getUserId());
        action.setOptionalCardIds(taoCards);
        action.setOptionalCards(cardIdsToClientMap(state, taoCards));
        action.setOptionalTargetIds(Collections.singletonList(player.getUserId()));
        action.setMessage(player.getUsername() + " 濒死，请使用桃救自己");
        action.setExtraData(Map.of("dyingPlayerId", player.getUserId()));

        state.setPendingAction(action);
        state.addLog(player.getUsername() + " 进入濒死状态");
        return success("濒死求桃", action);
    }

    /**
     * 处理濒死求桃响应
     */
    private ActionResult handleDying(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer player = findPlayerById(state, userId);
        if (player == null) return failure("未找到玩家");

        if (cardId != null) {
            // 出桃
            GameCard tao = findCard(player, cardId);
            if (tao == null || tao.getCardType() != CardType.TAO) {
                return failure("需要出桃");
            }

            player.removeHandCard(cardId);
            state.discardCard(tao);
            player.setAlive(true);
            player.heal(1);
            state.addLog(player.getUsername() + " 使用桃自救，回复1点体力");

            if (player.getCurrentHp() > 0) {
                state.setPendingAction(null);
                state.addLog(player.getUsername() + " 脱离濒死");
                return success("自救成功");
            }

            // 回血后仍然HP=0，继续检查
            return checkDyingResult(state, player);
        } else {
            // 放弃 -> 死亡
            player.setAlive(false);
            state.setPendingAction(null);
            state.addLog(player.getUsername() + " 死亡");
            state.checkGameOver();
            return success("玩家死亡");
        }
    }

    /**
     * 濒死状态递归检查（使用一个桃后仍濒死）
     */
    private ActionResult checkDyingResult(GameState state, GamePlayer player) {
        List<String> taoCards = player.getHandCards().stream()
                .filter(c -> c.getCardType() == CardType.TAO)
                .map(GameCard::getId)
                .toList();

        if (taoCards.isEmpty()) {
            player.setAlive(false);
            state.setPendingAction(null);
            state.addLog(player.getUsername() + " 死亡");
            state.checkGameOver();
            return success("玩家死亡");
        }

        GameAction action = new GameAction();
        action.setActionType("DYING_REQUIRE_TAO");
        action.setSourcePlayerId(player.getUserId());
        action.setOptionalCardIds(taoCards);
        action.setOptionalCards(cardIdsToClientMap(state, taoCards));
        action.setOptionalTargetIds(Collections.singletonList(player.getUserId()));
        action.setMessage(player.getUsername() + " 仍处于濒死，请使用桃");
        action.setExtraData(Map.of("dyingPlayerId", player.getUserId()));

        state.setPendingAction(action);
        return success("仍需桃", action);
    }

    // ============ 辅助方法 ============

    private GamePlayer findPlayer(GameState state, Long userId) {
        return state.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst().orElse(null);
    }

    private GamePlayer findPlayerById(GameState state, Long userId) {
        return state.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst().orElse(null);
    }

    private GameCard findCard(GamePlayer player, String cardId) {
        // 先从手牌找
        for (GameCard c : player.getHandCards()) {
            if (c.getId().equals(cardId)) return c;
        }
        // 再从装备区找
        if (player.getWeapon() != null && player.getWeapon().getId().equals(cardId))
            return player.getWeapon();
        if (player.getArmor() != null && player.getArmor().getId().equals(cardId))
            return player.getArmor();
        if (player.getPlusHorse() != null && player.getPlusHorse().getId().equals(cardId))
            return player.getPlusHorse();
        if (player.getMinusHorse() != null && player.getMinusHorse().getId().equals(cardId))
            return player.getMinusHorse();
        return null;
    }

    private GameCard findCardInDrawPile(GameState state, String cardId) {
        return state.getDrawPile().stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst().orElse(null);
    }

    /**
     * 在游戏状态中通过ID查找卡牌（从手牌、装备区、判定区、牌堆、弃牌堆中查找）
     */
    private GameCard findCardInGameState(GameState state, String cardId) {
        for (GamePlayer p : state.getPlayers()) {
            GameCard c = findCard(p, cardId);
            if (c != null) return c;
            // 也查找判定区
            for (GameCard judge : p.getJudgeArea()) {
                if (judge.getId().equals(cardId)) return judge;
            }
        }
        // 查找牌堆
        for (GameCard c : state.getDrawPile()) {
            if (c.getId().equals(cardId)) return c;
        }
        // 查找弃牌堆
        for (GameCard c : state.getDiscardPile()) {
            if (c.getId().equals(cardId)) return c;
        }
        return null;
    }

    /**
     * 从卡牌ID列表获取前端展示信息
     */
    private List<Map<String, Object>> cardIdsToClientMap(GameState state, List<String> cardIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : cardIds) {
            GameCard c = findCardInGameState(state, id);
            if (c != null) {
                result.add(cardToClientMap(c));
            }
        }
        return result;
    }

    private Map<String, Object> cardToClientMap(GameCard card) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (card == null) {
            m.put("id", "");
            m.put("displayName", "未知卡牌");
            return m;
        }
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

    private List<Map<String, Object>> cardsToClientMap(List<GameCard> cards) {
        return cards.stream().map(this::cardToClientMap).toList();
    }

    /**
     * 判断卡牌类型是否为对敌方使用的进攻性牌（不能以自己为目标）
     */
    private boolean isOffensiveCard(CardType type) {
        return type == CardType.SHA || type == CardType.JUE_DOU
                || type == CardType.GUO_HE || type == CardType.SHUN_SHOU;
    }

    /**
     * 统计玩家可弃置的牌总数（手牌+装备）
     */
    private int countDiscardableCards(GamePlayer player) {
        int count = player.getHandCards().size();
        if (player.getWeapon() != null) count++;
        if (player.getArmor() != null) count++;
        if (player.getPlusHorse() != null) count++;
        if (player.getMinusHorse() != null) count++;
        return count;
    }

    /**
     * 随机弃置玩家指定数量的牌（优先手牌，不足时弃装备）
     */
    private void discardRandomCards(GamePlayer player, GameState state, int count) {
        List<GameCard> allCards = new ArrayList<>(player.getHandCards());
        // 先加装备，这样装备在后、手牌在前，随机时均匀分布
        if (player.getWeapon() != null) allCards.add(player.getWeapon());
        if (player.getArmor() != null) allCards.add(player.getArmor());
        if (player.getPlusHorse() != null) allCards.add(player.getPlusHorse());
        if (player.getMinusHorse() != null) allCards.add(player.getMinusHorse());

        Collections.shuffle(allCards, new Random());
        int toDiscard = Math.min(count, allCards.size());
        for (int i = 0; i < toDiscard; i++) {
            GameCard c = allCards.get(i);
            if (player.getHandCards().contains(c)) {
                player.removeHandCard(c.getId());
            } else {
                player.unequip(c.getCardType());
            }
            state.discardCard(c);
        }
    }

    private ActionResult processActionPhaseResult(GameState state, GamePlayer current) {
        if (state.getPendingAction() != null) {
            return success("需要继续处理待响应动作", state.getPendingAction());
        }
        GameAction action = processPhase(state);
        if (action != null) {
            return success("阶段处理完成", action);
        }
        return success("阶段处理完成");
    }

    private ActionResult success(String message) {
        return success(message, null);
    }

    private ActionResult success(String message, GameAction action) {
        return new ActionResult(true, message, action);
    }

    private ActionResult failure(String message) {
        return new ActionResult(false, message, null);
    }

    /**
     * 触发游戏事件 — 通知所有已注册的 TriggerEffect
     * 目前为空实现，技能注册后生效
     */
    private void fireEvent(GameEvent event) {
        if (!skillRegistry.hasTriggers(event.getType())) return;
        for (var trigger : skillRegistry.getTriggers(event.getType())) {
            if (trigger.canTrigger(event.getState(), event)) {
                trigger.trigger(event.getState(), event);
            }
        }
    }

    /**
     * 动作结果
     */
    public record ActionResult(boolean success, String message, GameAction pendingAction) {
    }
}