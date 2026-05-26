import { useEffect, useState, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { send, on } from '../api/websocket';
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

export default function GamePage() {
  const navigate = useNavigate();
  const { roomId } = useParams<{ roomId: string }>();
  const currentUser = useAuthStore((s) => s.user);
  const [gs, setGs] = useState<GameStateDTO | null>(null);
  const [gameId, setGameId] = useState<string>('');
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const [gameOver, setGameOver] = useState(false);
  const logEndRef = useRef<HTMLDivElement>(null);

  const me = gs?.players.find((p) => p.userId === currentUser?.userId);
  const opponent = gs?.players.find((p) => p.userId !== currentUser?.userId);
  const currentPlayer = gs ? gs.players[gs.currentTurnIndex] : null;
  const isMyTurn = me != null && currentPlayer?.userId === me.userId;
  const isPlayPhase = gs?.phase === 'PLAY';

  useEffect(() => {
    const unsubGameState = on('GAME_STATE', (_, data) => {
      if (data.gameState) {
        const dto = data.gameState as GameStateDTO;
        setGs(dto);
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
        setLogs((prev) => [...prev, `🏆 游戏结束！${data.winnerName} 获胜！`]);
      } else {
        setLogs((prev) => [...prev, '游戏结束']);
      }
    });

    const unsubError = on('ERROR', (_, data) => {
      alert(data.message);
    });

    send('FETCH_GAME_STATE', { roomId });

    return () => {
      unsubGameState();
      unsubGameStart();
      unsubGameOver();
      unsubError();
    };
  }, []);

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  const handlePlayCard = (cardId: string) => {
    if (!isMyTurn || gameOver || !isPlayPhase) return;
    setSelectedCardId((prev) => (prev === cardId ? null : cardId));
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

  const pendingAction = gs?.pendingAction;
  const isMyPendingAction = pendingAction && pendingAction.optionalTargetIds?.includes(currentUser?.userId ?? 0);

  const handleConfirmPending = (cardId: string | null) => {
    if (!pendingAction || !isMyPendingAction) return;
    send('PENDING_RESPONSE', { gameId, cardId });
    setSelectedCardId(null);
  };

  const handleSkipPending = () => {
    if (!pendingAction || !isMyPendingAction) return;
    send('PENDING_RESPONSE', { gameId, cardId: null });
    setSelectedCardId(null);
  };

  const handleSurrender = () => {
    if (gameOver || !gameId) return;
    if (window.confirm('确定要认输吗？')) {
      send('SURRENDER', { gameId });
    }
  };

  const handleBackToLobby = () => {
    navigate('/lobby');
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
            <div className="text-pending-hint">需弃 {pendingAction.discardCount} 张</div>
          )}
          {pendingAction.optionalCards && pendingAction.optionalCards.length > 0 && (
            <div className="text-pending-cards">
              {pendingAction.optionalCards.map((card) => (
                <button
                  key={card.id}
                  className={`text-card-btn ${isCardRed(card) ? 'red' : 'black'} ${selectedCardId === card.id ? 'sel' : ''}`}
                  onClick={() => setSelectedCardId(selectedCardId === card.id ? null : card.id)}
                >
                  {formatCard(card)}
                </button>
              ))}
            </div>
          )}
          {(!pendingAction.optionalCards || pendingAction.optionalCards.length === 0) && (
            <div className="text-pending-hint">没有可用卡牌</div>
          )}
          <div className="text-pending-actions">
            {pendingAction.optionalCards && pendingAction.optionalCards.length > 0 && (
              <button className="text-btn primary" onClick={() => handleConfirmPending(selectedCardId)} disabled={!selectedCardId}>
                确认
              </button>
            )}
            <button className="text-btn" onClick={handleSkipPending}>跳过</button>
          </div>
        </div>
      )}

      {/* Battlefield — target selection */}
      <div className="text-section">
        {selectedCardId && isPlayPhase && isMyTurn && !gameOver && !pendingAction && (
          <div className="text-target-select">
            选择目标:
            {gs.players.filter((p) => p.alive).map((p) => (
              <button key={p.userId} className="text-btn primary" onClick={() => handleTargetPlayer(p.userId)}>
                {p.username}
              </button>
            ))}
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

          {/* Hand cards as plain text buttons */}
          {!gameOver && (
            <div className="text-handcards">
              {myHandCards.length === 0 && <span className="text-dim">手牌为空</span>}
              {myHandCards.map((card) => (
                <button
                  key={card.id}
                  className={`text-card-btn ${isCardRed(card) ? 'red' : 'black'} ${selectedCardId === card.id ? 'sel' : ''}`}
                  onClick={() => isPlayPhase && isMyTurn && !pendingAction && handlePlayCard(card.id)}
                  disabled={!isPlayPhase || !isMyTurn || !!pendingAction}
                  title={card.displayName}
                >
                  [{formatCard(card)}]
                </button>
              ))}
            </div>
          )}

          {/* Action buttons */}
          {isPlayPhase && isMyTurn && !pendingAction && !gameOver && (
            <div className="text-actions">
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