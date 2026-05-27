import { useEffect, useState, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { send, on } from '../api/websocket';
import { showToast } from '../components/Toast';
import { confirm } from '../components/ConfirmDialog';
import './GamePage.css';

interface CardDTO {
  id: string;
  type: string;
  displayName: string;
  category: string;
  suit: string;
  suitName: string;
  number: number;
  numberDisplay: string;
}

interface PlayerDTO {
  userId: number;
  username: string;
  slotIndex: number;
  maxHp: number;
  currentHp: number;
  alive: boolean;
  handCards?: CardDTO[];
  handCardCount?: number;
  weapon: CardDTO | null;
  armor: CardDTO | null;
  plusHorse: CardDTO | null;
  minusHorse: CardDTO | null;
  judgeArea: CardDTO[];
}

interface GameStateDTO {
  gameId: string;
  roomId: string;
  currentTurnIndex: number;
  phase: string;
  turnNumber: number;
  started: boolean;
  finished: boolean;
  winnerId: number | null;
  winnerName: string | null;
  players: PlayerDTO[];
  drawPileCount: number;
  discardPileCount: number;
  pendingAction: PendingActionDTO | null;
  log: string[];
}

interface PendingActionDTO {
  actionId: string;
  actionType: string;
  sourceCardId: string | null;
  sourcePlayerId: number;
  optionalCardIds: string[];
  optionalCards: CardDTO[];
  optionalTargetIds: number[];
  message: string;
  discardCount: number;
  extraData: Record<string, any>;
}

const PHASE_LABELS: Record<string, string> = {
  PREPARE: '准备阶段',
  JUDGE: '判定阶段',
  DRAW: '摸牌阶段',
  PLAY: '出牌阶段',
  DISCARD: '弃牌阶段',
  END: '结束阶段',
};

function getSuitSymbol(suitName: string): string {
  switch (suitName) {
    case 'HEART': return '♥';
    case 'DIAMOND': return '♦';
    case 'SPADE': return '♠';
    case 'CLUB': return '♣';
    default: return suitName;
  }
}

function formatCard(card: CardDTO): string {
  const suit = getSuitSymbol(card.suitName);
  return `${suit}${card.numberDisplay} ${card.displayName}`;
}

/** 判断卡牌是否不能以自己为目标 */
function isOffensiveCardType(type: string): boolean {
  return ['SHA', 'JUE_DOU', 'GUO_HE', 'SHUN_SHOU'].includes(type);
}

export default function GamePage() {
  const navigate = useNavigate();
  const { roomId } = useParams<{ roomId: string }>();
  const currentUser = useAuthStore((s) => s.user);
  const [gs, setGs] = useState<GameStateDTO | null>(null);
  const [gameId, setGameId] = useState<string>('');
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [selectedDiscardCardIds, setSelectedDiscardCardIds] = useState<string[]>([]);
  const [logs, setLogs] = useState<string[]>([]);
  const [gameOver, setGameOver] = useState(false);
  const [wuguSubmitting, setWuguSubmitting] = useState(false);
  const [responseSubmitting, setResponseSubmitting] = useState(false);
  const [testChangeHandSubmitting, setTestChangeHandSubmitting] = useState(false);
  const [skillMode, setSkillMode] = useState<string | null>(null);
  const [selectedSkillCardIds, setSelectedSkillCardIds] = useState<string[]>([]);
  const [selectedTargetIds, setSelectedTargetIds] = useState<number[]>([]);
  const logEndRef = useRef<HTMLDivElement>(null);

  const me = gs?.players.find((p) => p.userId === currentUser?.userId);
  const opponent = gs?.players.find((p) => p.userId !== currentUser?.userId);
  const currentPlayer = gs ? gs.players[gs.currentTurnIndex] : null;
  const isMyTurn = me != null && currentPlayer?.userId === me.userId;
  const isPlayPhase = gs?.phase === 'PLAY';
  const hasZhangBa = me?.weapon?.type === 'ZHANG_BA';

  useEffect(() => {
    const unsubGameState = on('GAME_STATE', (_, data) => {
      // [DIAG] 前端 GAME_STATE 同步日志：跟踪 pendingAction 生命周期
      try {
        console.log(`[FE GAME_STATE] old pendingAction=${gs?.pendingAction?.actionType ?? 'null'} old actionId=${gs?.pendingAction?.actionId ?? 'null'}`);
        if (data.gameState && data.gameState.pendingAction) {
          console.log(`[FE GAME_STATE] new pendingAction=${data.gameState.pendingAction.actionType} new actionId=${data.gameState.pendingAction.actionId} targetUserId=${data.gameState.pendingAction.optionalTargetIds?.[0] ?? 'null'}`);
        } else {
          console.log(`[FE GAME_STATE] new pendingAction=null`);
        }
      } catch (e) { /* logging only */ }
      if (data.gameState) {
        const dto = data.gameState as GameStateDTO;
        setGs(dto);
        setWuguSubmitting(false);
        setResponseSubmitting(false);
        setTestChangeHandSubmitting(false);
        setSelectedCardId(null);
        setSelectedDiscardCardIds([]);
        setSkillMode(null);
        setSelectedSkillCardIds([]);
        setSelectedTargetIds([]);
        if (data.gameId) setGameId(data.gameId);
        if (dto.log) setLogs(dto.log);
        if (dto.finished) setGameOver(true);
      }
    });

    const unsubGameStart = on('GAME_START', (_, data) => {
      if (data.gameId) setGameId(data.gameId);
      if (data.gameState) {
        const dto = data.gameState as GameStateDTO;
        setGs(dto);
        if (dto.log) setLogs(dto.log);
      }
    });

    const unsubGameOver = on('GAME_OVER', (_, data) => {
      setGameOver(true);
      if (data.gameState) {
        const dto = data.gameState as GameStateDTO;
        setGs(dto);
        if (dto.log) setLogs(dto.log);
      }
      if (data.winnerName) {
        setLogs((prev) => prev.includes(data.winnerName) ? prev : [...prev, `🏆 游戏结束！${data.winnerName} 获胜！`]);
      } else {
        setLogs((prev) => prev.includes('游戏结束') ? prev : [...prev, '游戏结束']);
      }
    });

    const unsubError = on('ERROR', (_, data) => {
      showToast(data.message, 'error');
      setResponseSubmitting(false);
      setTestChangeHandSubmitting(false);
    });

    const unsubToast = on('TOAST', (_, data) => {
      showToast(data.message, data.type === 'error' ? 'error' : 'success', 3000);
    });

    send('FETCH_GAME_STATE', { roomId });

    return () => {
      unsubGameState();
      unsubGameStart();
      unsubGameOver();
      unsubError();
      unsubToast();
    };
  }, []);

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  const handlePlayCard = (cardId: string) => {
    if (!isMyTurn || gameOver || !isPlayPhase) return;
    setSelectedCardId((prev) => (prev === cardId ? null : cardId));
    setSelectedTargetIds([]);
  };

  const handleUseCard = () => {
    if (!selectedCardId || !isMyTurn || gameOver) return;
    send('PLAY_CARD', { gameId, cardId: selectedCardId });
    setSelectedCardId(null);
  };

  const handleTargetPlayer = (userId: number) => {
    if (!selectedCardId || !isMyTurn || gameOver) return;
    send('PLAY_CARD', {
      gameId,
      cardId: selectedCardId,
      targetUserId: String(userId),
    });
    setSelectedCardId(null);
  };

  const handleEndTurn = () => {
    if (!isMyTurn || gameOver) return;
    send('END_TURN', { gameId });
    setSelectedCardId(null);
  };

  const handleZhangBaActive = (userId: number) => {
    if (selectedSkillCardIds.length !== 2 || gameOver) return;
    send('USE_SKILL', {
      gameId,
      skillCode: 'ZHANG_BA_SHE_MAO',
      selectedCardIds: selectedSkillCardIds,
      targetUserId: String(userId),
    });
    setSkillMode(null);
    setSelectedSkillCardIds([]);
    setSelectedCardId(null);
  };

  const handleZhangBaResponse = () => {
    if (selectedSkillCardIds.length !== 2 || gameOver) return;
    send('USE_SKILL', {
      gameId,
      skillCode: 'ZHANG_BA_SHE_MAO',
      selectedCardIds: selectedSkillCardIds,
      isResponse: true,
      actionId: pendingAction?.actionId,
    });
    setSkillMode(null);
    setSelectedSkillCardIds([]);
    setSelectedCardId(null);
  };

  const pendingAction = gs?.pendingAction;
  const isMyPendingAction = pendingAction && pendingAction.optionalTargetIds?.includes(currentUser?.userId ?? 0);
  const isWuxieResponse = pendingAction?.actionType === 'WAIT_WUXIE_RESPONSE' && isMyPendingAction;

  const handleConfirmPending = (cardId: string | null) => {
    if (!pendingAction || !isMyPendingAction) return;
    if (pendingAction.actionType === 'CHOOSE_WUGU_CARD' && wuguSubmitting) return;
    if (responseSubmitting && pendingAction.actionType !== 'CHOOSE_WUGU_CARD') return;

    // HUO_GONG_DISCARD: 前端强校验花色匹配
    if (pendingAction.actionType === 'HUO_GONG_DISCARD' && cardId) {
      const revealedSuit = pendingAction.extraData?.revealedSuit;
      if (revealedSuit) {
        const selectedCard = pendingAction.optionalCards?.find(c => c.id === cardId);
        if (selectedCard && selectedCard.suitName !== revealedSuit) {
          showToast('花色不匹配，请选择同花色牌', 'error');
          return;
        }
      }
    }

    send('PENDING_RESPONSE', { gameId, cardId, actionId: pendingAction.actionId });
    setSelectedCardId(null);
    if (pendingAction.actionType === 'CHOOSE_WUGU_CARD') {
      setWuguSubmitting(true);
    } else {
      setResponseSubmitting(true);
    }
  };

  const handleSkipPending = () => {
    if (!pendingAction || !isMyPendingAction) return;
    if (responseSubmitting) return;
    send('PENDING_RESPONSE', { gameId, cardId: null, actionId: pendingAction.actionId });
    setSelectedCardId(null);
    setResponseSubmitting(true);
  };

  const handleSurrender = async () => {
    if (gameOver || !gameId) return;
    const ok = await confirm('确定要认输吗？', '认输');
    if (ok) {
      send('SURRENDER', { gameId });
    }
  };

  const handleBackToLobby = () => {
    navigate('/lobby');
  };

  const handleTestChangeHand = () => {
    if (gameOver || !gameId || testChangeHandSubmitting) return;
    setTestChangeHandSubmitting(true);
    send('TEST_CHANGE_HAND', { gameId, roomId });
  };

  if (!gs) {
    return (
      <div className="page game-page">
        <div className="loading-screen">
          <h2>等待游戏开始...</h2>
        </div>
      </div>
    );
  }

  const myHandCards: CardDTO[] = me?.handCards || [];

  // Build equip string
  function equipText(p: PlayerDTO): string {
    const parts: string[] = [];
    if (p.weapon) parts.push(`武器:${formatCard(p.weapon)}`);
    if (p.armor) parts.push(`防具:${formatCard(p.armor)}`);
    if (p.plusHorse) parts.push(`+1马:${formatCard(p.plusHorse)}`);
    if (p.minusHorse) parts.push(`-1马:${formatCard(p.minusHorse)}`);
    return parts.length ? ` [${parts.join(' ')}]` : '';
  }

  function judgeText(p: PlayerDTO): string {
    if (!p.judgeArea || p.judgeArea.length === 0) return '';
    return ` 判定区:[${p.judgeArea.map(formatCard).join(', ')}]`;
  }

  function isCardRed(card: CardDTO): boolean {
    return card.suitName === 'HEART' || card.suitName === 'DIAMOND';
  }

  return (
    <div className="text-game-page">
      {/* Header */}
      <div className="text-header">
        <span>[{PHASE_LABELS[gs.phase] || gs.phase}]</span>
        <span>第{gs.turnNumber}回合</span>
        <span>{isMyTurn ? '← 你的回合' : `${currentPlayer?.username || '对手'}的回合 →`}</span>
        {!gameOver && <button className="text-btn surrender" onClick={handleSurrender}>认输</button>}
        {!gameOver && !pendingAction && (
          <button className="text-btn debug" onClick={handleTestChangeHand} disabled={testChangeHandSubmitting}>{testChangeHandSubmitting ? '换牌中...' : '测试换牌'}</button>
        )}
        <button className="text-btn" onClick={handleBackToLobby}>返回大厅</button>
      </div>

      {/* Opponent */}
      {opponent && (
        <div className="text-section">
          <div className="text-player opponent">
            <span className="text-name">【对手】{opponent.username}</span>
            <span className="text-hp">HP: {opponent.currentHp}/{opponent.maxHp}</span>
            <span className="text-handcount">手牌: {opponent.handCardCount ?? opponent.handCards?.length ?? 0}张</span>
            <span className="text-equip">{equipText(opponent)}</span>
            <span className="text-judge">{judgeText(opponent)}</span>
          </div>
        </div>
      )}

      {/* Pending action — plain text */}
      {pendingAction && isMyPendingAction && (
        <div className="text-pending">
          <div className="text-pending-msg">⚠️ {pendingAction.message}</div>
          {pendingAction.actionType === 'DISCARD' && (
            <div className="text-pending-hint">
              请选择 {pendingAction.discardCount} 张牌弃置（已选 {selectedDiscardCardIds.length}/{pendingAction.discardCount} 张）
              {selectedDiscardCardIds.length === pendingAction.discardCount && (
                <button className="text-btn primary"
                  onClick={() => {
                    send('PENDING_RESPONSE', { gameId, cardIds: selectedDiscardCardIds, actionId: pendingAction.actionId });
                    setSelectedDiscardCardIds([]);
                    setResponseSubmitting(true);
                  }}
                  disabled={responseSubmitting}
                  style={{ marginLeft: 8 }}>
                  {responseSubmitting ? '处理中...' : '弃置所选牌'}
                </button>
              )}
            </div>
          )}
          {pendingAction.actionType === 'CHOOSE_WUGU_CARD' && (
            <div className="text-pending-hint">
              [调试] actionType={pendingAction.actionType} targetUserId={pendingAction.optionalTargetIds?.[0]} currentUserId={currentUser?.userId} cards={pendingAction.optionalCards?.length}
            </div>
          )}
          {pendingAction.optionalCards && pendingAction.optionalCards.length > 0 && pendingAction.actionType !== 'DISCARD' && pendingAction.actionType !== 'HUO_GONG_DISCARD' && (
            <div className="text-pending-cards">
              {pendingAction.optionalCards.map((card) => {
                const isDisabled = pendingAction.actionType === 'CHOOSE_WUGU_CARD' ? wuguSubmitting : responseSubmitting;
                return (
                  <button
                    key={card.id}
                    disabled={isDisabled}
                    className={`text-card-btn ${card.id === '__RANDOM_HAND__' ? '' : isCardRed(card) ? 'red' : 'black'} ${selectedCardId === card.id ? 'sel' : ''}`}
                    onClick={() => !isDisabled && setSelectedCardId(selectedCardId === card.id ? null : card.id)}
                  >
                    {card.id === '__RANDOM_HAND__' ? `[随机] ${card.displayName}` : formatCard(card)}
                  </button>
                );
              })}
            </div>
          )}
          {(!pendingAction.optionalCards || pendingAction.optionalCards.length === 0) && pendingAction.actionType !== 'WAIT_WUXIE_RESPONSE' && (
            <div className="text-pending-hint">没有可用卡牌</div>
          )}
          {/* WAIT_EQUIP_TRIGGER: 装备触发（麒麟弓选马/青龙偃月刀选杀/贯石斧确认/寒冰剑确认） */}
          {pendingAction.actionType === 'WAIT_EQUIP_TRIGGER' && (
            <div className="text-pending-actions">
              {pendingAction.optionalCards && pendingAction.optionalCards.length > 0 ? (
                <button className="text-btn primary" onClick={() => handleConfirmPending(selectedCardId)} disabled={!selectedCardId || responseSubmitting}>
                  发动
                </button>
              ) : (
                <button className="text-btn primary" onClick={() => handleConfirmPending('__CONFIRM__')} disabled={responseSubmitting}>
                  确认发动
                </button>
              )}
              <button className="text-btn" onClick={handleSkipPending} disabled={responseSubmitting}>不发动</button>
            </div>
          )}

          {/* WAIT_WUXIE_RESPONSE: 无懈可击响应 */}
          {pendingAction.actionType === 'WAIT_WUXIE_RESPONSE' && (
            <>
              <div className="text-pending-hint">选择手牌中的无懈可击，或点击跳过</div>
              <div className="text-pending-actions">
                <button className="text-btn primary" onClick={() => handleConfirmPending(selectedCardId)} disabled={!selectedCardId || responseSubmitting}>
                  使用
                </button>
                <button className="text-btn" onClick={handleSkipPending} disabled={responseSubmitting}>跳过</button>
              </div>
            </>
          )}

          {/* HUO_GONG_DISCARD: 火攻弃同花色牌 */}
          {pendingAction.actionType === 'HUO_GONG_DISCARD' && (
            <>
              <div className="text-pending-hint">
                {pendingAction.extraData?.revealedSuit
                  ? `请弃置一张 ${getSuitSymbol(pendingAction.extraData.revealedSuit)} 花色手牌，或跳过（放弃火攻）`
                  : pendingAction.message}
              </div>
              <div className="text-pending-cards">
                {pendingAction.optionalCards?.map((card) => {
                  const suitMatches = card.suitName === pendingAction.extraData?.revealedSuit;
                  return (
                    <button
                      key={card.id}
                      disabled={!suitMatches || responseSubmitting}
                      className={`text-card-btn ${isCardRed(card) ? 'red' : 'black'} ${selectedCardId === card.id ? 'sel' : ''}`}
                      onClick={() => setSelectedCardId(selectedCardId === card.id ? null : card.id)}
                      style={!suitMatches ? {opacity: 0.3, cursor: 'not-allowed'} : undefined}
                    >
                      {formatCard(card)}
                    </button>
                  );
                })}
              </div>
              <div className="text-pending-actions">
                <button className="text-btn primary"
                  onClick={() => handleConfirmPending(selectedCardId)}
                  disabled={!selectedCardId || responseSubmitting}>
                  {responseSubmitting ? '处理中...' : '确认'}
                </button>
                <button className="text-btn" onClick={handleSkipPending} disabled={responseSubmitting}>
                  放弃火攻
                </button>
              </div>
            </>
          )}

          {/* HAN_BING_CHOOSE: 寒冰剑弃牌选择 */}
          {pendingAction.actionType === 'HAN_BING_CHOOSE' && (
            <>
              <div className="text-pending-hint">
                {pendingAction.message}
                {pendingAction.discardCount > 0 && (
                  <span>（已选 {selectedDiscardCardIds.length}/{pendingAction.discardCount} 张）</span>
                )}
              </div>
              <div className="text-pending-cards">
                {pendingAction.optionalCards?.map((card) => (
                  <button
                    key={card.id}
                    className={`text-card-btn ${card.id === '__RANDOM_HAND__' ? '' : isCardRed(card) ? 'red' : 'black'} ${selectedDiscardCardIds.includes(card.id) ? 'discard-sel' : ''}`}
                    onClick={() => {
                      if (responseSubmitting) return;
                      setSelectedDiscardCardIds(prev =>
                        prev.includes(card.id)
                          ? prev.filter(id => id !== card.id)
                          : (prev.length < pendingAction.discardCount ? [...prev, card.id] : prev)
                      );
                    }}
                  >
                    {card.id === '__RANDOM_HAND__' ? `[随机] ${card.displayName}` : formatCard(card)}
                  </button>
                ))}
              </div>
              <div className="text-pending-actions">
                <button className="text-btn primary"
                  onClick={() => {
                    send('PENDING_RESPONSE', { gameId, cardIds: selectedDiscardCardIds, actionId: pendingAction.actionId });
                    setSelectedDiscardCardIds([]);
                    setResponseSubmitting(true);
                  }}
                  disabled={selectedDiscardCardIds.length === 0 || responseSubmitting}>
                  {responseSubmitting ? '处理中...' : '确认弃牌'}
                </button>
                <button className="text-btn" onClick={() => {
                  send('PENDING_RESPONSE', { gameId, cardIds: [], actionId: pendingAction.actionId });
                  setSelectedDiscardCardIds([]);
                  setResponseSubmitting(true);
                }} disabled={responseSubmitting}>
                  放弃寒冰剑
                </button>
              </div>
            </>
          )}

          {/* DYING_REQUIRE_TAO: 濒死救援 */}
          {pendingAction.actionType === 'DYING_REQUIRE_TAO' && (
            <>
              <div className="text-pending-hint">
                {pendingAction.optionalCards && pendingAction.optionalCards.length > 0
                  ? '选择一张救援卡牌，或点击跳过放弃救援'
                  : '没有可用救援卡牌'}
              </div>
              <div className="text-pending-actions">
                {pendingAction.optionalCards && pendingAction.optionalCards.length > 0 && (
                  <button className="text-btn primary" onClick={() => handleConfirmPending(selectedCardId)}
                    disabled={!selectedCardId || responseSubmitting}>
                    {responseSubmitting ? '处理中...' : '使用'}
                  </button>
                )}
                <button className="text-btn" onClick={handleSkipPending} disabled={responseSubmitting}>
                  {responseSubmitting ? '处理中...' : '跳过（放弃救援）'}
                </button>
              </div>
            </>
          )}

          {/* 通用按钮 */}
          {pendingAction.actionType !== 'WAIT_EQUIP_TRIGGER' && pendingAction.actionType !== 'WAIT_WUXIE_RESPONSE' && pendingAction.actionType !== 'DYING_REQUIRE_TAO' && pendingAction.actionType !== 'DISCARD' && pendingAction.actionType !== 'HUO_GONG_DISCARD' && pendingAction.actionType !== 'HAN_BING_CHOOSE' && (
            <div className="text-pending-actions">
              {pendingAction.optionalCards && pendingAction.optionalCards.length > 0 && (
                <button className="text-btn primary" onClick={() => handleConfirmPending(selectedCardId)}
                  disabled={!selectedCardId || (pendingAction.actionType === 'CHOOSE_WUGU_CARD' && wuguSubmitting) || responseSubmitting}>
                  {pendingAction.actionType === 'CHOOSE_WUGU_CARD' && wuguSubmitting ? '选择中...' : responseSubmitting ? '处理中...' : '确认'}
                </button>
              )}
              {pendingAction.actionType !== 'CHOOSE_WUGU_CARD' && pendingAction.actionType !== 'DISCARD' && (
                <button className="text-btn" onClick={handleSkipPending}
                  disabled={responseSubmitting}>
                  {responseSubmitting ? '处理中...' : '跳过'}
                </button>
              )}
            </div>
          )}

          {/* 丈八蛇矛选项（RESPOND_SHA 时可用） */}
          {pendingAction.actionType === 'RESPOND_SHA' && hasZhangBa && (me?.handCards?.length ?? 0) >= 2 && !skillMode && (
            <div className="text-skill-option">
              <button className="text-btn skill" onClick={() => {
                setSkillMode('ZHANG_BA_SHE_MAO');
                setSelectedSkillCardIds([]);
              }}>
                使用丈八蛇矛（两张手牌当杀打出）
              </button>
            </div>
          )}
          {pendingAction.actionType === 'RESPOND_SHA' && skillMode === 'ZHANG_BA_SHE_MAO' && (
            <div className="text-skill-option">
              <span>已选 {selectedSkillCardIds.length}/2 张手牌</span>
              {selectedSkillCardIds.length === 2 && (
                <button className="text-btn primary" onClick={handleZhangBaResponse}>
                  确认出杀
                </button>
              )}
              <button className="text-btn" onClick={() => { setSkillMode(null); setSelectedSkillCardIds([]); }}>
                取消
              </button>
            </div>
          )}
        </div>
      )}

      {/* 等待提示 — pending action 存在但不是你的回合 */}
      {pendingAction && !isMyPendingAction && pendingAction.actionType === 'CHOOSE_WUGU_CARD' && (
        <div className="text-pending">
          <div className="text-pending-msg">等待对手选择五谷丰登牌...</div>
        </div>
      )}
      {pendingAction && !isMyPendingAction && pendingAction.actionType === 'WAIT_EQUIP_TRIGGER' && (
        <div className="text-pending">
          <div className="text-pending-msg">等待对手选择是否发动装备效果...</div>
        </div>
      )}
      {pendingAction && !isMyPendingAction && pendingAction.actionType === 'WAIT_WUXIE_RESPONSE' && (
        <div className="text-pending">
          <div className="text-pending-msg">等待 {gs?.players.find(p => p.userId === pendingAction.optionalTargetIds?.[0])?.username ?? '对手'} 使用无懈可击...</div>
        </div>
      )}
      {pendingAction && !isMyPendingAction && pendingAction.actionType === 'DYING_REQUIRE_TAO' && (
        <div className="text-pending">
          <div className="text-pending-msg">等待 {gs?.players.find(p => p.userId === pendingAction.optionalTargetIds?.[0])?.username ?? '对手'} 救援濒死玩家...</div>
        </div>
      )}
      {pendingAction && !isMyPendingAction && pendingAction.actionType === 'HAN_BING_CHOOSE' && (
        <div className="text-pending">
          <div className="text-pending-msg">等待对手选择寒冰剑弃牌...</div>
        </div>
      )}
      {/* 通用等待提示 */}
      {pendingAction && !isMyPendingAction && !['CHOOSE_WUGU_CARD', 'WAIT_EQUIP_TRIGGER', 'WAIT_WUXIE_RESPONSE', 'DYING_REQUIRE_TAO', 'HAN_BING_CHOOSE'].includes(pendingAction.actionType) && (
        <div className="text-pending">
          <div className="text-pending-msg">等待对手操作...</div>
        </div>
      )}

      {/* Battlefield — card action */}
      <div className="text-section">
        {selectedCardId && isPlayPhase && isMyTurn && !gameOver && !pendingAction && (() => {
          const card = myHandCards.find(c => c.id === selectedCardId);
          const needsTarget = card && (card.category === '基本牌' ? card.type === 'SHA' :
            card.category === '锦囊牌' ? !['WU_ZHONG', 'TAO_YUAN'].includes(card.type) : false);
          const isOffensive = card && isOffensiveCardType(card.type);
          const isTieSuo = card?.type === 'TIE_SUO';
          if (isTieSuo) {
            const toggleTarget = (uid: number) => {
              setSelectedTargetIds(prev =>
                prev.includes(uid) ? prev.filter(id => id !== uid) : [...prev, uid]
              );
            };
            return (
              <div className="text-target-select">
                <span>选择目标（可选1-2个）:</span>
                {gs.players.filter(p => p.alive).map((p) => (
                  <button key={p.userId}
                    className={`text-btn ${selectedTargetIds.includes(p.userId) ? 'primary' : ''}`}
                    onClick={() => toggleTarget(p.userId)}>
                    {p.username}{selectedTargetIds.includes(p.userId) ? ' ✓' : ''}
                  </button>
                ))}
                {selectedTargetIds.length > 0 && (
                  <div className="text-dim">
                    已选择目标: {selectedTargetIds.map(id => gs.players.find(p => p.userId === id)?.username).join('、')}
                  </div>
                )}
                <div className="text-tiesuo-actions">
                  <button className="text-btn primary"
                    disabled={selectedTargetIds.length === 0}
                    onClick={() => {
                      send('PLAY_CARD', { gameId, cardId: selectedCardId, targetUserIds: selectedTargetIds.map(String) });
                      setSelectedCardId(null);
                      setSelectedTargetIds([]);
                    }}>确定使用</button>
                  <button className="text-btn" onClick={() => {
                    send('PLAY_CARD', { gameId, cardId: selectedCardId });
                    setSelectedCardId(null);
                    setSelectedTargetIds([]);
                  }}>重铸（弃牌摸一张）</button>
                  <button className="text-btn" onClick={() => { setSelectedCardId(null); setSelectedTargetIds([]); }}>取消</button>
                </div>
              </div>
            );
          }
          // 借刀杀人：双目标选择
          const isJieDao = card?.type === 'JIE_DAO';
          if (isJieDao) {
            return (
              <div className="text-target-select">
                <span>
                  {selectedTargetIds.length === 0
                    ? '选择借刀目标（有武器的敌人）：'
                    : selectedTargetIds.length === 1
                    ? '选择杀的目标（在借刀目标攻击范围内）：'
                    : '已选择两个目标'}
                </span>
                {gs.players.filter((p) => p.alive && (selectedTargetIds.length > 0 ? p.userId !== currentUser?.userId : true)).map((p) => {
                  const idx = selectedTargetIds.indexOf(p.userId);
                  const isSelected = idx >= 0;
                  // 已经选了借刀目标后，不能再选自己作为杀目标
                  const blocked = selectedTargetIds.length === 1 && p.userId === selectedTargetIds[0];
                  return (
                    <button key={p.userId}
                      className={`text-btn ${isSelected ? 'primary' : ''}`}
                      disabled={blocked || isSelected}
                      onClick={() => {
                        if (selectedTargetIds.length === 0 || (selectedTargetIds.length === 1 && p.userId !== selectedTargetIds[0])) {
                          setSelectedTargetIds(prev => [...prev, p.userId]);
                        }
                      }}>
                      {p.username}{isSelected ? `（第${idx + 1}目标）` : ''}
                    </button>
                  );
                })}
                {selectedTargetIds.length === 2 && (
                  <div className="text-dim">
                    借刀目标：{gs.players.find(p => p.userId === selectedTargetIds[0])?.username}，
                    杀目标：{gs.players.find(p => p.userId === selectedTargetIds[1])?.username}
                  </div>
                )}
                <div className="text-actions" style={{ marginTop: 6 }}>
                  <button className="text-btn primary"
                    disabled={selectedTargetIds.length < 2}
                    onClick={() => {
                      send('PLAY_CARD', { gameId, cardId: selectedCardId, targetUserIds: selectedTargetIds.map(String) });
                      setSelectedCardId(null);
                      setSelectedTargetIds([]);
                    }}>确定使用</button>
                  <button className="text-btn" onClick={() => { setSelectedCardId(null); setSelectedTargetIds([]); }}>取消</button>
                </div>
              </div>
            );
          }
          return needsTarget ? (
            <div className="text-target-select">
              选择目标:
              {gs.players.filter((p) => p.alive && (!isOffensive || p.userId !== currentUser?.userId)).map((p) => (
                <button key={p.userId} className="text-btn primary" onClick={() => handleTargetPlayer(p.userId)}>
                  {p.username}
                </button>
              ))}
              <button className="text-btn" onClick={() => setSelectedCardId(null)}>取消</button>
            </div>
          ) : (
            <div className="text-target-select">
              <span>使用 </span>
              <button className="text-btn primary" onClick={handleUseCard}>确定使用</button>
              <button className="text-btn" onClick={() => setSelectedCardId(null)}>取消</button>
            </div>
          );
        })()}

        {/* 丈八蛇矛 target selection */}
        {skillMode === 'ZHANG_BA_SHE_MAO' && selectedSkillCardIds.length === 2 && isPlayPhase && isMyTurn && !gameOver && !pendingAction && (
          <div className="text-target-select">
            选择目标（丈八蛇矛）:
            {gs.players.filter((p) => p.alive && p.userId !== currentUser?.userId).map((p) => (
              <button key={p.userId} className="text-btn primary" onClick={() => handleZhangBaActive(p.userId)}>
                {p.username}
              </button>
            ))}
            <button className="text-btn" onClick={() => { setSkillMode(null); setSelectedSkillCardIds([]); }}>取消</button>
          </div>
        )}

        {gameOver && (
          <div className="text-gameover">
            游戏结束！{gs.winnerName ? `${gs.winnerName} 获胜！` : '平局'}
            <button className="text-btn primary" onClick={handleBackToLobby}>返回大厅</button>
          </div>
        )}
      </div>

      {/* My area */}
      {me && (
        <div className="text-section">
          <div className="text-player me">
            <span className="text-name">【我】{me.username}</span>
            <span className="text-hp">HP: {me.currentHp}/{me.maxHp}</span>
            <span className="text-handcount">手牌: {myHandCards.length}张</span>
            <span className="text-equip">{equipText(me)}</span>
            <span className="text-judge">{judgeText(me)}</span>
          </div>

          {/* Skill mode banner */}
          {skillMode === 'ZHANG_BA_SHE_MAO' && (
            <div className="text-skill-banner">
              丈八蛇矛：选择两张手牌当杀使用
              （已选 {selectedSkillCardIds.length}/2 张）
              <button className="text-btn" onClick={() => { setSkillMode(null); setSelectedSkillCardIds([]); }}>取消</button>
            </div>
          )}

          {/* Hand cards as plain text buttons */}
          {!gameOver && (
            <div className="text-handcards">
              {myHandCards.length === 0 && <span className="text-dim">手牌为空</span>}
              {myHandCards.map((card) => {
                const isSkillSelected = selectedSkillCardIds.includes(card.id);
                return (
                  <button
                    key={card.id}
                    className={`text-card-btn ${isCardRed(card) ? 'red' : 'black'} ${selectedCardId === card.id ? 'sel' : ''} ${isSkillSelected ? 'skill-sel' : ''} ${selectedDiscardCardIds.includes(card.id) ? 'discard-sel' : ''}`}
                    onClick={() => {
                      if (skillMode === 'ZHANG_BA_SHE_MAO') {
                        if (isSkillSelected) {
                          setSelectedSkillCardIds(prev => prev.filter(id => id !== card.id));
                        } else if (selectedSkillCardIds.length < 2) {
                          setSelectedSkillCardIds(prev => [...prev, card.id]);
                        }
                      } else if (pendingAction?.actionType === 'DISCARD' && isMyPendingAction) {
                        const isDiscardSelected = selectedDiscardCardIds.includes(card.id);
                        if (isDiscardSelected) {
                          setSelectedDiscardCardIds(prev => prev.filter(id => id !== card.id));
                        } else if (selectedDiscardCardIds.length < (pendingAction.discardCount ?? 1)) {
                          setSelectedDiscardCardIds(prev => [...prev, card.id]);
                        }
                      } else if (isWuxieResponse && card.type === 'WU_XIE') {
                        setSelectedCardId(prev => (prev === card.id ? null : card.id));
                      } else if (isPlayPhase && isMyTurn && !pendingAction) {
                        handlePlayCard(card.id);
                      }
                    }}
                    disabled={
                      skillMode ? false :
                      pendingAction?.actionType === 'DISCARD' ? false :
                      isWuxieResponse ? (card.type !== 'WU_XIE') :
                      (!isPlayPhase || !isMyTurn || !!pendingAction)
                    }
                    title={card.displayName}
                  >
                    [{isSkillSelected ? '✓ ' : ''}{selectedDiscardCardIds.includes(card.id) ? '✓ ' : ''}{formatCard(card)}]
                  </button>
                );
              })}
            </div>
          )}

          {/* Action buttons */}
          {isPlayPhase && isMyTurn && !pendingAction && !gameOver && (
            <div className="text-actions">
              {hasZhangBa && myHandCards.length >= 2 && !skillMode && (
                <button className="text-btn skill" onClick={() => {
                  setSkillMode('ZHANG_BA_SHE_MAO');
                  setSelectedSkillCardIds([]);
                }}>
                  丈八蛇矛
                </button>
              )}
              {skillMode === 'ZHANG_BA_SHE_MAO' && selectedSkillCardIds.length === 2 && !pendingAction && (
                <span className="text-dim">请选择目标</span>
              )}
              <button className="text-btn primary" onClick={handleEndTurn}>结束出牌</button>
            </div>
          )}
          {!isPlayPhase && isMyTurn && !pendingAction && !gameOver && (
            <div className="text-actions">
              <button className="text-btn" onClick={handleEndTurn}>结束回合</button>
            </div>
          )}
        </div>
      )}

      {/* Game log */}
      <div className="text-log">
        <div className="text-log-title">=== 游戏日志 ===</div>
        <div className="text-log-entries">
          {logs.map((log, idx) => (
            <div key={idx} className="text-log-entry">{log}</div>
          ))}
          <div ref={logEndRef} />
        </div>
      </div>

      {/* Footer */}
      <div className="text-footer">
        <span>牌堆: {gs.drawPileCount}张</span>
        <span>弃牌堆: {gs.discardPileCount}张</span>
      </div>
    </div>
  );
}