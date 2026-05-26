package com.sanguosha.game.engine;

import com.sanguosha.game.card.CardType;
import com.sanguosha.game.card.GameCard;
import com.sanguosha.game.context.CardPool;
import com.sanguosha.game.player.GamePlayer;
import com.sanguosha.game.state.GameAction;
import com.sanguosha.game.state.GameState;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1v1 游戏引擎 - 核心游戏逻辑
 */
@Component
public class GameEngine {

    private final ConcurrentHashMap<String, GameState> games = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> roomGameIndex = new ConcurrentHashMap<>();

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

        switch (phase) {
            case "PREPARE" -> {
                state.addLog(current.getUsername() + " 的准备阶段");
                state.nextPhase();
                return processPhase(state);
            }
            case "JUDGE" -> {
                state.addLog(current.getUsername() + " 的判定阶段");
                // 处理延时锦囊判定
                if (!current.getJudgeArea().isEmpty()) {
                    GameCard judgeCard = current.getJudgeArea().get(0);
                    current.getJudgeArea().remove(0);
                    state.discardCard(judgeCard);
                    state.addLog(current.getUsername() + " 判定：" + judgeCard.getCardType().getDisplayName());
                }
                state.nextPhase();
                return processPhase(state);
            }
            case "DRAW" -> {
                // 摸牌阶段 - 摸2张
                List<GameCard> drawn = state.drawCards(2);
                if (drawn.isEmpty()) {
                    state.addLog("牌堆已空，" + current.getUsername() + " 无法摸牌");
                } else {
                    current.drawCards(drawn);
                    state.addLog(current.getUsername() + " 摸了 " + drawn.size() + " 张牌");
                }
                state.nextPhase();
                return processPhase(state);
            }
            case "PLAY" -> {
                state.addLog(current.getUsername() + " 的出牌阶段");
                // 重置出牌阶段状态
                current.resetTurnState();
                // 不出牌直接结束出牌阶段，可通过 endPlayPhase 触发进入DISCARD
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
     * 玩家出牌
     */
    public ActionResult playCard(GameState state, Long userId, String cardId,
                                  String targetUserId, String targetCardId) {
        GamePlayer player = findPlayer(state, userId);
        if (player == null) return failure("未找到玩家");

        // 检查是否是当前玩家的回合
        if (!state.getCurrentPlayer().getUserId().equals(userId)) {
            return failure("不是你的回合");
        }

        // 检查是否在出牌阶段
        if (!"PLAY".equals(state.getPhase())) {
            return failure("不在出牌阶段");
        }

        // 查找手牌
        GameCard card = findCard(player, cardId);
        if (card == null) return failure("未找到该卡牌");

        CardType type = card.getCardType();

        if (type.isBasic()) {
            return playBasicCard(state, player, card, targetUserId);
        } else if (type.isTrick()) {
            return playTrickCard(state, player, card, targetUserId);
        } else if (type.isEquipment()) {
            return playEquipmentCard(state, player, card);
        } else if (type.isDelayTrick()) {
            return playDelayTrick(state, player, card, targetUserId);
        }

        return failure("未知卡牌类型");
    }

    /**
     * 使用基本牌
     */
    private ActionResult playBasicCard(GameState state, GamePlayer player, GameCard card, String targetUserId) {
        return switch (card.getCardType()) {
            case SHA -> useSha(state, player, card, targetUserId);
            case TAO -> useTao(state, player, card);
            case JIU -> useJiu(state, player, card);
            default -> failure("不支持的基本牌");
        };
    }

    /**
     * 使用杀
     */
    private ActionResult useSha(GameState state, GamePlayer player, GameCard card, String targetUserId) {
        // 检查本回合是否已使用过杀
        if (player.isUsedShaThisTurn()) {
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
        action.setExtraData(Map.of("hasJiuEffect", hasJiuEffect, "damage", 1));

        state.setPendingAction(action);

        state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了" + card.getCardType().getDisplayName());
        player.setUsedShaThisTurn(true);
        player.setShaCountThisTurn(player.getShaCountThisTurn() + 1);

        return success("杀已使用,等待对手响应", action);
    }

    /**
     * 使用桃
     */
    private ActionResult useTao(GameState state, GamePlayer player, GameCard card) {
        if (player.getCurrentHp() >= player.getMaxHp()) {
            return failure("体力已满，不能使用桃");
        }

        player.removeHandCard(card.getId());
        player.heal(1);
        state.addLog(player.getUsername() + " 使用了桃，回复1点体力");
        state.discardCard(card);

        return success("使用桃成功");
    }

    /**
     * 使用酒
     */
    private ActionResult useJiu(GameState state, GamePlayer player, GameCard card) {
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
     * 使用锦囊牌
     */
    private ActionResult playTrickCard(GameState state, GamePlayer player, GameCard card, String targetUserId) {
        return switch (card.getCardType()) {
            case WU_ZHONG -> {
                player.removeHandCard(card.getId());
                List<GameCard> drawn = state.drawCards(2);
                player.drawCards(drawn);
                state.discardCard(card);
                state.addLog(player.getUsername() + " 使用了无中生有，摸了2张牌");
                yield success("摸2张牌");
            }
            case GUO_HE -> {
                GamePlayer target = findPlayerById(state, targetUserId != null ?
                        Long.valueOf(targetUserId) : state.getOpponent().getUserId());
                if (target == null || !target.isAlive()) yield failure("目标无效");

                // 构建可选目标：对手的牌（手牌+装备）
                List<String> targetCards = new ArrayList<>();
                targetCards.addAll(target.getHandCards().stream().map(GameCard::getId).toList());
                if (target.getWeapon() != null) targetCards.add(target.getWeapon().getId());
                if (target.getArmor() != null) targetCards.add(target.getArmor().getId());
                if (target.getPlusHorse() != null) targetCards.add(target.getPlusHorse().getId());
                if (target.getMinusHorse() != null) targetCards.add(target.getMinusHorse().getId());

                if (targetCards.isEmpty()) yield failure("目标没有可拆的牌");

                player.removeHandCard(card.getId());
                state.discardCard(card);

                GameAction action = new GameAction();
                action.setActionType("CHOOSE_TARGET_CARD");
                action.setSourceCardId(card.getId());
                action.setSourcePlayerId(player.getUserId());
                action.setOptionalCardIds(targetCards);
                action.setOptionalCards(cardIdsToClientMap(state, targetCards));
                action.setOptionalTargetIds(Collections.singletonList(target.getUserId()));
                action.setMessage("请选择要弃置的牌（过河拆桥）");
                action.setExtraData(Map.of("effectType", "GUO_HE"));

                state.setPendingAction(action);
                state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了过河拆桥");
                yield success("过河拆桥已使用", action);
            }
            case SHUN_SHOU -> {
                GamePlayer target = findPlayerById(state, targetUserId != null ?
                        Long.valueOf(targetUserId) : state.getOpponent().getUserId());
                if (target == null || !target.isAlive()) yield failure("目标无效");

                // 计算距离
                int dist = player.calculateDistanceTo(target);
                if (dist > 1) yield failure("距离不足");

                List<String> targetCards = new ArrayList<>();
                targetCards.addAll(target.getHandCards().stream().map(GameCard::getId).toList());
                if (target.getWeapon() != null) targetCards.add(target.getWeapon().getId());
                if (target.getArmor() != null) targetCards.add(target.getArmor().getId());
                if (target.getPlusHorse() != null) targetCards.add(target.getPlusHorse().getId());
                if (target.getMinusHorse() != null) targetCards.add(target.getMinusHorse().getId());

                if (targetCards.isEmpty()) yield failure("目标没有可顺的牌");

                player.removeHandCard(card.getId());
                state.discardCard(card);

                GameAction action = new GameAction();
                action.setActionType("CHOOSE_TARGET_CARD");
                action.setSourceCardId(card.getId());
                action.setSourcePlayerId(player.getUserId());
                action.setOptionalCardIds(targetCards);
                action.setOptionalCards(cardIdsToClientMap(state, targetCards));
                action.setOptionalTargetIds(Collections.singletonList(target.getUserId()));
                action.setMessage("请选择要获得的牌（顺手牵羊）");
                action.setExtraData(Map.of("effectType", "SHUN_SHOU"));

                state.setPendingAction(action);
                state.addLog(player.getUsername() + " 对 " + target.getUsername() + " 使用了顺手牵羊");
                yield success("顺手牵羊已使用", action);
            }
            case JUE_DOU -> {
                GamePlayer target = findPlayerById(state, targetUserId != null ?
                        Long.valueOf(targetUserId) : state.getOpponent().getUserId());
                if (target == null || !target.isAlive()) yield failure("目标无效");

                player.removeHandCard(card.getId());
                state.discardCard(card);

                // 设置决斗响应：目标需要出杀
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
                yield success("决斗已使用", action);
            }
            case NAN_MAN -> {
                player.removeHandCard(card.getId());
                state.discardCard(card);

                GamePlayer target = state.getOpponent();
                if (!target.isAlive()) yield success("南蛮入侵已使用，无存活目标");

                List<String> shaCards = target.getHandCards().stream()
                        .filter(c -> c.getCardType() == CardType.SHA)
                        .map(GameCard::getId)
                        .toList();

                // 藤甲免疫
                boolean hasTengJia = target.getArmor() != null &&
                        target.getArmor().getCardType() == CardType.TENG_JIA;

                if (hasTengJia) {
                    state.addLog(target.getUsername() + " 的藤甲免疫了南蛮入侵");
                    yield success("南蛮入侵被藤甲免疫");
                }

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
                yield success("南蛮入侵已使用", action);
            }
            case WAN_JIAN -> {
                player.removeHandCard(card.getId());
                state.discardCard(card);

                GamePlayer target = state.getOpponent();
                if (!target.isAlive()) yield success("万箭齐发已使用，无存活目标");

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
                yield success("万箭齐发已使用", action);
            }
            case TAO_YUAN -> {
                player.removeHandCard(card.getId());
                state.discardCard(card);

                for (GamePlayer p : state.getAlivePlayers()) {
                    int healed = p.heal(1);
                    if (healed > 0) {
                        state.addLog(p.getUsername() + " 回复了1点体力");
                    }
                }
                state.addLog(player.getUsername() + " 使用了桃园结义");
                yield success("桃园结义已使用，全员回复1点体力");
            }
            case WU_GU -> {
                player.removeHandCard(card.getId());
                state.discardCard(card);

                // 展示牌堆顶等同于角色数的牌
                int count = state.getAlivePlayers().size();
                List<GameCard> shown = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    GameCard c = state.drawCard();
                    if (c != null) shown.add(c);
                }

                List<String> shownIds = shown.stream().map(GameCard::getId).toList();
                state.addLog(player.getUsername() + " 使用了五谷丰登，展示" + shown.size() + "张牌");

                // 构建卡牌展示信息（从drawn card对象直接构建，而非从state查找）
                List<Map<String, Object>> wuguCardInfos = shown.stream().map(this::cardToClientMap).toList();

                GameAction action = new GameAction();
                action.setActionType("CHOOSE_WUGU_CARD");
                action.setSourceCardId(card.getId());
                action.setSourcePlayerId(player.getUserId());
                action.setOptionalCardIds(shownIds);
                action.setOptionalCards(wuguCardInfos);
                action.setMessage("请选择一张牌（五谷丰登）");
                action.setOptionalTargetIds(Collections.singletonList(player.getUserId()));
                action.setExtraData(Map.of("wuguCards", shownIds));

                state.setPendingAction(action);
                yield success("五谷丰登已使用", action);
            }
            case JIE_DAO -> {
                GamePlayer target = state.getOpponent();
                if (target == null || !target.isAlive() || target.getWeapon() == null) {
                    yield failure("借刀杀人：目标没有武器");
                }

                player.removeHandCard(card.getId());
                state.discardCard(card);

                // 需要目标使用杀
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
                yield success("借刀杀人已使用", action);
            }
            default -> failure("不支持的锦囊牌");
        };
    }

    /**
     * 使用装备牌
     */
    private ActionResult playEquipmentCard(GameState state, GamePlayer player, GameCard card) {
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
     * 使用延时锦囊
     */
    private ActionResult playDelayTrick(GameState state, GamePlayer player, GameCard card, String targetUserId) {
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

            state.addLog(responder.getUsername() + " 出闪成功");
            state.setPendingAction(null);

            // 判断是否为AOE效果
            if ("NAN_MAN".equals(effectType) || "WAN_JIAN".equals(effectType)) {
                // 响应成功，继续原流程
            }

            return success("出闪成功");
        } else {
            // 没有出闪 - 命中
            int damage = baseDamage;
            if (hasJiuEffect) {
                damage += 1;
                state.addLog("酒效果使伤害+1");
            }

            // 检查防具
            boolean armorEffective = true;
            if (responder.getArmor() != null) {
                CardType armorType = responder.getArmor().getCardType();
                if (armorType == CardType.REN_WANG && isBlackCard(pending.getSourceCardId(), attacker)) {
                    armorEffective = false;
                    state.addLog(responder.getUsername() + " 的仁王盾使黑色杀无效");
                    state.setPendingAction(null);
                    return success("杀被仁王盾抵挡");
                }
                if (armorType == CardType.TENG_JIA && effectType == null) {
                    // 普通杀藤甲无效
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

            // 藤甲火伤+1检查（朱雀羽扇）
            if (responder.getArmor() != null && responder.getArmor().getCardType() == CardType.TENG_JIA) {
                // 如果是火杀
                if (attacker.getWeapon() != null && attacker.getWeapon().getCardType() == CardType.ZHU_QUE) {
                    responder.takeDamage(1);
                    state.addLog("藤甲使火伤害+1");
                }
            }

            state.checkGameOver();
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
            state.addLog(responder.getUsername() + " 出杀响应");

            if ("JUE_DOU".equals(effectType)) {
                // 决斗：继续向对方要求出杀
                state.setPendingAction(null);
                // 交替：现在需要原发起者出杀
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
                // 借刀杀人：出了杀，不失去武器
                GamePlayer initiator = findPlayerById(state, pending.getSourcePlayerId());
                if (initiator != null) {
                    state.addLog(responder.getUsername() + " 出杀响应借刀杀人，保留武器");
                }
                return success("借刀杀人：出杀成功");
            }

            state.setPendingAction(null);
            return success("响应成功");
        } else {
            // 没有出杀
            if ("JUE_DOU".equals(effectType)) {
                // 决斗：没出杀的人受到伤害
                GamePlayer initiator = findPlayerById(state, pending.getSourcePlayerId());
                GamePlayer defender = responder;

                // 如果responder是发起者
                if (responder.getUserId().equals(pending.getSourcePlayerId())) {
                    // 发起者没出杀，自己受伤
                    responder.takeDamage(1);
                    state.addLog(responder.getUsername() + " 决斗失败，受到1点伤害");
                } else {
                    // 响应者没出杀，响应者受伤
                    responder.takeDamage(1);
                    state.addLog(responder.getUsername() + " 决斗失败，受到1点伤害");
                }
                state.checkGameOver();
                state.setPendingAction(null);
                return success("决斗结束，有人受伤");
            } else if ("NAN_MAN".equals(effectType)) {
                // 南蛮：没出杀受到伤害
                responder.takeDamage(1);
                state.addLog(responder.getUsername() + " 未出杀，受到南蛮入侵伤害");
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
     * 处理选择目标卡牌（过河拆桥/顺手牵羊）
     */
    private ActionResult handleChooseTargetCard(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer player = findPlayerById(state, userId);
        if (player == null) return failure("未找到玩家");

        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        String effectType = extra != null ? (String) extra.get("effectType") : null;

        GamePlayer target = findPlayerById(state, pending.getOptionalTargetIds().get(0));

        if ("GUO_HE".equals(effectType)) {
            // 过河拆桥：弃置目标一张牌
            // 从目标手牌或装备中找到该牌
            GameCard discardCard = findCard(target, cardId);
            if (discardCard != null) {
                // 如果是装备区的牌
                if (discardCard.getCardType().isEquipment()) {
                    target.unequip(discardCard.getCardType());
                } else {
                    target.removeHandCard(cardId);
                }
                state.discardCard(discardCard);
                state.addLog(player.getUsername() + " 弃置了" + target.getUsername() +
                        " 的 " + discardCard.getCardType().getDisplayName());
            }
            state.setPendingAction(null);
            return success("过河拆桥成功");
        } else if ("SHUN_SHOU".equals(effectType)) {
            // 顺手牵羊：获得目标一张牌
            GameCard stealCard = findCard(target, cardId);
            if (stealCard != null) {
                // 从目标处移除
                if (stealCard.getCardType().isEquipment()) {
                    target.unequip(stealCard.getCardType());
                } else {
                    target.removeHandCard(cardId);
                }
                // 加入获得者的手牌
                player.drawCards(Collections.singletonList(stealCard));
                state.addLog(player.getUsername() + " 获得了" + target.getUsername() +
                        " 的 " + stealCard.getCardType().getDisplayName());
            }
            state.setPendingAction(null);
            return success("顺手牵羊成功");
        }

        state.setPendingAction(null);
        return failure("未知效果");
    }

    /**
     * 处理五谷丰登选牌
     */
    private ActionResult handleChooseWuguCard(GameState state, Long userId, String cardId, GameAction pending) {
        GamePlayer player = findPlayerById(state, userId);
        if (player == null) return failure("未找到玩家");

        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) pending.getExtraData();
        @SuppressWarnings("unchecked")
        List<String> wuguCards = extra != null ? (List<String>) extra.get("wuguCards") : null;

        if (wuguCards != null && wuguCards.contains(cardId)) {
            // 从展示区移除并加入手牌
            // 注意：这些牌实际是从drawPile抽出来的，需要特殊处理
            // 简化处理：检查是否在pending的可选列表中
            if (pending.getOptionalCardIds().contains(cardId)) {
                player.drawCards(Collections.singletonList(findCardInDrawPile(state, cardId)));
                state.addLog(player.getUsername() + " 选择了" + cardId);
            }
            state.setPendingAction(null);
            return success("五谷丰登选牌成功");
        }

        return failure("无效的选牌");
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
        m.put("id", card.getId());
        m.put("type", card.getCardType().name());
        m.put("displayName", card.getCardType().getDisplayName());
        m.put("category", card.getCardType().getCategory());
        m.put("suit", card.getSuit().getSymbol());
        m.put("suitName", card.getSuit().name());
        m.put("number", card.getNumber());
        m.put("numberDisplay", card.getNumberDisplay());
        return m;
    }

    private List<Map<String, Object>> cardsToClientMap(List<GameCard> cards) {
        return cards.stream().map(this::cardToClientMap).toList();
    }

    private boolean isBlackCard(String cardId, GamePlayer player) {
        GameCard card = findCard(player, cardId);
        return card != null && card.isBlack();
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
     * 动作结果
     */
    public record ActionResult(boolean success, String message, GameAction pendingAction) {
    }
}