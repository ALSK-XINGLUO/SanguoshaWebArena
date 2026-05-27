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
        trickEffects.put(CardType.HUO_GONG, this::useHuoGong);

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
                // 处理延时锦囊判定：逐个判定，判定阶段才打开无懈可击窗口
                while (!current.getJudgeArea().isEmpty()) {
                    GameCard judgeCard = current.getJudgeArea().get(0);
                    current.getJudgeArea().remove(0);
                    state.getTempCards().clear();
                    state.getTempCards().add(judgeCard);

                    // 判定阶段打开无懈可击窗口
                    if (hasAnyWuxie(state)) {
                        Map<String, Object> extraData = new HashMap<>();
                        extraData.put("trickCardId", judgeCard.getId());
                        extraData.put("trickCardType", judgeCard.getCardType().name());
                        extraData.put("sourcePlayerId", current.getUserId());
                        extraData.put("originalTargetUserId", String.valueOf(current.getUserId()));
                        extraData.put("originalTargetCardId", null);
                        extraData.put("pendingTargetUserIds", new ArrayList<String>());
                        extraData.put("currentTargetIndex", 0);
                        extraData.put("wuxieStack", new ArrayList<String>());
                        extraData.put("respondedSkipIds", new ArrayList<Long>());
                        extraData.put("isDelayTrickJudgment", true);
                        extraData.put("isAoe", false);

                        List<Long> queue = state.getAlivePlayers().stream()
                                .map(GamePlayer::getUserId)
                                .collect(java.util.stream.Collectors.toList());
                        extraData.put("responderQueue", queue);
                        extraData.put("currentResponderIndex", 0);

                        ActionResult ar = advanceToNextWuxieResponder(state, extraData, current.getUserId());
                        GameAction pendingFromWuxie = ar.pendingAction();
                        if (pendingFromWuxie != null) {
                            return pendingFromWuxie;
                        }
                        // 队列无人有资格响应或无懈已结算，继续下一张判定牌
                        state.getTempCards().clear();

                        // [GUARD] 无懈可击流程结束后，防御性清理残留的 WAIT_WUXIE_RESPONSE
                        if (state.getPendingAction() != null && "WAIT_WUXIE_RESPONSE".equals(state.getPendingAction().getActionType())) {
                            log.warn("[WUXIE GUARD] JUDGE phase: stale WAIT_WUXIE_RESPONSE after wuxie chain resolution, clearing");
                            state.setPendingAction(null);
                        }

                        if (state.getPendingAction() != null) {
                            return state.getPendingAction();
                        }
                        continue;
                    }

                    // 无人有无懈，直接判定
                    applyDelayTrickEffect(state, current, judgeCard);
                    state.getTempCards().clear();
                    // applyDelayTrickEffect 可能设置了濒死 pending action（闪电传导）
                    if (state.getPendingAction() != null) {
                        return state.getPendingAction();
                    }
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
     * 玩家出牌 - 使用卡牌效果注册表分发（单目标版本）
     */
    public ActionResult playCard(GameState state, Long userId, String cardId,
                                  String targetUserId, String targetCardId) {
        List<String> targetUserIds = targetUserId != null ?
                Collections.singletonList(targetUserId) : null;
        return playCard(state, userId, cardId, targetUserId, targetCardId, targetUserIds);
    }

    /**
     * 玩家出牌 - 使用卡牌效果注册表分发（支持多目标）
     */
    public ActionResult playCard(GameState state, Long userId, String cardId,
                                  String targetUserId, String targetCardId,
                                  List<String> targetUserIds) {
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

        // 铁索连环特殊处理（先校验，再进无懈窗口）
        if (type == CardType.TIE_SUO) {
            String tieSuoErr = validateTrickUse(state, player, type, targetUserId, targetUserIds);
            if (tieSuoErr != null) return failure(tieSuoErr);
            if (targetUserIds == null || targetUserIds.isEmpty()) {
                // 重铸：不触发无懈可击
                player.removeHandCard(card.getId());
                state.discardCard(card);
                List<GameCard> drawn = state.drawCards(1);
                if (!drawn.isEmpty()) {
                    player.drawCards(drawn);
                    state.addLog(player.getUsername() + " 重铸铁索连环，摸了1张牌");
                }
                return success("铁索连环重铸成功");
            }
            // 多目标铁索连环：走无懈可击流程（逐目标）
            return playTieSuoWithWuxieCheck(state, player, card, targetUserIds);
        }

        // 延时锦囊：直接放入判定区，出牌阶段不触发无懈可击（无懈在判定阶段）
        if (type.isDelayTrick()) {
            String delayErr = validateDelayTrickUse(state, player, card, targetUserId);
            if (delayErr != null) return failure(delayErr);

            // 设置效果目标
            if (targetUserId == null) {
                targetUserId = type == CardType.SHAN_DIAN
                        ? String.valueOf(player.getUserId())   // 闪电对自己
                        : String.valueOf(state.getOpponent().getUserId()); // 乐/兵对对手
            }

            CardEffect effect = this::playDelayTrick;
            ActionResult result = effect.execute(state, player, card, targetUserId, targetCardId);
            if (result.success()) {
                fireEvent(new GameEvent(GameEventType.CARD_USED, state, player, null, card, 0, null));
            }
            return result;
        }

        // 策略模式分发
        CardEffect effect = null;
        if (type.isBasic()) {
            effect = basicEffects.get(type);
        } else if (type.isTrick()) {
            effect = trickEffects.get(type);
        } else if (type.isEquipment()) {
            effect = this::playEquipmentCard;
        }

        if (effect == null) {
            return failure("不支持的卡牌类型");
        }

        // 锦囊牌：先校验合法性，再进入无懈窗口
        if (type.isTrick()) {
            // 借刀杀人：双目标特殊处理（借刀目标 + 杀目标）
            if (type == CardType.JIE_DAO) {
                return playJieDaoWithWuxie(state, player, card, targetUserId, targetUserIds);
            }
            String trickErr = validateTrickUse(state, player, type, targetUserId, targetUserIds);
            if (trickErr != null) return failure(trickErr);
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

        // 朱雀羽扇：普通杀可选择转为火杀
        boolean hasZhuQue = player.getWeapon() != null &&
                player.getWeapon().getCardType() == CardType.ZHU_QUE;

        if (hasZhuQue && card.getNature() == GameCard.Nature.NORMAL) {
            // 普通杀 + 朱雀羽扇 → 询问是否发动（所有上下文存在extraData，不依赖tempCards）
            player.removeHandCard(card.getId());

            GameAction zhuQueAction = new GameAction();
            zhuQueAction.setActionType("WAIT_EQUIP_TRIGGER");
            zhuQueAction.setSourcePlayerId(player.getUserId());
            zhuQueAction.setOptionalTargetIds(Collections.singletonList(player.getUserId()));
            zhuQueAction.setMessage("是否发动朱雀羽扇，将此【杀】改为【火杀】？");

            Map<String, Object> extraData = new HashMap<>();
            extraData.put("skillCode", "ZHU_QUE_YU_SHAN");
            extraData.put("cardId", card.getId());
            extraData.put("isBlack", card.isBlack());
            extraData.put("targetUserId", targetUserId);
            extraData.put("hasJiuEffect", hasJiuEffect);
            extraData.put("hasQingGang", hasQingGang);
            extraData.put("hasZhugeNu", hasZhugeNu);
            zhuQueAction.setExtraData(extraData);

            state.setPendingAction(zhuQueAction);
            state.addLog(player.getUsername() + " 可使用朱雀羽扇转化火杀");
            return success("请选择是否发动朱雀羽扇", zhuQueAction);
        }

        // 正常杀结算：移除手牌并创建出闪响应
        player.removeHandCard(card.getId());
        return createShaRespondAction(state, player, card.getId(), card.isBlack(), target, targetUserId,
                card.getNature(), hasJiuEffect, hasQingGang, hasZhugeNu);
    }

    /**
     * 创建杀结算的RESPOND_SHAN pending action（useSha 和 朱雀羽扇共用）
     * @param nature 实际生效的伤害属性（ZHU_QUE可能覆盖为FIRE）
     */
    private ActionResult createShaRespondAction(GameState state, GamePlayer player,
                                                 String shaCardId, boolean shaIsBlack,
                                                 GamePlayer target, String targetUserId,
                                                 GameCard.Nature nature,
                                                 boolean hasJiuEffect, boolean hasQingGang, boolean hasZhugeNu) {
        // 先记录杀已使用（无论是否被防具抵挡）
        String shaDisplayName = switch (nature) {
            case FIRE -> "火杀";
            case THUNDER -> "雷杀";
            default -> "杀";
        };
        state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了" + shaDisplayName);
        if (!hasZhugeNu) {
            player.setUsedShaThisTurn(true);
        }
        player.setShaCountThisTurn(player.getShaCountThisTurn() + 1);

        // 仁王盾在出闪之前判断：黑色普通杀直接被仁王盾抵挡
        if (nature == GameCard.Nature.NORMAL && shaIsBlack
                && target.getArmor() != null && target.getArmor().getCardType() == CardType.REN_WANG
                && !hasQingGang) {
            state.addLog(target.getUsername() + " 的仁王盾使黑色杀无效");
            return success("杀被仁王盾抵挡");
        }

        GameAction action = new GameAction();
        action.setActionType("RESPOND_SHAN");
        action.setSourceCardId(shaCardId);
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
        shaExtra.put("nature", nature.name());
        shaExtra.put("isBlack", shaIsBlack);
        shaExtra.put("hasQingGang", hasQingGang);
        shaExtra.put("shaResolveId", UUID.randomUUID().toString());
        action.setExtraData(shaExtra);

        state.setPendingAction(action);

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
     * 过河拆桥 — 不能对自己使用，不暴露对手手牌。
     * 目标区域支持：装备区（公开）、判定区（公开）、手牌区（随机一张）。
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
        boolean hasJudgeCards = !target.getJudgeArea().isEmpty();
        if (!hasHandCards && !hasEquipment && !hasJudgeCards) return failure("目标没有可拆的牌");

        player.removeHandCard(card.getId());
        state.discardCard(card);

        // 始终展示所有可选牌（除非只有手牌）
        boolean hasVisibleCards = hasEquipment || hasJudgeCards;
        if (hasVisibleCards) {
            List<String> choiceIds = new ArrayList<>();
            List<Map<String, Object>> choiceCards = new ArrayList<>();
            if (target.getWeapon() != null) {
                choiceIds.add(target.getWeapon().getId());
                choiceCards.add(cardToClientMap(target.getWeapon()));
            }
            if (target.getArmor() != null) {
                choiceIds.add(target.getArmor().getId());
                choiceCards.add(cardToClientMap(target.getArmor()));
            }
            if (target.getPlusHorse() != null) {
                choiceIds.add(target.getPlusHorse().getId());
                choiceCards.add(cardToClientMap(target.getPlusHorse()));
            }
            if (target.getMinusHorse() != null) {
                choiceIds.add(target.getMinusHorse().getId());
                choiceCards.add(cardToClientMap(target.getMinusHorse()));
            }
            // 添加判定区牌
            for (GameCard jc : target.getJudgeArea()) {
                choiceIds.add(jc.getId());
                choiceCards.add(cardToClientMap(jc));
            }
            // 添加一个随机弃手牌的选项
            if (hasHandCards) {
                choiceIds.add("__RANDOM_HAND__");
                choiceCards.add(Map.of("id", "__RANDOM_HAND__", "displayName", "随机一张手牌",
                        "category", "特殊", "type", "RANDOM"));
            }

            GameAction action = new GameAction();
            action.setActionType("CHOOSE_TARGET_CARD");
            action.setSourceCardId(card.getId());
            action.setSourcePlayerId(player.getUserId());
            action.setOptionalCardIds(choiceIds);
            action.setOptionalCards(choiceCards);
            action.setOptionalTargetIds(Collections.singletonList(player.getUserId()));
            action.setMessage("请选择要弃置的牌（过河拆桥）");
            action.setExtraData(Map.of("effectType", "GUO_HE", "targetId", target.getUserId()));
            state.setPendingAction(action);
            state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了过河拆桥");
            return success("过河拆桥已使用，请选择要弃置的牌", action);
        }

        // 无可见牌（装备/判定区）：随机弃一张手牌
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
     * 顺手牵羊 — 不能对自己使用，不暴露对手手牌。
     * 目标区域支持：装备区（公开）、判定区（公开）、手牌区（随机一张）。
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
        boolean hasJudgeCards = !target.getJudgeArea().isEmpty();
        if (!hasHandCards && !hasEquipment && !hasJudgeCards) return failure("目标没有可顺的牌");

        player.removeHandCard(card.getId());
        state.discardCard(card);

        // 始终展示所有可选牌（除非只有手牌）
        boolean hasVisibleCards = hasEquipment || hasJudgeCards;
        if (hasVisibleCards) {
            List<String> choiceIds = new ArrayList<>();
            List<Map<String, Object>> choiceCards = new ArrayList<>();
            if (target.getWeapon() != null) {
                choiceIds.add(target.getWeapon().getId());
                choiceCards.add(cardToClientMap(target.getWeapon()));
            }
            if (target.getArmor() != null) {
                choiceIds.add(target.getArmor().getId());
                choiceCards.add(cardToClientMap(target.getArmor()));
            }
            if (target.getPlusHorse() != null) {
                choiceIds.add(target.getPlusHorse().getId());
                choiceCards.add(cardToClientMap(target.getPlusHorse()));
            }
            if (target.getMinusHorse() != null) {
                choiceIds.add(target.getMinusHorse().getId());
                choiceCards.add(cardToClientMap(target.getMinusHorse()));
            }
            // 添加判定区牌
            for (GameCard jc : target.getJudgeArea()) {
                choiceIds.add(jc.getId());
                choiceCards.add(cardToClientMap(jc));
            }
            // 添加一个随机获得手牌的选项
            if (hasHandCards) {
                choiceIds.add("__RANDOM_HAND__");
                choiceCards.add(Map.of("id", "__RANDOM_HAND__", "displayName", "随机一张手牌",
                        "category", "特殊", "type", "RANDOM"));
            }

            GameAction action = new GameAction();
            action.setActionType("CHOOSE_TARGET_CARD");
            action.setSourceCardId(card.getId());
            action.setSourcePlayerId(player.getUserId());
            action.setOptionalCardIds(choiceIds);
            action.setOptionalCards(choiceCards);
            action.setOptionalTargetIds(Collections.singletonList(player.getUserId()));
            action.setMessage("请选择要获得的牌（顺手牵羊）");
            action.setExtraData(Map.of("effectType", "SHUN_SHOU", "targetId", target.getUserId()));
            state.setPendingAction(action);
            state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了顺手牵羊");
            return success("顺手牵羊已使用，请选择要获得的牌", action);
        }

        // 无可见牌：随机获得一张手牌
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
     * 借刀杀人play入口（双目标：借刀目标 + 杀目标）
     * 先校验合法性，再进入无懈可击窗口。
     */
    private ActionResult playJieDaoWithWuxie(GameState state, GamePlayer player, GameCard card,
                                              String targetUserId, List<String> targetUserIds) {
        // 从 targetUserIds 解析双目标
        String jieDaoTargetId;
        String shaTargetId;
        if (targetUserIds != null && targetUserIds.size() >= 2) {
            jieDaoTargetId = targetUserIds.get(0);
            shaTargetId = targetUserIds.get(1);
        } else {
            return failure("借刀杀人需要选择两个目标：借刀目标和杀的目标");
        }

        GamePlayer jieDaoTarget = findPlayerById(state, Long.valueOf(jieDaoTargetId));
        GamePlayer shaTarget = findPlayerById(state, Long.valueOf(shaTargetId));
        if (jieDaoTarget == null || !jieDaoTarget.isAlive()) return failure("借刀目标无效");
        if (shaTarget == null || !shaTarget.isAlive()) return failure("杀的目标无效");
        if (jieDaoTarget.getUserId().equals(player.getUserId())) return failure("不能对自己使用借刀杀人");
        if (shaTarget.getUserId().equals(jieDaoTarget.getUserId())) return failure("杀的目标不能是借刀目标自己");
        if (jieDaoTarget.getWeapon() == null) return failure("借刀杀人：目标没有武器");
        // 验证杀目标在借刀目标攻击范围内
        if (!jieDaoTarget.canAttack(shaTarget, false)) {
            return failure("杀的目标不在" + jieDaoTarget.getUsername() + "的攻击范围内");
        }

        // 校验通过，移除手牌并进入无懈可击窗口
        player.removeHandCard(card.getId());
        state.discardCard(card);

        // 构建效果执行器：无懈通过后执行借刀效果
        CardEffect jdEffect = (st, pl, cd, tgtId, tgtCardId) -> {
            GamePlayer jdTarget = findPlayerById(st, Long.valueOf(jieDaoTargetId));
            GamePlayer sTarget = findPlayerById(st, Long.valueOf(shaTargetId));
            if (jdTarget == null) return failure("借刀目标已不存在");
            if (sTarget == null) return failure("杀目标已不存在");

            // 创建 RESPOND_SHA：要求借刀目标对杀目标出一张杀
            List<String> shaCardsList = jdTarget.getHandCards().stream()
                    .filter(c -> c.getCardType() == CardType.SHA)
                    .map(GameCard::getId)
                    .toList();

            if (shaCardsList.isEmpty()) {
                // 没有杀可出：直接失去武器
                GameCard weapon = removeEquipment(jdTarget, st, jdTarget.getWeapon().getCardType());
                if (weapon != null) {
                    pl.drawCards(Collections.singletonList(weapon));
                    st.addLog(pl.getUsername() + " 获得了" + jdTarget.getUsername() + " 的 " + weapon.getCardType().getDisplayName());
                }
                st.setPendingAction(null);
                return success("借刀杀人：借刀目标无杀，失去武器");
            }

            GameAction jdAction = new GameAction();
            jdAction.setActionType("RESPOND_SHA");
            jdAction.setSourceCardId(cd.getId());
            jdAction.setSourcePlayerId(pl.getUserId());
            jdAction.setOptionalCardIds(shaCardsList);
            jdAction.setOptionalCards(cardIdsToClientMap(st, shaCardsList));
            jdAction.setOptionalTargetIds(Collections.singletonList(jdTarget.getUserId()));
            jdAction.setMessage("请出杀，否则失去武器（借刀杀人）");
            jdAction.setExtraData(Map.of(
                    "effectType", "JIE_DAO",
                    "weaponId", jdTarget.getWeapon().getId(),
                    "jieDaoTargetId", jieDaoTargetId,
                    "shaTargetId", shaTargetId
            ));

            st.setPendingAction(jdAction);
            st.addLog(pl.getUsername() + " 对 " + jdTarget.getUsername() + " 使用了借刀杀人，要求对 " + sTarget.getUsername() + " 出杀");
            return success("借刀杀人已使用", jdAction);
        };

        // 进入无懈可击窗口（以借刀目标作为无懈的原始目标）
        return playTrickWithWuxieCheck(state, player, card, CardType.JIE_DAO, jdEffect,
                jieDaoTargetId, shaTargetId);
    }

    /**
     * 使用装备牌
     */
    private ActionResult playEquipmentCard(GameState state, GamePlayer player, GameCard card,
                                           String targetUserId, String targetCardId) {
        player.removeHandCard(card.getId());

        // 如果有同类型装备，先卸下（触发失去装备效果，如白银狮子回血）
        CardType type = card.getCardType();
        GameCard old = removeEquipment(player, state, type);
        if (old != null) {
            state.discardCard(old);
            state.addLog(player.getUsername() + " 替换了" + old.getCardType().getDisplayName());
        }

        player.equip(card);
        state.addLog(player.getUsername() + " 装备了" + card.getCardType().getDisplayName());

        return success("装备成功");
    }

    /**
     * 火攻 - 第一步：目标选择一张手牌展示
     */
    private ActionResult useHuoGong(GameState state, GamePlayer player, GameCard card,
                                    String targetUserId, String targetCardId) {
        GamePlayer target = findPlayerById(state, targetUserId != null ?
                Long.valueOf(targetUserId) : state.getOpponent().getUserId());
        if (target == null || !target.isAlive()) return failure("目标无效");
        if (target.getHandCards().isEmpty()) return failure("目标没有手牌，无法火攻");

        player.removeHandCard(card.getId());
        state.discardCard(card);

        // 目标选择一张手牌展示
        List<String> targetCardIds = target.getHandCards().stream()
                .map(GameCard::getId).toList();

        GameAction action = new GameAction();
        action.setActionType("HUO_GONG_SHOW");
        action.setSourcePlayerId(player.getUserId());
        action.setOptionalCardIds(targetCardIds);
        action.setOptionalCards(cardIdsToClientMap(state, targetCardIds));
        action.setOptionalTargetIds(Collections.singletonList(target.getUserId()));
        action.setMessage("请选择一张手牌展示（火攻）");

        Map<String, Object> extra = new HashMap<>();
        extra.put("attackerId", player.getUserId());
        extra.put("attackerName", player.getUsername());
        action.setExtraData(extra);

        state.setPendingAction(action);
        state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了火攻，等待展示手牌");
        return success("火攻已使用，请目标展示手牌", action);
    }

    /**
     * 火攻 - 第二步：目标展示手牌后由使用者弃同花色牌
     */
    @SuppressWarnings("unchecked")
    private ActionResult handleHuoGongShow(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer target = findPlayerById(state, userId);
        if (target == null) return failure("未找到玩家");

        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        Long attackerId = extra.get("attackerId") instanceof Number
                ? ((Number) extra.get("attackerId")).longValue() : null;
        GamePlayer attacker = findPlayerById(state, attackerId);
        if (attacker == null) return failure("未找到攻击者");

        if (cardId == null) {
            state.addLog(target.getUsername() + " 跳过了火攻展示");
            state.setPendingAction(null);
            return success("火攻已取消");
        }

        GameCard revealedCard = findCard(target, cardId);
        if (revealedCard == null) return failure("无效的卡牌");

        // 展示卡牌（不弃置，只展示花色信息）
        state.addLog(target.getUsername() + " 展示了 " + revealedCard.getDisplayName() +
                "（" + revealedCard.getSuit().getSymbol() + "" + revealedCard.getNumberDisplay() + "）");

        // 让使用者弃一张同花色手牌
        List<String> attackerCardIds = attacker.getHandCards().stream()
                .map(GameCard::getId).toList();

        GameAction action = new GameAction();
        action.setActionType("HUO_GONG_DISCARD");
        action.setSourcePlayerId(attackerId);
        action.setOptionalCardIds(attackerCardIds);
        action.setOptionalCards(cardIdsToClientMap(state, attackerCardIds));
        action.setOptionalTargetIds(Collections.singletonList(attackerId));
        action.setMessage("火攻：" + target.getUsername() + " 展示了 " + revealedCard.getDisplayName() +
                "（" + revealedCard.getSuit().getSymbol() + "），请弃置一张相同花色的手牌或跳过");
        action.setExtraData(Map.of(
                "effectType", "HUO_GONG",
                "targetId", target.getUserId(),
                "revealedSuit", revealedCard.getSuit().name()
        ));

        state.setPendingAction(action);
        state.addLog("等待 " + attacker.getUsername() + " 弃同花色牌");
        return success("卡牌已展示，等待弃牌", action);
    }

    /**
     * 铁索连环 — 支持1-2个目标
     */
    private ActionResult useTieSuo(GameState state, GamePlayer player, GameCard card,
                                   List<String> targetUserIds) {
        if (targetUserIds.size() > 2) return failure("铁索连环最多选择2个目标");

        Set<String> unique = new HashSet<>(targetUserIds);
        if (unique.size() != targetUserIds.size()) return failure("目标不能重复");

        for (String uid : targetUserIds) {
            GamePlayer t = findPlayerById(state, Long.valueOf(uid));
            if (t == null || !t.isAlive()) return failure("目标无效或已死亡");
        }

        player.removeHandCard(card.getId());
        state.discardCard(card);

        for (String uid : targetUserIds) {
            GamePlayer t = findPlayerById(state, Long.valueOf(uid));
            t.setChained(!t.isChained());
            state.addLog(player.getUsername() +
                    (t.getUserId().equals(player.getUserId()) ? "将自身" : "将" + t.getUsername()) +
                    (t.isChained() ? "横置" : "重置"));
        }

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

            // 先检查花色是否匹配，匹配才弃牌
            if (discardCard.getSuit().name().equals(revealedSuit)) {
                player.removeHandCard(cardId);
                state.discardCard(discardCard);

                // 花色相同，成功
                // 计算铁索传导基础伤害（含藤甲+1）
                int hgChainBase = 1 + (target.getArmor() != null && target.getArmor().getCardType() == CardType.TENG_JIA ? 1 : 0);
                // 使用统一入口计算最终伤害（先藤甲+1，再白银狮子减至1）
                int huoGongFinal = calculateFinalDamage(state, target, 1, "FIRE");
                target.takeDamage(huoGongFinal);
                state.addLog("火攻成功！" + target.getUsername() + "受到" + huoGongFinal + "点火伤害");

                fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, player, target, null, huoGongFinal,
                        Map.of("effectType", "HUO_GONG")));
                // 铁索连环传导（火属性伤害）
                boolean hgSourceWasChained = player != null && player.isChained();
                propagateChainDamage(state, player, target, hgChainBase, "FIRE");

                // 统一濒死检查：原始目标优先，铁索传导目标次之
                List<GamePlayer> hgDyingCandidates = new ArrayList<>();
                hgDyingCandidates.add(target);
                if (hgSourceWasChained && player != null && !player.getUserId().equals(target.getUserId())) {
                    hgDyingCandidates.add(player);
                }
                ActionResult hgDying = checkDyingForAll(state, hgDyingCandidates);
                if (hgDying != null) return hgDying;
                state.checkGameOver();
                if (state.isFinished()) return success("火攻导致游戏结束");
            } else {
                state.addLog("火攻失败，" + discardCard.getSuit().getSymbol() + "≠已展示花色，卡牌未弃置");
                state.setPendingAction(null);
                return success("火攻失败，花色不匹配");
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
        state.addLog("判定牌：" + judgeCard.getSuit().getSymbol() + judgeCard.getNumberDisplay() + " " + judgeCard.getDisplayName());

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
                    int lightningFinal = calculateFinalDamage(state, player, 3, "THUNDER");
                    player.takeDamage(lightningFinal);
                    state.addLog("⚡闪电判定生效！" + player.getUsername() + "受到" + lightningFinal + "点雷电伤害");
                    fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, null, player, null, lightningFinal,
                            Map.of("effectType", "SHAN_DIAN")));
                    // 铁索连环传导（雷电伤害，使用原始伤害3，传导目标独立判定防具）
                    GamePlayer lightningOpponent = player.getOpponent(state.getPlayers());
                    boolean lightningOpponentWasChained = lightningOpponent != null && lightningOpponent.isChained();
                    propagateChainDamage(state, null, player, 3, "THUNDER");
                    // 统一濒死检查
                    List<GamePlayer> lightningDying = new ArrayList<>();
                    lightningDying.add(player);
                    if (lightningOpponentWasChained && lightningOpponent != null) {
                        lightningDying.add(lightningOpponent);
                    }
                    ActionResult ld = checkDyingForAll(state, lightningDying);
                    if (ld != null) return;
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

    // ============ 卡牌使用合法性校验 ============

    /**
     * 锦囊牌使用前校验（在进入无懈窗口之前调用，不修改任何状态）
     */
    private String validateTrickUse(GameState state, GamePlayer player, CardType type,
                                     String targetUserId, List<String> targetUserIds) {
        switch (type) {
            case GUO_HE -> {
                GamePlayer target = findPlayerById(state, targetUserId != null ?
                        Long.valueOf(targetUserId) : state.getOpponent().getUserId());
                if (target == null || !target.isAlive()) return "目标无效";
                if (target.getUserId().equals(player.getUserId())) return "不能对自己使用过河拆桥";
                if (target.getHandCards().isEmpty() && target.getWeapon() == null
                        && target.getArmor() == null && target.getPlusHorse() == null
                        && target.getMinusHorse() == null && target.getJudgeArea().isEmpty())
                    return "目标没有可拆的牌";
                return null;
            }
            case SHUN_SHOU -> {
                GamePlayer target = findPlayerById(state, targetUserId != null ?
                        Long.valueOf(targetUserId) : state.getOpponent().getUserId());
                if (target == null || !target.isAlive()) return "目标无效";
                if (target.getUserId().equals(player.getUserId())) return "不能对自己使用顺手牵羊";
                if (player.calculateDistanceTo(target) > 1) return "距离不足";
                if (target.getHandCards().isEmpty() && target.getWeapon() == null
                        && target.getArmor() == null && target.getPlusHorse() == null
                        && target.getMinusHorse() == null && target.getJudgeArea().isEmpty())
                    return "目标没有可顺的牌";
                return null;
            }
            case HUO_GONG -> {
                GamePlayer target = findPlayerById(state, targetUserId != null ?
                        Long.valueOf(targetUserId) : state.getOpponent().getUserId());
                if (target == null || !target.isAlive()) return "目标无效";
                if (target.getHandCards().isEmpty()) return "目标没有手牌，无法火攻";
                return null;
            }
            case JIE_DAO -> {
                // JIE_DAO 双目标校验现在在 playJieDaoWithWuxie 中独立完成
                return null;
            }
            case JUE_DOU -> {
                GamePlayer target = findPlayerById(state, targetUserId != null ?
                        Long.valueOf(targetUserId) : state.getOpponent().getUserId());
                if (target == null || !target.isAlive()) return "目标无效";
                return null;
            }
            case TIE_SUO -> {
                if (targetUserIds == null || targetUserIds.isEmpty()) return null; // 重铸无需校验
                if (targetUserIds.size() > 2) return "铁索连环最多选择2个目标";
                Set<String> unique = new HashSet<>(targetUserIds);
                if (unique.size() != targetUserIds.size()) return "目标不能重复";
                for (String uid : targetUserIds) {
                    GamePlayer t = findPlayerById(state, Long.valueOf(uid));
                    if (t == null || !t.isAlive()) return "目标无效或已死亡";
                }
                return null;
            }
            default -> {
                // WU_ZHONG, NAN_MAN, WAN_JIAN, TAO_YUAN, WU_GU：无需特殊校验
                return null;
            }
        }
    }

    /**
     * 延时锦囊使用前校验
     */
    private String validateDelayTrickUse(GameState state, GamePlayer player, GameCard card,
                                          String targetUserId) {
        GamePlayer target = findPlayerById(state, targetUserId != null ?
                Long.valueOf(targetUserId) : state.getOpponent().getUserId());
        if (target == null || !target.isAlive()) return "目标无效";
        if (target.getJudgeArea().size() >= 3) return "目标判定区已满";
        for (GameCard judgeCard : target.getJudgeArea()) {
            if (judgeCard.getCardType() == card.getCardType()) {
                return "目标判定区已有同名延时锦囊";
            }
        }
        return null;
    }

    // ============ 无懈可击响应链 ============

    /**
     * 动态扫描所有存活玩家手牌，判断是否有人有无懈可击
     * 每次调用重新检查，不缓存结果
     */
    private boolean hasAnyWuxie(GameState state) {
        return state.getAlivePlayers().stream()
                .anyMatch(p -> p.getHandCards().stream().anyMatch(c -> c.getCardType() == CardType.WU_XIE));
    }

    /**
     * 判断锦囊类型是否需要逐目标无懈结算
     */
    private boolean isAoeCardType(CardType type) {
        return type == CardType.TAO_YUAN || type == CardType.WU_GU
                || type == CardType.NAN_MAN || type == CardType.WAN_JIAN;
    }

    /**
     * 锦囊牌使用时打开无懈可击响应窗口给所有存活玩家。
     * 所有玩家可同时响应，先到先得。
     */
    @SuppressWarnings("unchecked")
    private ActionResult playTrickWithWuxieCheck(GameState state, GamePlayer player, GameCard card,
                                                  CardType type, CardEffect effect,
                                                  String targetUserId, String targetCardId) {
        // 先将锦囊牌从手牌移除
        player.removeHandCard(card.getId());
        state.getTempCards().clear();
        state.getTempCards().add(card);

        // 构建目标列表
        boolean isAoe = isAoeCardType(type);
        List<String> targets;
        if (isAoe) {
            if (type == CardType.TAO_YUAN || type == CardType.WU_GU) {
                // 对自己和对方都生效
                targets = state.getAlivePlayers().stream()
                        .map(p -> String.valueOf(p.getUserId()))
                        .collect(java.util.stream.Collectors.toList());
            } else {
                // 南蛮/万箭：仅对对方生效（逐目标无懈）
                targets = state.getAlivePlayers().stream()
                        .filter(p -> !p.getUserId().equals(player.getUserId()))
                        .map(p -> String.valueOf(p.getUserId()))
                        .collect(java.util.stream.Collectors.toList());
            }
            state.discardCard(card);

            // 五谷丰登：先摸取展示牌（所有目标共享此牌池）
            if (type == CardType.WU_GU) {
                int count = state.getAlivePlayers().size();
                List<GameCard> shown = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    GameCard c = state.drawCard();
                    if (c != null) shown.add(c);
                }
                if (shown.isEmpty()) {
                    state.addLog(player.getUsername() + " 使用了五谷丰登，但牌堆已空");
                    return success("无牌可展示");
                }
                state.getTempCards().clear();
                state.getTempCards().addAll(shown);
                state.addLog(player.getUsername() + " 使用了五谷丰登，展示" + shown.size() + "张牌");
            }
        } else {
            targets = (targetUserId != null)
                    ? new ArrayList<>(Collections.singletonList(targetUserId))
                    : new ArrayList<>();
            // 无中生有：自目标锦囊，效果目标为使用者自己
            if (targets.isEmpty() && type == CardType.WU_ZHONG) {
                targetUserId = String.valueOf(player.getUserId());
                targets = new ArrayList<>(Collections.singletonList(targetUserId));
            }
        }

        if (targets.isEmpty()) {
            state.getTempCards().clear();
            return success("无有效目标");
        }

        // 动态扫描：每次调用重新检查
        if (!hasAnyWuxie(state)) {
            if (isAoe) {
                // AOE 无快路径：走正常无懈框架确保 RESPOND 动作有 aoeContext
                // advanceToNextWuxieResponder 将耗尽队列（无人有无懈）
                // → resolveWuxieChain → resolveAoeTarget → 创建带 aoeContext 的 RESPOND 动作
                return openWuxieForTarget(state, player.getUserId(), card, type,
                        targetUserId, targetCardId, targets, 0, true);
            }
            // 非AOE锦囊：无人有无懈，直接结算
            state.getTempCards().clear();
            ActionResult result = effect.execute(state, player, card, targetUserId, targetCardId);
            if (result.success()) {
                fireEvent(new GameEvent(GameEventType.CARD_USED, state, player, null, card, 0, null));
            }
            return result;
        }

        // 有人有无懈，打开无懈响应窗口（AOE逐目标）
        return openWuxieForTarget(state, player.getUserId(), card, type,
                targetUserId, targetCardId, targets, 0, isAoe);
    }

    /**
     * 铁索连环多目标无懈入口
     * 多目标铁索连环需要逐目标无懈可击判断
     */
    private ActionResult playTieSuoWithWuxieCheck(GameState state, GamePlayer player, GameCard card,
                                                   List<String> targetUserIds) {
        player.removeHandCard(card.getId());
        state.getTempCards().clear();
        state.getTempCards().add(card);

        List<String> targets = new ArrayList<>(targetUserIds);
        if (targets.isEmpty()) {
            state.getTempCards().clear();
            return success("无有效目标");
        }

        // 铁索连环牌已使用，弃置
        state.discardCard(card);

        // 动态扫描
        if (!hasAnyWuxie(state)) {
            state.getTempCards().clear();
            return useTieSuo(state, player, card, targetUserIds);
        }

        // 逐目标无懈
        return openWuxieForTarget(state, player.getUserId(), card, CardType.TIE_SUO,
                null, null, targets, 0, true);
    }

    /**
     * 打开无懈可击响应窗口，支持逐目标结算
     * 创建 WAIT_WUXIE_RESPONSE 并设置完整上下文
     */
    @SuppressWarnings("unchecked")
    private ActionResult openWuxieForTarget(GameState state, Long sourcePlayerId, GameCard card,
                                              CardType type, String originalTargetUserId,
                                              String originalTargetCardId, List<String> allTargets,
                                              int targetIndex, boolean isAoe) {
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("trickCardId", card.getId());
        extraData.put("trickCardType", type.name());
        extraData.put("sourcePlayerId", sourcePlayerId);
        extraData.put("originalTargetUserId", originalTargetUserId);
        extraData.put("originalTargetCardId", originalTargetCardId);
        extraData.put("pendingTargetUserIds", new ArrayList<>(allTargets));
        extraData.put("currentTargetIndex", targetIndex);
        extraData.put("wuxieStack", new ArrayList<String>());
        extraData.put("respondedSkipIds", new ArrayList<Long>());
        extraData.put("isDelayTrickJudgment", false);
        extraData.put("isAoe", isAoe);

        // 五谷丰登：存储展示牌信息和取消列表
        if (type == CardType.WU_GU) {
            List<GameCard> shown = state.getTempCards();
            List<String> shownIds = shown.stream().map(GameCard::getId).toList();
            List<Map<String, Object>> wuguCardInfos = shown.stream().map(this::cardToClientMap).toList();
            extraData.put("wuguShownIds", shownIds);
            extraData.put("wuguCardInfos", wuguCardInfos);
            extraData.put("wuguCancelledTargets", new ArrayList<String>());
            extraData.put("wuguAlreadyPicked", new ArrayList<String>());
        }

        // 构建顺序响应队列并推进至首个有资格响应者
        List<Long> responderQueue = state.getAlivePlayers().stream()
                .map(GamePlayer::getUserId)
                .collect(java.util.stream.Collectors.toList());
        extraData.put("responderQueue", responderQueue);
        extraData.put("currentResponderIndex", 0);

        return advanceToNextWuxieResponder(state, extraData, sourcePlayerId);
    }

    /**
     * 推进无懈响应队列至下一个有资格的响应者
     * 按 responderQueue 顺序依次检查：
     * - 死亡玩家跳过
     * - 无 WU_XIE 玩家自动跳过（加入 respondedSkipIds）
     * - 有 WU_XIE 玩家：创建 PendingAction，等待响应
     * - 队列耗尽：调用 resolveWuxieChain 结算
     */
    @SuppressWarnings("unchecked")
    private ActionResult advanceToNextWuxieResponder(GameState state, Map<String, Object> extraData,
                                                      Long sourcePlayerId) {
        List<Long> responderQueue = (List<Long>) extraData.get("responderQueue");
        int currentIndex = (int) extraData.get("currentResponderIndex");
        List<Long> respondedSkipIds = (List<Long>) extraData.get("respondedSkipIds");

        while (currentIndex < responderQueue.size()) {
            Long candidateId = responderQueue.get(currentIndex);
            GamePlayer candidate = findPlayerById(state, candidateId);

            if (candidate == null || !candidate.isAlive()) {
                currentIndex++;
                continue;
            }

            boolean hasWuxie = candidate.getHandCards().stream()
                    .anyMatch(c -> c.getCardType() == CardType.WU_XIE);

            if (!hasWuxie) {
                if (!respondedSkipIds.contains(candidateId)) {
                    respondedSkipIds.add(candidateId);
                }
                currentIndex++;
                continue;
            }

            // 找到有资格的响应者
            extraData.put("currentResponderIndex", currentIndex);

            String targetName = candidate.getUsername();
            boolean isAoe = Boolean.TRUE.equals(extraData.get("isAoe"));

            GameAction action = new GameAction();
            action.setActionType("WAIT_WUXIE_RESPONSE");
            action.setSourcePlayerId(sourcePlayerId);
            action.setOptionalCardIds(Collections.emptyList());
            action.setOptionalCards(Collections.emptyList());
            action.setOptionalTargetIds(Collections.singletonList(candidateId));
            action.setMessage(isAoe ? "是否使用无懈可击？（当前目标：" + targetName + "）" : "是否使用无懈可击？");
            action.setExtraData(extraData);

            state.setPendingAction(action);
            return success("等待无懈可击响应", action);
        }

        // 队列耗尽，结算无懈链
        CardType trickCardType = CardType.valueOf((String) extraData.get("trickCardType"));
        Long spId = extraData.get("sourcePlayerId") instanceof Number
                ? ((Number) extraData.get("sourcePlayerId")).longValue() : null;
        String originalTargetUserId = (String) extraData.get("originalTargetUserId");
        String originalTargetCardId = (String) extraData.get("originalTargetCardId");
        List<String> wuxieStack = (List<String>) extraData.get("wuxieStack");

        return resolveWuxieChain(state, trickCardType, spId,
                originalTargetUserId, originalTargetCardId, wuxieStack, extraData);
    }

    /**
     * 处理无懈可击响应 — 顺序响应模式。
     * 所有存活玩家按 responderQueue 顺序依次询问：
     * - 当前响应者有 WU_XIE 则等待其选择使用/跳过
     * - 当前响应者没有 WU_XIE 则自动跳过
     * - 当前响应者使用无懈后，重新构建响应队列继续反无懈链
     * - 队列耗尽后结算无懈链
     *
     * cardId != null → 使用无懈可击；null → 跳过
     */
    @SuppressWarnings("unchecked")
    private ActionResult handleWuxieResponse(GameState state, Long userId, String cardId, GameAction pending) {
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        if (extra == null) return failure("无懈可击数据异常");

        List<Long> responderQueue = (List<Long>) extra.get("responderQueue");
        int currentIndex = (int) extra.get("currentResponderIndex");

        // 验证当前响应者
        if (currentIndex >= responderQueue.size()) {
            return failure("无懈响应队列异常");
        }
        Long currentResponderId = responderQueue.get(currentIndex);
        if (!userId.equals(currentResponderId)) {
            return failure("当前不是你的响应时机");
        }

        List<Long> respondedSkipIds = (List<Long>) extra.get("respondedSkipIds");
        List<String> wuxieStack = (List<String>) extra.get("wuxieStack");
        Long sourcePlayerId = extra.get("sourcePlayerId") instanceof Number
                ? ((Number) extra.get("sourcePlayerId")).longValue() : null;
        CardType trickCardType = CardType.valueOf((String) extra.get("trickCardType"));

        // 跳过一次校验：如果已响应过则拒绝
        if (respondedSkipIds.contains(userId) || wuxieStack.contains(String.valueOf(userId))) {
            return failure("你已做出响应");
        }

        GamePlayer currentPlayer = findPlayerById(state, userId);
        if (currentPlayer == null) return failure("未找到玩家");

        if (cardId != null) {
            // === 打出一张无懈可击 ===
            boolean hasWuxieInHand = currentPlayer.getHandCards().stream()
                    .anyMatch(c -> c.getCardType() == CardType.WU_XIE);
            if (!hasWuxieInHand) {
                return failure("你没有无懈可击");
            }
            GameCard wuxieCard = findCard(currentPlayer, cardId);
            if (wuxieCard == null || wuxieCard.getCardType() != CardType.WU_XIE) {
                return failure("无效的无懈可击");
            }

            currentPlayer.removeHandCard(cardId);
            state.discardCard(wuxieCard);
            wuxieStack.add(String.valueOf(userId));
            state.addLog(currentPlayer.getUsername() + " 使用了无懈可击");

            // 无懈已使用，进入反无懈轮：排除刚刚使用无懈的玩家，重新构建队列
            List<Long> nextRoundQueue = state.getAlivePlayers().stream()
                    .map(GamePlayer::getUserId)
                    .filter(id -> !id.equals(userId))
                    .collect(java.util.stream.Collectors.toList());
            extra.put("responderQueue", nextRoundQueue);
            extra.put("currentResponderIndex", 0);
            extra.put("respondedSkipIds", new ArrayList<Long>());

            return advanceToNextWuxieResponder(state, extra, sourcePlayerId);
        } else {
            // === 跳过 ===
            respondedSkipIds.add(userId);
            extra.put("currentResponderIndex", currentIndex + 1);
            return advanceToNextWuxieResponder(state, extra, sourcePlayerId);
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
                // 清除旧 WAIT_WUXIE_RESPONSE，避免被误当作濒死 pending action 重新发出
                state.setPendingAction(null);
                if (judgeCard != null) {
                    applyDelayTrickEffect(state, current, judgeCard);
                }
                state.getTempCards().clear();
                // applyDelayTrickEffect 可能设置了濒死求桃 pending action（如闪电）
                GameAction pendingAfterApply = state.getPendingAction();
                if (pendingAfterApply != null) {
                    return success("延时锦囊判定完成，需处理濒死", pendingAfterApply);
                }
                return success("延时锦囊判定完成");
            }
        }

        // === AOE 锦囊逐目标结算 ===
        boolean isAoe = extra != null && Boolean.TRUE.equals(extra.get("isAoe"));
        if (isAoe) {
            return resolveAoeTarget(state, trickCardType, sourcePlayerId, wuxieStack, extra);
        }

        // === 普通锦囊（单目标）无懈可击链结算 ===
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
     * AOE 锦囊逐目标无懈可击结算
     * 已取消（奇数无懈）→ 跳过此目标效果
     * 未取消（偶数无懈）→ 对当前目标执行效果
     * 目标队列未空 → 推进至下一目标并打开新无懈窗口
     * 目标队列已空 → 清理完毕（五谷丰登则启动选牌流程）
     */
    @SuppressWarnings("unchecked")
    private ActionResult resolveAoeTarget(GameState state, CardType trickCardType,
                                           Long sourcePlayerId, List<String> wuxieStack,
                                           Map<String, Object> extra) {
        List<String> pendingTargets = (List<String>) extra.get("pendingTargetUserIds");
        int currentIndex = (int) extra.get("currentTargetIndex");
        String currentTargetId = pendingTargets.get(currentIndex);
        boolean cancelled = wuxieStack.size() % 2 == 1;

        // 重复目标防护
        List<String> processedIds = (List<String>) extra.get("processedTargetUserIds");
        if (processedIds == null) {
            processedIds = new ArrayList<>();
            extra.put("processedTargetUserIds", processedIds);
        }
        if (processedIds.contains(currentTargetId)) {
            state.addLog("跳过已处理的AOE目标");
            return advanceAoeToNext(state, trickCardType, sourcePlayerId, extra, pendingTargets, currentIndex);
        }
        processedIds.add(currentTargetId);

        GamePlayer currentTarget = findPlayerById(state, Long.valueOf(currentTargetId));
        GamePlayer sourcePlayer = findPlayerById(state, sourcePlayerId);
        if (sourcePlayer == null) sourcePlayer = state.getCurrentPlayer();

        // === 南蛮入侵：逐目标出杀响应 ===
        if (trickCardType == CardType.NAN_MAN) {
            if (!cancelled && currentTarget != null && currentTarget.isAlive()) {
                // 藤甲免疫
                if (currentTarget.getArmor() != null && currentTarget.getArmor().getCardType() == CardType.TENG_JIA) {
                    state.addLog(currentTarget.getUsername() + " 的藤甲免疫了南蛮入侵");
                    return advanceAoeToNext(state, trickCardType, sourcePlayerId, extra, pendingTargets, currentIndex);
                }

                List<String> shaCards = currentTarget.getHandCards().stream()
                        .filter(c -> c.getCardType() == CardType.SHA)
                        .map(GameCard::getId)
                        .toList();

                String trickCardId = (String) extra.get("trickCardId");
                GameAction action = new GameAction();
                action.setActionType("RESPOND_SHA");
                action.setSourceCardId(trickCardId);
                action.setSourcePlayerId(sourcePlayerId);
                action.setOptionalCardIds(shaCards);
                action.setOptionalCards(cardIdsToClientMap(state, shaCards));
                action.setOptionalTargetIds(Collections.singletonList(currentTarget.getUserId()));
                action.setMessage("请出杀响应南蛮入侵");
                Map<String, Object> nanManExtra = new HashMap<>();
                nanManExtra.put("effectType", "NAN_MAN");
                nanManExtra.put("damage", 1);
                Map<String, Object> aoeCtx = new HashMap<>();
                aoeCtx.put("pendingTargetUserIds", new ArrayList<>(pendingTargets));
                aoeCtx.put("currentTargetIndex", currentIndex);
                aoeCtx.put("trickCardType", trickCardType.name());
                aoeCtx.put("sourcePlayerId", sourcePlayerId);
                aoeCtx.put("aoeOriginalExtra", extra);
                nanManExtra.put("aoeContext", aoeCtx);
                action.setExtraData(nanManExtra);

                state.setPendingAction(action);
                state.addLog(sourcePlayer.getUsername() + " 使用了南蛮入侵");
                return success("南蛮入侵已使用", action);
            }
            state.addLog((currentTarget != null ? currentTarget.getUsername() : currentTargetId)
                    + " 的南蛮入侵效果被无懈可击抵消");
            return advanceAoeToNext(state, trickCardType, sourcePlayerId, extra, pendingTargets, currentIndex);
        }

        // === 万箭齐发：逐目标出闪响应 ===
        if (trickCardType == CardType.WAN_JIAN) {
            if (!cancelled && currentTarget != null && currentTarget.isAlive()) {
                List<String> shanCards = currentTarget.getHandCards().stream()
                        .filter(c -> c.getCardType() == CardType.SHAN)
                        .map(GameCard::getId)
                        .toList();

                String trickCardId = (String) extra.get("trickCardId");
                GameAction action = new GameAction();
                action.setActionType("RESPOND_SHAN");
                action.setSourceCardId(trickCardId);
                action.setSourcePlayerId(sourcePlayerId);
                action.setOptionalCardIds(shanCards);
                action.setOptionalCards(cardIdsToClientMap(state, shanCards));
                action.setOptionalTargetIds(Collections.singletonList(currentTarget.getUserId()));
                action.setMessage("请出闪响应万箭齐发");
                Map<String, Object> wanJianExtra = new HashMap<>();
                wanJianExtra.put("effectType", "WAN_JIAN");
                wanJianExtra.put("damage", 1);
                Map<String, Object> aoeCtx = new HashMap<>();
                aoeCtx.put("pendingTargetUserIds", new ArrayList<>(pendingTargets));
                aoeCtx.put("currentTargetIndex", currentIndex);
                aoeCtx.put("trickCardType", trickCardType.name());
                aoeCtx.put("sourcePlayerId", sourcePlayerId);
                aoeCtx.put("aoeOriginalExtra", extra);
                wanJianExtra.put("aoeContext", aoeCtx);
                action.setExtraData(wanJianExtra);

                state.setPendingAction(action);
                state.addLog(sourcePlayer.getUsername() + " 使用了万箭齐发");
                return success("万箭齐发已使用", action);
            }
            state.addLog((currentTarget != null ? currentTarget.getUsername() : currentTargetId)
                    + " 的万箭齐发效果被无懈可击抵消");
            return advanceAoeToNext(state, trickCardType, sourcePlayerId, extra, pendingTargets, currentIndex);
        }

        // === 桃园结义：逐目标回复 ===
        if (trickCardType == CardType.TAO_YUAN) {
            if (!cancelled && currentTarget != null && currentTarget.isAlive()) {
                int healed = currentTarget.heal(1);
                if (healed > 0) {
                    state.addLog(currentTarget.getUsername() + " 回复了1点体力（桃园结义）");
                }
            } else {
                state.addLog((currentTarget != null ? currentTarget.getUsername() : currentTargetId)
                        + " 的桃园结义效果被无懈可击抵消");
            }
        }

        // === 五谷丰登：记录取消目标 ===
        if (trickCardType == CardType.WU_GU) {
            if (cancelled) {
                List<String> cancelledTargets = (List<String>) extra.get("wuguCancelledTargets");
                cancelledTargets.add(currentTargetId);
                state.addLog((currentTarget != null ? currentTarget.getUsername() : currentTargetId)
                        + " 被无懈可击，跳过五谷丰登选牌");
            }
        }

        // === 铁索连环：逐目标横置/重置 ===
        if (trickCardType == CardType.TIE_SUO) {
            if (!cancelled && currentTarget != null) {
                currentTarget.setChained(!currentTarget.isChained());
                state.addLog(sourcePlayer.getUsername() +
                        (currentTarget.getUserId().equals(sourcePlayerId) ? "将自身" : "将" + currentTarget.getUsername()) +
                        (currentTarget.isChained() ? "横置" : "重置") + "（铁索连环）");
            } else {
                state.addLog((currentTarget != null ? currentTarget.getUsername() : currentTargetId)
                        + " 的铁索连环效果被无懈可击抵消");
            }
        }

        // 推进至下一目标
        return advanceAoeToNext(state, trickCardType, sourcePlayerId, extra, pendingTargets, currentIndex);
    }

    /**
     * AOE逐目标完成后清理，或推进至下一目标
     */
    @SuppressWarnings("unchecked")
    private ActionResult advanceAoeToNext(GameState state, CardType trickCardType,
                                           Long sourcePlayerId, Map<String, Object> extra,
                                           List<String> pendingTargets, int currentIndex) {
        int nextIndex = currentIndex + 1;
        extra.put("currentTargetIndex", nextIndex);

        if (nextIndex < pendingTargets.size()) {
            // 动态扫描：下个目标前重新检查是否有人有无懈
            if (!hasAnyWuxie(state)) {
                // 无人有无懈，剩余目标直接结算
                for (int i = nextIndex; i < pendingTargets.size(); i++) {
                    @SuppressWarnings("unchecked")
                    List<String> procIds = (List<String>) extra.get("processedTargetUserIds");
                    if (procIds != null && procIds.contains(pendingTargets.get(i))) {
                        continue;
                    }
                    applyDirectAoeEffect(state, trickCardType, sourcePlayerId, extra, pendingTargets, i);
                }
                return finishAoe(state, trickCardType, extra);
            }

            // 打开新窗口给下一目标
            extra.put("wuxieStack", new ArrayList<String>());

            List<Long> aoeResponderQueue = state.getAlivePlayers().stream()
                    .map(GamePlayer::getUserId)
                    .collect(java.util.stream.Collectors.toList());
            extra.put("responderQueue", aoeResponderQueue);
            extra.put("currentResponderIndex", 0);
            extra.put("respondedSkipIds", new ArrayList<Long>());

            return advanceToNextWuxieResponder(state, extra, sourcePlayerId);
        }

        return finishAoe(state, trickCardType, extra);
    }

    /**
     * 直接对AOE目标应用效果（无懈窗口跳过时）
     */
    @SuppressWarnings("unchecked")
    private void applyDirectAoeEffect(GameState state, CardType trickCardType,
                                       Long sourcePlayerId, Map<String, Object> extra,
                                       List<String> pendingTargets, int index) {
        String targetId = pendingTargets.get(index);
        GamePlayer target = findPlayerById(state, Long.valueOf(targetId));
        if (target == null || !target.isAlive()) return;

        GamePlayer sourcePlayer = findPlayerById(state, sourcePlayerId);
        if (sourcePlayer == null) sourcePlayer = state.getCurrentPlayer();

        switch (trickCardType) {
            case TAO_YUAN -> {
                int healed = target.heal(1);
                if (healed > 0) state.addLog(target.getUsername() + " 回复了1点体力（桃园结义）");
            }
            case TIE_SUO -> {
                target.setChained(!target.isChained());
                state.addLog(sourcePlayer.getUsername() +
                        (target.getUserId().equals(sourcePlayerId) ? "将自身" : "将" + target.getUsername()) +
                        (target.isChained() ? "横置" : "重置") + "（铁索连环）");
            }
            case WU_GU -> {
                // 无懈跳过时只记录未取消的
            }
            default -> {}
        }
    }

    /**
     * AOE结算完成清理
     */
    @SuppressWarnings("unchecked")
    private ActionResult finishAoe(GameState state, CardType trickCardType,
                                    Map<String, Object> extra) {
        if (trickCardType == CardType.WU_GU) {
            List<String> cancelledTargets = (List<String>) extra.get("wuguCancelledTargets");
            List<String> alreadyPicked = (List<String>) extra.get("wuguAlreadyPicked");
            final List<String> effectivePicked = alreadyPicked != null ? alreadyPicked : new ArrayList<>();

            // 已有玩家选过牌后恢复：使用当前 tempCards，而非原始全量列表
            List<String> resumeCardIds = (List<String>) extra.get("wuguCurrentCardIds");
            List<Map<String, Object>> resumeCardInfos = null;
            if (resumeCardIds != null) {
                resumeCardInfos = (List<Map<String, Object>>) extra.get("wuguCurrentCardInfos");
            }

            List<String> cardIds = resumeCardIds != null ? resumeCardIds
                    : (List<String>) extra.get("wuguShownIds");
            List<Map<String, Object>> cardInfos = resumeCardInfos != null ? resumeCardInfos
                    : (List<Map<String, Object>>) extra.get("wuguCardInfos");

            List<Long> filteredOrder = state.getAlivePlayers().stream()
                    .map(GamePlayer::getUserId)
                    .filter(id -> !cancelledTargets.contains(String.valueOf(id)))
                    .filter(id -> !effectivePicked.contains(String.valueOf(id)))
                    .toList();

            if (filteredOrder.isEmpty() || cardIds.isEmpty()) {
                state.getTempCards().clear();
                state.setPendingAction(null);
                state.addLog("所有玩家已选完或被无懈可击，五谷丰登结束");
                return success("五谷丰登结束");
            }

            GameAction action = new GameAction();
            action.setActionType("CHOOSE_WUGU_CARD");
            action.setSourcePlayerId(extra.get("sourcePlayerId") instanceof Number
                    ? ((Number) extra.get("sourcePlayerId")).longValue() : null);
            action.setOptionalCardIds(cardIds);
            action.setOptionalCards(cardInfos);
            action.setMessage("请选择一张牌（五谷丰登）");
            action.setOptionalTargetIds(Collections.singletonList(filteredOrder.get(0)));
            Map<String, Object> chooserExtra = new LinkedHashMap<>();
            chooserExtra.put("pickerOrder", new ArrayList<>(filteredOrder));
            chooserExtra.put("pickerIndex", 0);
            chooserExtra.put("aoeOriginalExtra", extra);
            action.setExtraData(chooserExtra);
            state.setPendingAction(action);
            state.addLog("五谷丰登选牌开始");
            return success("五谷丰登选牌开始", action);
        }

        // NAN_MAN / WAN_JIAN / TIE_SUO / TAO_YUAN
        state.getTempCards().clear();
        state.setPendingAction(null);
        return success(trickCardType.getDisplayName() + "结算完成");
    }

    /**
     * AOE交互目标（南蛮/万箭）跳过无懈后清理
     */
    private ActionResult cleanupAoeTarget(GameState state, List<String> pendingTargets, int currentIndex) {
        state.getTempCards().clear();
        state.setPendingAction(null);
        return success("已处理");
    }

    /**
     * 处理玩家响应（出闪/出杀等）
     */
    public ActionResult handleResponse(GameState state, Long userId, String cardId, List<String> cardIds, String targetUserId, String requestActionId) {
        GameAction pending = state.getPendingAction();
        if (pending == null) return failure("没有待处理的响应");

        // actionId 校验：确保消费的是当前 pendingAction，防止 WebSocket 重复请求
        log.info("[ENGINE handleResponse] userId={} cardId={} requestActionId={} pendingActionId={} pendingType={}",
                userId, cardId, requestActionId, pending.getActionId(), pending.getActionType());
        if (requestActionId == null || !requestActionId.equals(pending.getActionId())) {
            log.info("[ENGINE handleResponse FAIL] actionId mismatch: request={} pending={}", requestActionId, pending.getActionId());
            return failure("响应已过期");
        }

        // 验证响应者
        if (!pending.getOptionalTargetIds().contains(userId)) {
            return failure("不是你需要响应");
        }

        // 提前保存 AOE 上下文（handleXXX 可能会清空 pendingAction）
        @SuppressWarnings("unchecked")
        Map<String, Object> pendingExtra = (Map<String, Object>) pending.getExtraData();
        Map<String, Object> aoeContext = null;
        if (pendingExtra != null) {
            aoeContext = (Map<String, Object>) pendingExtra.get("aoeContext");
        }

        String actionType = pending.getActionType();

        ActionResult result = switch (actionType) {
            case "RESPOND_SHAN" -> handleRespondShan(state, userId, cardId, pending);
            case "RESPOND_SHA" -> handleRespondSha(state, userId, cardId, pending);
            case "CHOOSE_TARGET_CARD" -> handleChooseTargetCard(state, userId, cardId, pending);
            case "CHOOSE_WUGU_CARD" -> handleChooseWuguCard(state, userId, cardId, pending);
            case "DISCARD" -> handleDiscardPhase(state, userId, cardIds, pending);
            case "DYING_REQUIRE_TAO" -> handleDying(state, userId, cardId, pending);
            case "HUO_GONG_SHOW" -> handleHuoGongShow(state, userId, cardId, pending);
            case "HUO_GONG_DISCARD" -> handleHuoGongDiscard(state, userId, cardId, pending);
            case "WAIT_EQUIP_TRIGGER" -> handleEquipTrigger(state, userId, cardId, pending);
            case "WAIT_WUXIE_RESPONSE" -> handleWuxieResponse(state, userId, cardId, pending);
            case "HAN_BING_CHOOSE" -> handleHanBingChoose(state, userId, cardIds, pending);
            default -> failure("未知响应类型");
        };

        // 南蛮/万箭 AOE continuation：当前目标结算完且无待处理动作时，推进到下一目标
        if (aoeContext != null && state.getPendingAction() == null && !state.isFinished() && result.success()) {
            @SuppressWarnings("unchecked")
            List<String> aoeTargets = (List<String>) aoeContext.get("pendingTargetUserIds");
            int aoeIndex = ((Number) aoeContext.get("currentTargetIndex")).intValue();
            CardType aoeType = CardType.valueOf((String) aoeContext.get("trickCardType"));
            Long aoeSource = ((Number) aoeContext.get("sourcePlayerId")).longValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> aoeExtra = (Map<String, Object>) aoeContext.get("aoeOriginalExtra");
            log.info("[AOE CONTINUATION] type={} targets={} index={}/{} hasWuxie={}", aoeType, aoeTargets, aoeIndex, aoeTargets.size(), hasAnyWuxie(state));
            return advanceAoeToNext(state, aoeType, aoeSource, aoeExtra, aoeTargets, aoeIndex);
        }

        if (aoeContext != null && result.success()) {
            log.info("[AOE CONTINUATION SKIPPED] pendingActionIsNull={} isFinished={} aoeContext={}",
                    state.getPendingAction() == null, state.isFinished(), aoeContext);
        }

        return result;
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
        String natureStr = extra != null ? (String) extra.get("nature") : "NORMAL";

        log.info("[ENGINE handleRespondShan] userId={} cardId={} effectType={} hasJiuEffect={} baseDamage={} natureStr={} pendingActionId={}",
                userId, cardId, effectType, hasJiuEffect, baseDamage, pending.getActionId());

        // === 万箭齐发：单独的 RESPOND_SHAN 分支，不走杀逻辑 ===
        if ("WAN_JIAN".equals(effectType)) {
            if (cardId != null) {
                GameCard shanCard = findCard(responder, cardId);
                if (shanCard == null || shanCard.getCardType() != CardType.SHAN) {
                    return failure("无效的闪");
                }
                boolean needShan = true;
                if (responder.getArmor() != null && responder.getArmor().getCardType() == CardType.BA_GUA) {
                    GameCard judgeCard = state.drawCard();
                    if (judgeCard != null) {
                        state.discardCard(judgeCard);
                        state.addLog("判定牌：" + judgeCard.getSuit().getSymbol() + judgeCard.getNumberDisplay() + " " + judgeCard.getDisplayName() + "（八卦阵）");
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
                state.addLog(responder.getUsername() + " 打出闪，响应万箭齐发");
                state.setPendingAction(null);
                return success("出闪成功");
            } else {
                int damage = baseDamage;
                if (responder.getArmor() != null && responder.getArmor().getCardType() == CardType.BAI_YIN) {
                    damage = Math.min(damage, 1);
                    state.addLog("白银狮子将伤害降为1");
                }
                log.info("[ENGINE WAN_JIAN DAMAGE] userId={} damage={} responder={} pendingActionId={} caller={}",
                        userId, damage, responder.getUsername(), pending.getActionId(),
                        Thread.currentThread().getStackTrace()[2]);
                responder.takeDamage(damage);
                state.addLog(responder.getUsername() + " 未响应万箭齐发，受到" + damage + "点伤害");

                ActionResult dying = checkDying(state, responder);
                if (dying != null) return dying;
                state.checkGameOver();

                if (!state.isFinished()) {
                    GameAction oldAction = state.getPendingAction();
                    fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, attacker, responder, null, damage,
                            Map.of("effectType", "WAN_JIAN")));
                    GameAction newAction = state.getPendingAction();
                    if (newAction != null && newAction != oldAction) {
                        return success("万箭齐发命中，触发事件", newAction);
                    }
                }
                state.setPendingAction(null);
                return success("万箭齐发命中");
            }
        }

        // ========== 杀逻辑（effectType == null / "SHA"）==========
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
                    state.addLog("判定牌：" + judgeCard.getSuit().getSymbol() + judgeCard.getNumberDisplay() + " " + judgeCard.getDisplayName() + "（八卦阵）");
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
                        ed.put("shaResolveId", extra != null ? extra.get("shaResolveId") : null);
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

            // 古锭刀：仅对杀有效，不对万箭/南蛮生效
            if (effectType == null && attacker.getWeapon() != null && attacker.getWeapon().getCardType() == CardType.GU_DING_DAO
                    && responder.getHandCards().isEmpty()) {
                damage += 1;
                state.addLog("古锭刀效果：目标无手牌，伤害+1");
            }


            // 检查防具
            boolean armorEffective = true;
            if (responder.getArmor() != null) {
                CardType armorType = responder.getArmor().getCardType();
                if (armorType == CardType.REN_WANG && extra != null && Boolean.TRUE.equals(extra.get("isBlack"))
                        && !Boolean.TRUE.equals(extra.get("hasQingGang"))) {
                    armorEffective = false;
                    state.addLog(responder.getUsername() + " 的仁王盾使黑色杀无效");
                    state.setPendingAction(null);
                    return success("杀被仁王盾抵挡");
                }
                if (armorType == CardType.TENG_JIA && effectType == null && "NORMAL".equals(natureStr)
                        && !Boolean.TRUE.equals(extra.get("hasQingGang"))) {
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
            // 寒冰剑：杀命中时可改为弃目标两张牌（≥1张即可触发，由后续选择确定弃牌数）
            if (attacker.getWeapon() != null && attacker.getWeapon().getCardType() == CardType.HAN_BING
                    && !"NAN_MAN".equals(effectType) && !"WAN_JIAN".equals(effectType)
                    && countDiscardableCards(responder) >= 1) {
                GameAction equipAction = new GameAction();
                equipAction.setActionType("WAIT_EQUIP_TRIGGER");
                equipAction.setSourcePlayerId(attacker.getUserId());
                equipAction.setOptionalTargetIds(Collections.singletonList(attacker.getUserId()));
                equipAction.setMessage("是否发动寒冰剑？改为弃置" + responder.getUsername() + "的牌（装备或随机手牌）");
                Map<String, Object> ed = new HashMap<>();
                ed.put("skillCode", "HAN_BING");
                ed.put("targetId", responder.getUserId());
                ed.put("effectType", effectType);
                ed.put("damage", baseDamage);
                ed.put("hasJiuEffect", hasJiuEffect);
                ed.put("nature", extra != null ? extra.get("nature") : "NORMAL");
                ed.put("isBlack", extra != null && Boolean.TRUE.equals(extra.get("isBlack")));
                ed.put("shaResolveId", extra != null ? extra.get("shaResolveId") : null);
                equipAction.setExtraData(ed);
                state.setPendingAction(equipAction);
                state.addLog(attacker.getUsername() + " 可发动寒冰剑");
                return success("寒冰剑触发", equipAction);
            }

            // 防重复伤害机制
            Object rawShaResolveId = extra != null ? extra.get("shaResolveId") : null;
            if (rawShaResolveId != null) {
                String damageKey = rawShaResolveId + ":" + responder.getUserId();
                if (state.getResolvedDamageKeys().contains(damageKey)) {
                    state.addLog("跳过重复伤害处理（同一杀已命中目标, key=" + damageKey + "）");
                    state.setPendingAction(null);
                    return success("重复伤害已跳过");
                }
                state.getResolvedDamageKeys().add(damageKey);
                state.addLog("记录伤害 key=" + damageKey);
            }

            // 计算铁索传导基础伤害（含藤甲+1，不含白银狮子减伤，因传导目标独立判定防具）
            boolean shaHasTengJiaFire = responder.getArmor() != null
                    && responder.getArmor().getCardType() == CardType.TENG_JIA
                    && "FIRE".equals(natureStr);
            int chainBaseDamage = damage + (shaHasTengJiaFire ? 1 : 0);

            // 使用统一入口计算最终伤害（先藤甲+1，再白银狮子减至1）
            int finalDamage = calculateFinalDamage(state, responder, damage, natureStr);
            responder.takeDamage(finalDamage);
            state.addLog(responder.getUsername() + " 受到" + finalDamage + "点伤害" +
                    (rawShaResolveId != null ? " [sha:" + rawShaResolveId + "]" : ""));
            state.addLog(attacker.getUsername() + " 的杀命中" +
                    (rawShaResolveId != null ? " [sha:" + rawShaResolveId + "]" : ""));
            int shaChainDamage = chainBaseDamage;

            // 铁索连环传导（使用含藤甲的实际伤害值）
            // 注意：propagateChainDamage 不再检查濒死，由下方统一处理
            boolean attackerWasChained = attacker != null && attacker.isChained();
            if (("FIRE".equals(natureStr) || "THUNDER".equals(natureStr)) && responder.isChained()) {
                propagateChainDamage(state, attacker, responder, shaChainDamage, natureStr);
            }

            // 统一濒死检查：原始目标优先，铁索传导目标（场上唯一其他存活玩家）次之
            List<GamePlayer> dyingCandidates = new ArrayList<>();
            dyingCandidates.add(responder);
            if (attackerWasChained && attacker != null && !attacker.getUserId().equals(responder.getUserId())) {
                dyingCandidates.add(attacker);
            }
            ActionResult dying = checkDyingForAll(state, dyingCandidates);
            if (dying != null) return dying;

            state.checkGameOver();
            if (state.isFinished()) return success("连环传导导致游戏结束");

            // 游戏未结束时触发伤害事件（麒麟弓等装备效果）
            if (!state.isFinished()) {
                GameAction oldAction = state.getPendingAction();
                fireEvent(new GameEvent(GameEventType.DAMAGE_DONE, state, attacker, responder, null, damage,
                        Map.of("effectType", effectType != null ? effectType : "SHA")));
                GameAction newAction = state.getPendingAction();
                // 仅当事件处理器设置了新的 pendingAction（不是原先的 RESPOND_SHAN）时才保留
                if (newAction != null && newAction != oldAction) {
                    return success("命中，" + damage + "点伤害，触发装备效果", newAction);
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
                // 借刀杀人：没出杀，失去武器（给发起者）
                String weaponId = extra != null ? (String) extra.get("weaponId") : null;
                if (weaponId != null && responder.getWeapon() != null &&
                        responder.getWeapon().getId().equals(weaponId)) {
                    GameCard weapon = removeEquipment(responder, state, responder.getWeapon().getCardType());
                    if (weapon != null) {
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
            case "ZHU_QUE_YU_SHAN" -> handleZhuQue(state, userId, cardId, pending);
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

        // 防重复伤害机制
        Object rawShaResolveId = extra.get("shaResolveId");
        if (rawShaResolveId != null) {
            String damageKey = rawShaResolveId + ":" + target.getUserId();
            if (state.getResolvedDamageKeys().contains(damageKey)) {
                state.addLog("贯石斧跳过重复伤害（同一杀已造成伤害）");
                state.setPendingAction(null);
                return success("重复伤害已跳过");
            }
            state.getResolvedDamageKeys().add(damageKey);
        }

        // 计算铁索传导基础伤害（含藤甲+1）
        int guanChainDamage = damage + (target.getArmor() != null && target.getArmor().getCardType() == CardType.TENG_JIA && "FIRE".equals(natureStr) ? 1 : 0);
        // 使用统一入口计算最终伤害（先藤甲+1，再白银狮子减至1）
        int guanFinal = calculateFinalDamage(state, target, damage, natureStr);
        target.takeDamage(guanFinal);
        state.addLog(target.getUsername() + " 受到" + guanFinal + "点伤害（贯石斧）");

        // 铁索连环传导（使用含藤甲的实际伤害值）
        // propagateChainDamage 不再检查濒死，由下方统一处理
        boolean guanAttackerWasChained = attacker != null && attacker.isChained();
        if (("FIRE".equals(natureStr) || "THUNDER".equals(natureStr)) && target.isChained()) {
            propagateChainDamage(state, attacker, target, guanChainDamage, natureStr);
        }

        // 统一濒死检查：原始目标优先，铁索传导目标次之
        List<GamePlayer> guanDyingCandidates = new ArrayList<>();
        guanDyingCandidates.add(target);
        if (guanAttackerWasChained && attacker != null && !attacker.getUserId().equals(target.getUserId())) {
            guanDyingCandidates.add(attacker);
        }
        ActionResult guanDying = checkDyingForAll(state, guanDyingCandidates);
        if (guanDying != null) return guanDying;

        state.checkGameOver();
        if (state.isFinished()) return success("连环传导导致游戏结束");
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
        action.setExtraData(Map.of("hasJiuEffect", false, "damage", 1,
                "nature", shaCard.getNature().name(),
                "shaResolveId", UUID.randomUUID().toString()));

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
            // 古锭刀：仅对杀有效（effectTypeStr = null = 普通杀）
            if (effectTypeStr == null && attacker.getWeapon() != null && attacker.getWeapon().getCardType() == CardType.GU_DING_DAO
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
            // 计算铁索传导基础伤害（含藤甲+1）
            int hbChainBase = damage + (target.getArmor() != null && target.getArmor().getCardType() == CardType.TENG_JIA && "FIRE".equals(natureStr) ? 1 : 0);
            // 使用统一入口计算最终伤害（先藤甲+1，再白银狮子减至1）
            int hbFinal = calculateFinalDamage(state, target, damage, natureStr);

            // 防重复伤害机制
            Object rawShaResolveId = extra.get("shaResolveId");
            if (rawShaResolveId != null) {
                String damageKey = rawShaResolveId + ":" + target.getUserId();
                if (state.getResolvedDamageKeys().contains(damageKey)) {
                    state.addLog("寒冰剑跳过重复伤害（同一杀已造成伤害）");
                    state.setPendingAction(null);
                    return success("重复伤害已跳过");
                }
                state.getResolvedDamageKeys().add(damageKey);
            }

            target.takeDamage(hbFinal);
            state.addLog(target.getUsername() + " 受到" + hbFinal + "点伤害");
            int hanbingChainDamage = hbChainBase;

            // 铁索连环传导（使用含藤甲的实际伤害值）
            // propagateChainDamage 不再检查濒死，由下方统一处理
            boolean hanbingAttackerWasChained = attacker != null && attacker.isChained();
            if (("FIRE".equals(natureStr) || "THUNDER".equals(natureStr)) && target.isChained()) {
                propagateChainDamage(state, attacker, target, hanbingChainDamage, natureStr);
            }

            // 统一濒死检查：原始目标优先，铁索传导目标次之
            List<GamePlayer> hbDyingCandidates = new ArrayList<>();
            hbDyingCandidates.add(target);
            if (hanbingAttackerWasChained && attacker != null && !attacker.getUserId().equals(target.getUserId())) {
                hbDyingCandidates.add(attacker);
            }
            ActionResult hbDying = checkDyingForAll(state, hbDyingCandidates);
            if (hbDying != null) return hbDying;
            state.checkGameOver();
            if (state.isFinished()) return success("连环传导导致游戏结束");

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

        // 进入选择界面：展示目标装备 + 随机手牌选项
        int hbTotal = countDiscardableCards(target);
        int hbMaxPick = Math.min(2, hbTotal);
        List<String> hbChoiceIds = new ArrayList<>();
        List<Map<String, Object>> hbChoiceCards = new ArrayList<>();
        if (target.getWeapon() != null) {
            hbChoiceIds.add(target.getWeapon().getId());
            hbChoiceCards.add(cardToClientMap(target.getWeapon()));
        }
        if (target.getArmor() != null) {
            hbChoiceIds.add(target.getArmor().getId());
            hbChoiceCards.add(cardToClientMap(target.getArmor()));
        }
        if (target.getPlusHorse() != null) {
            hbChoiceIds.add(target.getPlusHorse().getId());
            hbChoiceCards.add(cardToClientMap(target.getPlusHorse()));
        }
        if (target.getMinusHorse() != null) {
            hbChoiceIds.add(target.getMinusHorse().getId());
            hbChoiceCards.add(cardToClientMap(target.getMinusHorse()));
        }
        if (!target.getHandCards().isEmpty()) {
            hbChoiceIds.add("__RANDOM_HAND__");
            Map<String, Object> handPh = new HashMap<>();
            handPh.put("id", "__RANDOM_HAND__");
            handPh.put("type", "HAND_CARD");
            handPh.put("displayName", "随机手牌×" + target.getHandCards().size());
            handPh.put("category", "基本牌");
            handPh.put("suit", "");
            handPh.put("suitName", "");
            handPh.put("number", 0);
            handPh.put("numberDisplay", "");
            hbChoiceCards.add(handPh);
        }
        GameAction hbAction = new GameAction();
        hbAction.setActionType("HAN_BING_CHOOSE");
        hbAction.setSourcePlayerId(attacker.getUserId());
        hbAction.setOptionalCardIds(hbChoiceIds);
        hbAction.setOptionalCards(hbChoiceCards);
        hbAction.setOptionalTargetIds(Collections.singletonList(attacker.getUserId()));
        hbAction.setDiscardCount(hbMaxPick);
        hbAction.setMessage("寒冰剑：选择要弃置的牌（最多" + hbMaxPick + "张）");
        Map<String, Object> hbExtra = new HashMap<>();
        hbExtra.put("targetId", target.getUserId());
        hbExtra.put("targetName", target.getUsername());
        hbAction.setExtraData(hbExtra);
        state.setPendingAction(hbAction);
        return success("寒冰剑：请选择要弃置的牌", hbAction);
    }

    /**
     * 寒冰剑弃牌选择响应
     */
    private ActionResult handleHanBingChoose(GameState state, Long userId, List<String> cardIds, GameAction pending) {
        GamePlayer attacker = findPlayerById(state, userId);
        if (attacker == null) return failure("未找到玩家");
        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        Long targetId = Long.valueOf(extra.get("targetId").toString());
        GamePlayer target = findPlayerById(state, targetId);
        if (target == null) return failure("未找到目标");

        if (cardIds == null || cardIds.isEmpty()) {
            state.addLog(attacker.getUsername() + " 发动寒冰剑，但选择不弃牌");
            state.setPendingAction(null);
            return success("寒冰剑：不弃牌");
        }

        int discarded = 0;
        int maxDiscard = Math.min(2, countDiscardableCards(target));
        java.util.Random rand = new java.util.Random();
        boolean wantRandom = false;

        for (String cid : cardIds) {
            if ("__RANDOM_HAND__".equals(cid)) {
                wantRandom = true;
            } else {
                GameCard equip = findCard(target, cid);
                if (equip == null) {
                    // 判定区牌
                    equip = target.getJudgeArea().stream().filter(c -> c.getId().equals(cid)).findFirst().orElse(null);
                    if (equip != null) {
                        target.getJudgeArea().removeIf(c -> c.getId().equals(cid));
                        state.discardCard(equip);
                        discarded++;
                    }
                } else if (equip.getCardType().isEquipment()) {
                    removeEquipment(target, state, equip.getCardType());
                    state.discardCard(equip);
                    discarded++;
                }
            }
        }

        // 随机手牌：填满剩余弃牌名额（最多到 maxDiscard 张）
        if (wantRandom && !target.getHandCards().isEmpty()) {
            int randomCount = Math.min(maxDiscard - discarded, target.getHandCards().size());
            for (int i = 0; i < randomCount; i++) {
                int idx = rand.nextInt(target.getHandCards().size());
                GameCard hc = target.getHandCards().get(idx);
                target.removeHandCard(hc.getId());
                state.discardCard(hc);
                discarded++;
            }
        }

        state.addLog(attacker.getUsername() + " 发动寒冰剑，弃置了" + target.getUsername() + "的" + discarded + "张牌");
        state.setPendingAction(null);
        return success("寒冰剑：弃" + discarded + "张牌代替伤害");
    }

    /**
     * 朱雀羽扇：处理是否发动转化火杀的响应
     * cardId != null → 发动（转为火杀）
     * cardId == null → 不发动（保持普通杀）
     */
    @SuppressWarnings("unchecked")
    private ActionResult handleZhuQue(GameState state, Long userId, String cardId, GameAction pending) {
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();

        GamePlayer player = findPlayerById(state, userId);
        if (player == null) return failure("未找到玩家");

        // 所有上下文从extraData读取，不依赖tempCards
        String shaCardId = (String) extra.get("cardId");
        if (shaCardId == null) return failure("杀数据丢失");
        boolean shaIsBlack = Boolean.TRUE.equals(extra.get("isBlack"));

        String targetUserId = (String) extra.get("targetUserId");
        GamePlayer target;
        if (targetUserId != null) {
            target = findPlayerById(state, Long.valueOf(targetUserId));
        } else {
            target = player.getOpponent(state.getPlayers());
        }
        if (target == null || !target.isAlive()) {
            return failure("目标已死亡或无效");
        }

        boolean hasJiuEffect = Boolean.TRUE.equals(extra.get("hasJiuEffect"));
        boolean hasQingGang = Boolean.TRUE.equals(extra.get("hasQingGang"));
        boolean hasZhugeNu = Boolean.TRUE.equals(extra.get("hasZhugeNu"));

        GameCard.Nature shaNature;
        if (cardId != null) {
            // 发动朱雀羽扇：转为火杀
            shaNature = GameCard.Nature.FIRE;
            state.addLog(player.getUsername() + " 发动朱雀羽扇，将【杀】改为【火杀】");
        } else {
            // 不发动：保持普通杀
            shaNature = GameCard.Nature.NORMAL;
            state.addLog(player.getUsername() + " 选择不发动朱雀羽扇");
        }

        return createShaRespondAction(state, player, shaCardId, shaIsBlack, target, targetUserId,
                shaNature, hasJiuEffect, hasQingGang, hasZhugeNu);
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
            // actionId 校验：防止 USE_SKILL 重复消费同个 pendingAction
            if (req.getActionId() == null || !req.getActionId().equals(currentPending.getActionId())) {
                return failure("响应已过期");
            }
            state.setPendingAction(null);
            return onShaPlayed(state, player, currentPending,
                    player.getUsername() + " 使用丈八蛇矛出杀");
        } else {
            // 主动出牌阶段使用
            if (req.getTargetUserId() == null) return failure("请选择目标");
            GamePlayer target = findPlayerById(state, Long.valueOf(req.getTargetUserId()));
            if (target == null || !target.isAlive()) return failure("目标无效");

            // 丈八蛇矛主动当杀计入本回合出杀次数
            boolean zhangBaHasZhugeNu = player.getWeapon() != null &&
                    player.getWeapon().getCardType() == CardType.ZHUGE_LIAN_NU;
            if (!zhangBaHasZhugeNu && player.isUsedShaThisTurn()) {
                return failure("本回合已使用过杀");
            }
            if (!zhangBaHasZhugeNu) {
                player.setUsedShaThisTurn(true);
            }
            player.setShaCountThisTurn(player.getShaCountThisTurn() + 1);

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
            action.setExtraData(Map.of("hasJiuEffect", false, "damage", 1,
                    "nature", "NORMAL", "isBlack", false, "hasQingGang", false,
                    "shaResolveId", UUID.randomUUID().toString()));

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
                // 选择了指定装备/判定区牌弃置
                GameCard discardCard = findCard(target, cardId);
                if (discardCard == null) {
                    // 可能在判定区（findCard只查手牌+装备，不查判定区）
                    discardCard = target.getJudgeArea().stream().filter(c -> c.getId().equals(cardId)).findFirst().orElse(null);
                }
                if (discardCard != null) {
                    if (discardCard.getCardType().isEquipment()) {
                        removeEquipment(target, state, discardCard.getCardType());
                    } else if (discardCard.getCardType().isDelayTrick()) {
                        target.getJudgeArea().removeIf(c -> c.getId().equals(cardId));
                    } else {
                        target.unequip(discardCard.getCardType());
                    }
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
                // 选择了指定装备/判定区牌获得
                GameCard stealCard = findCard(target, cardId);
                if (stealCard == null) {
                    stealCard = target.getJudgeArea().stream().filter(c -> c.getId().equals(cardId)).findFirst().orElse(null);
                }
                if (stealCard != null) {
                    if (stealCard.getCardType().isEquipment()) {
                        removeEquipment(target, state, stealCard.getCardType());
                    } else if (stealCard.getCardType().isDelayTrick()) {
                        target.getJudgeArea().removeIf(c -> c.getId().equals(cardId));
                    } else {
                        target.unequip(stealCard.getCardType());
                    }
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
                // 动态检测：如果当前有玩家持有无懈可击（可能刚从此五谷中拿到），打开无懈窗口
                if (hasAnyWuxie(state)) {
                    // 获取原始 AOE 上下文，继续五谷的逐目标无懈流程
                    Map<String, Object> aoeExtra = (Map<String, Object>) extra.get("aoeOriginalExtra");
                    if (aoeExtra != null) {
                        // 记录已选牌玩家，finishAoe 将排除他们
                        List<String> alreadyPicked = (List<String>) aoeExtra.get("wuguAlreadyPicked");
                        alreadyPicked.add(String.valueOf(userId));

                        // 保存当前剩余卡牌快照（已有牌被选走）
                        List<String> remainingIds = state.getTempCards().stream().map(GameCard::getId).toList();
                        List<Map<String, Object>> remainingInfos = state.getTempCards().stream().map(this::cardToClientMap).toList();
                        aoeExtra.put("wuguCurrentCardIds", remainingIds);
                        aoeExtra.put("wuguCurrentCardInfos", remainingInfos);

                        // 剩余待选玩家作为新目标，重置无懈栈
                        List<String> remainingTargets = new ArrayList<>();
                        for (int i = nextIndex; i < pickerOrder.size(); i++) {
                            remainingTargets.add(String.valueOf(pickerOrder.get(i)));
                        }
                        aoeExtra.put("pendingTargetUserIds", remainingTargets);
                        aoeExtra.put("currentTargetIndex", 0);
                        aoeExtra.put("wuxieStack", new ArrayList<String>());
                        aoeExtra.put("respondedSkipIds", new ArrayList<Long>());

                        // 进入无懈框架：无人有无懈则跳过 → resolveAoeTarget → finishAoe
                        return advanceToNextWuxieResponder(state, aoeExtra,
                                aoeExtra.get("sourcePlayerId") instanceof Number
                                        ? ((Number) aoeExtra.get("sourcePlayerId")).longValue() : null);
                    }
                }

                // 继续正常选牌
                // 在原始 aoeExtra 中记录此玩家已选牌，确保后续可能的 wuxie 重入时
                // finishAoe 能正确排除已选玩家
                Map<String, Object> aoeOriginal = (Map<String, Object>) extra.get("aoeOriginalExtra");
                if (aoeOriginal != null) {
                    List<String> alreadyPickedAoe = (List<String>) aoeOriginal.get("wuguAlreadyPicked");
                    if (alreadyPickedAoe != null) {
                        alreadyPickedAoe.add(String.valueOf(userId));
                    }
                }
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
                Map<String, Object> nextExtra = new LinkedHashMap<>();
                nextExtra.put("pickerOrder", new ArrayList<>(pickerOrder));
                nextExtra.put("pickerIndex", nextIndex);
                nextExtra.put("aoeOriginalExtra", extra.get("aoeOriginalExtra"));
                newAction.setExtraData(nextExtra);

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
    private ActionResult handleDiscardPhase(GameState state, Long userId, List<String> cardIds, GameAction pending) {
        GamePlayer player = findPlayerById(state, userId);
        if (player == null) return failure("未找到玩家");

        int requiredCount = pending.getDiscardCount();
        if (requiredCount <= 0) {
            state.nextPhase();
            state.setPendingAction(null);
            return success("无需弃牌");
        }

        // 校验 cardIds
        if (cardIds == null || cardIds.isEmpty()) {
            return failure("请选择要弃置的手牌");
        }
        if (cardIds.size() != requiredCount) {
            return failure("需要弃置 " + requiredCount + " 张牌，收到 " + cardIds.size() + " 张");
        }
        // 检查重复
        if (cardIds.stream().distinct().count() != cardIds.size()) {
            return failure("存在重复的卡牌ID");
        }
        // 检查每张牌是否属于当前玩家手牌
        List<GameCard> toDiscard = new ArrayList<>();
        for (String cid : cardIds) {
            GameCard card = findCard(player, cid);
            if (card == null) {
                return failure("卡牌 " + cid + " 不在手牌中");
            }
            toDiscard.add(card);
        }

        // 全部校验通过，统一移除
        for (GameCard card : toDiscard) {
            player.removeHandCard(card.getId());
            state.discardCard(card);
        }
        state.addLog(player.getUsername() + " 弃置了 " + requiredCount + " 张牌");

        state.nextPhase();
        state.setPendingAction(null);
        return success("弃牌完成");
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

        // 调用handleResponse携带当前 actionId
        return handleResponse(state, userId, null, null, null, pending.getActionId());
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
     * 检查玩家是否濒死，如果是则创建队列式 DYING_REQUIRE_TAO 响应链
     * @return 如果有濒死action返回ActionResult，否则返回null（继续正常流程）
     */
    private ActionResult checkDying(GameState state, GamePlayer player) {
        if (player.getCurrentHp() > 0) return null;

        // 玩家处于濒死，临时存活以便求援
        player.setAlive(true);

        // 按玩家顺序构建响应队列
        List<Long> responderQueue = state.getPlayers().stream()
                .filter(GamePlayer::isAlive)
                .map(GamePlayer::getUserId)
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> dyingExtra = new HashMap<>();
        dyingExtra.put("dyingPlayerId", player.getUserId());
        dyingExtra.put("responderQueue", responderQueue);
        dyingExtra.put("currentResponderIndex", 0);

        // AOE/连锁 continuation：从当前 pending action 传播 aoeContext
        GameAction currentPending = state.getPendingAction();
        if (currentPending != null && currentPending.getExtraData() instanceof Map) {
            Object ctx = ((Map<?, ?>) currentPending.getExtraData()).get("aoeContext");
            if (ctx != null) {
                dyingExtra.put("aoeContext", ctx);
            }
        }

        state.addLog(player.getUsername() + " 进入濒死状态");
        return advanceToNextDyingResponder(state, dyingExtra);
    }

    /**
     * 推进濒死响应队列至下一个有资格的救援者
     * 依次检查 responderQueue 中每一位玩家：
     * - 死亡玩家跳过
     * - 只有【桃】（其他玩家）/【桃】或【酒】（濒死者本人）可响应
     * - 无可救援牌则自动跳过
     * - 队列耗尽且仍濒死则死亡
     */
    @SuppressWarnings("unchecked")
    private ActionResult advanceToNextDyingResponder(GameState state, Map<String, Object> dyingExtra) {
        List<Long> responderQueue = (List<Long>) dyingExtra.get("responderQueue");
        int currentIndex = (int) dyingExtra.get("currentResponderIndex");
        Long dyingUserId = (Long) dyingExtra.get("dyingPlayerId");
        GamePlayer dyingPlayer = findPlayerById(state, dyingUserId);
        if (dyingPlayer == null) return failure("未找到濒死玩家");

        while (currentIndex < responderQueue.size()) {
            Long responderId = responderQueue.get(currentIndex);
            GamePlayer responder = findPlayerById(state, responderId);

            if (responder == null || !responder.isAlive()) {
                currentIndex++;
                continue;
            }

            // 确定可用救援牌：濒死者可用【桃】或【酒】，其他玩家只能用【桃】
            boolean isSelf = responderId.equals(dyingUserId);
            List<String> rescueCardIds = new ArrayList<>();
            for (GameCard c : responder.getHandCards()) {
                if (c.getCardType() == CardType.TAO) {
                    rescueCardIds.add(c.getId());
                } else if (isSelf && c.getCardType() == CardType.JIU) {
                    rescueCardIds.add(c.getId());
                }
            }

            if (rescueCardIds.isEmpty()) {
                // 无可用救援牌，自动跳过
                currentIndex++;
                continue;
            }

            // 找到有资格响应者
            dyingExtra.put("currentResponderIndex", currentIndex);

            GameAction action = new GameAction();
            action.setActionType("DYING_REQUIRE_TAO");
            action.setSourcePlayerId(dyingUserId);
            action.setOptionalCardIds(rescueCardIds);
            action.setOptionalCards(cardIdsToClientMap(state, rescueCardIds));
            action.setOptionalTargetIds(Collections.singletonList(responderId));
            action.setExtraData(dyingExtra);

            if (isSelf) {
                action.setMessage("你处于濒死状态，请使用【桃】或【酒】自救");
            } else {
                action.setMessage(dyingPlayer.getUsername() + " 濒死，是否使用【桃】救援？");
            }

            state.setPendingAction(action);
            return success("等待濒死响应", action);
        }

        // 队列耗尽 → 死亡
        dyingPlayer.setAlive(false);
        state.setPendingAction(null);
        state.addLog(dyingPlayer.getUsername() + " 死亡");
        // 铁索传导后可能有多个玩家濒死，先检查其他濒死玩家再判断游戏结束
        for (GamePlayer p : state.getPlayers()) {
            if (p.getUserId() != dyingUserId && p.getCurrentHp() <= 0) {
                state.addLog("检测到 " + p.getUsername() + " 也需要濒死处理");
                return checkDying(state, p);
            }
        }
        state.checkGameOver();
        return success("玩家死亡");
    }

    /**
     * 处理濒死响应（使用救援牌或跳过）
     */
    @SuppressWarnings("unchecked")
    private ActionResult handleDying(GameState state, Long userId, String cardId, GameAction pending) {
        Map<String, Object> dyingExtra = (Map<String, Object>) pending.getExtraData();
        Long dyingUserId = (Long) dyingExtra.get("dyingPlayerId");
        GamePlayer dyingPlayer = findPlayerById(state, dyingUserId);
        if (dyingPlayer == null) return failure("未找到濒死玩家");

        int currentIndex = (int) dyingExtra.get("currentResponderIndex");
        List<Long> responderQueue = (List<Long>) dyingExtra.get("responderQueue");
        if (currentIndex >= responderQueue.size() || !responderQueue.get(currentIndex).equals(userId)) {
            return failure("不是你的响应回合");
        }

        if (cardId != null) {
            GamePlayer responder = findPlayerById(state, userId);
            GameCard card = findCard(responder, cardId);
            if (card == null) return failure("未找到卡牌");

            boolean isSelf = userId.equals(dyingUserId);
            if (card.getCardType() == CardType.JIU && !isSelf) {
                return failure("不能使用【酒】救援其他玩家");
            }
            if (card.getCardType() != CardType.TAO && card.getCardType() != CardType.JIU) {
                return failure("需要【桃】或【酒】");
            }

            responder.removeHandCard(cardId);
            state.discardCard(card);
            dyingPlayer.setAlive(true);
            dyingPlayer.heal(1);
            state.addLog(responder.getUsername() + " 使用" + card.getCardType().getDisplayName()
                    + "救援" + dyingPlayer.getUsername() + "，回复1点体力");

            if (dyingPlayer.getCurrentHp() > 0) {
                // 脱离濒死
                state.setPendingAction(null);
                state.addLog(dyingPlayer.getUsername() + " 脱离濒死");
                // 铁索传导后可能有多个玩家濒死，检查其他玩家是否需要濒死处理
                for (GamePlayer p : state.getPlayers()) {
                    if (p.getUserId() != dyingUserId && p.getCurrentHp() <= 0) {
                        state.addLog("检测到 " + p.getUsername() + " 也需要濒死处理");
                        return checkDying(state, p);
                    }
                }
                return success("救援成功");
            }

            // 仍濒死
            if (isSelf) {
                // 濒死者自己使用后仍濒死 → 继续询问自己（可以连续使用多张桃/酒）
                return advanceToNextDyingResponder(state, dyingExtra);
            } else {
                // 其他玩家使用后仍濒死 → 推进至下一响应者
                dyingExtra.put("currentResponderIndex", currentIndex + 1);
                return advanceToNextDyingResponder(state, dyingExtra);
            }
        } else {
            // 跳过，推进至下一响应者
            dyingExtra.put("currentResponderIndex", currentIndex + 1);
            return advanceToNextDyingResponder(state, dyingExtra);
        }
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
        // 火杀/雷杀显示名称
        String displayName;
        if (card.getCardType() == CardType.SHA) {
            displayName = switch (card.getNature()) {
                case FIRE -> "火杀";
                case THUNDER -> "雷杀";
                default -> "杀";
            };
        } else {
            displayName = card.getCardType().getDisplayName();
        }
        m.put("displayName", displayName);
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
                removeEquipment(player, state, c.getCardType());
            }
            state.discardCard(c);
        }
    }

    /**
     * 在伤害结算（含铁索传导）后统一检查所有受影响玩家的濒死状态。
     * 按顺序检查：原始受伤者 -> 横扫传导目标 -> 其他受伤者。
     * 返回第一个濒死玩家的 pendingAction，其余玩家在濒死结算恢复后由 handleDying 继续检查。
     */
    private ActionResult checkDyingForAll(GameState state, List<GamePlayer> affectedPlayers) {
        for (GamePlayer p : affectedPlayers) {
            if (p != null && p.getCurrentHp() <= 0) {
                ActionResult r = checkDying(state, p);
                if (r != null) return r;
            }
        }
        return null;
    }

    /**
     * 计算最终伤害，考虑防具效果（藤甲火焰+1、白银狮子减伤）。
     * 规则：先计算藤甲火焰+1，再由白银狮子将最终伤害降至1。
     * 此方法会修改 state 中的日志。
     */
    private int calculateFinalDamage(GameState state, GamePlayer target, int baseDamage, String nature) {
        int damage = baseDamage;
        boolean hasSilverLion = target.getArmor() != null && target.getArmor().getCardType() == CardType.BAI_YIN;
        boolean hasTengjiaFire = target.getArmor() != null && target.getArmor().getCardType() == CardType.TENG_JIA && "FIRE".equals(nature);

        // 先计算藤甲火焰+1
        if (hasTengjiaFire) {
            damage++;
            state.addLog("藤甲使火焰伤害+1");
        }

        // 再由白银狮子减伤至1
        if (hasSilverLion && damage > 1) {
            state.addLog("白银狮子效果触发，伤害减至1点");
            damage = 1;
        }

        return damage;
    }

    /**
     * 移除玩家装备区指定类型的装备，并触发失去装备的效果（如白银狮子回血）。
     * 返回被移除的装备卡牌，如果没有则返回null。
     */
    private GameCard removeEquipment(GamePlayer player, GameState state, CardType equipSlot) {
        GameCard removed = player.unequip(equipSlot);
        if (removed != null) {
            // 白银狮子：失去时回复1点体力
            if (removed.getCardType() == CardType.BAI_YIN) {
                int healed = player.heal(1);
                if (healed > 0) {
                    state.addLog(player.getUsername() + " 失去白银狮子，回复1点体力");
                }
            }
        }
        return removed;
    }

    /**
     * 铁索连环属性伤害传导。
     * 对横置目标造成火/雷伤害后调用，将同点数伤害传导给其他横置角色。
     * 注意：此方法不再检查濒死（checkDying），由各调用者统一处理。
     * 注意：此方法不再触发 fireEvent，由各调用者统一处理。
     * 设 state.chainDamageInProgress = true 以防重入。
     */
    private void propagateChainDamage(GameState state, GamePlayer source, GamePlayer target,
                                       int damage, String nature) {
        if (!"FIRE".equals(nature) && !"THUNDER".equals(nature)) return;
        if (!target.isChained()) return;
        if (state.isChainDamageInProgress()) {
            log.warn("[CHAIN GUARD] chainDamageInProgress=true, skipping re-entry for target={} source={}", target.getUserId(), source != null ? source.getUserId() : null);
            return;
        }

        state.setChainDamageInProgress(true);
        try {
            target.setChained(false);
            state.addLog(target.getUsername() + " 解除横置状态，触发连环");

            for (GamePlayer p : state.getAlivePlayers()) {
                if (p.getUserId().equals(target.getUserId())) continue;
                if (!p.isChained()) continue;

                p.setChained(false);

                // 计算实际传导伤害（每名目标独立判定藤甲火焰+1和白银狮子减伤）
                int actualDamage = damage;
                if ("FIRE".equals(nature) && p.getArmor() != null && p.getArmor().getCardType() == CardType.TENG_JIA) {
                    actualDamage++;
                    state.addLog("藤甲效果触发，火焰伤害+1");
                }
                if (p.getArmor() != null && p.getArmor().getCardType() == CardType.BAI_YIN && actualDamage > 1) {
                    state.addLog("白银狮子效果触发，伤害减至1点");
                    actualDamage = 1;
                }

                p.takeDamage(actualDamage);
                state.addLog(p.getUsername() + " 受到" + actualDamage + "点连环传导伤害");
                // 注意：checkDying 由调用者在所有传导完成后统一检查
            }
        } finally {
            state.setChainDamageInProgress(false);
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