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
  pendingAction: any;
  log: string[];
}

const PHASE_LABELS: Record<string, string> = {
  PREPARE: '准备阶段',
  JUDGE: '判定阶段',
  DRAW: '摸牌阶段',
  PLAY: '出牌阶段',
  DISCARD: '弃牌阶段',
  END: '结束阶段',
};

const CATEGORY_COLORS: Record<string, string> = {
  BASIC: '#27ae60',
  EQUIPMENT: '#2980b9',
  STRATEGY: '#8e44ad',
  DELAYED_STRATEGY: '#d35400',
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

function isRedSuit(suitName: string): boolean {
  return suitName === 'HEART' || suitName === 'DIAMOND';
}

function getCardCatBg(category: string): string {
  switch (category) {
    case 'BASIC': return '#e8f5e9';
    case 'EQUIPMENT': return '#e3f2fd';
    case 'STRATEGY': return '#f3e5f5';
    case 'DELAYED_STRATEGY': return '#fff3e0';
    default: return '#f5f0e8';
  }
}

export default function GamePage() {
  const navigate = useNavigate();
  const { roomId } = useParams<{ roomId: string }>();
  const currentUser = useAuthStore((s) => s.user);
  const [gs, setGs] = useState<GameStateDTO | null>(null);
  const [gameId, setGameId] = useState<string>('');
  const [selectedCard, setSelectedCard] = useState<string | null>(null);
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

  const handleUseCard = (cardId: string) => {
    if (!isMyTurn || gameOver || !isPlayPhase) return;
    setSelectedCard((prev) => (prev === cardId ? null : cardId));
  };

  const handleTargetPlayer = (userId: number) => {
    if (!selectedCard || !isMyTurn || gameOver) return;
    send('PLAY_CARD', {
      gameId,
      cardId: selectedCard,
      targetUserId: String(userId),
    });
    setSelectedCard(null);
  };

  const handleEndTurn = () => {
    if (!isMyTurn || gameOver) return;
    send('END_TURN', { gameId });
    setSelectedCard(null);
  };

  const handleBackToLobby = () => {
    navigate('/lobby');
  };

  if (!gs) {
    return (
      <div className="page game-page">
        <div className="loading-screen">
          <h2>等待游戏开始...</h2>
          <div className="spinner" />
        </div>
      </div>
    );
  }

  const myHandCards: CardDTO[] = me?.handCards || [];

  const renderCard = (card: CardDTO, clickable: boolean) => {
    const suitSym = getSuitSymbol(card.suitName);
    const isRed = isRedSuit(card.suitName);
    const selected = selectedCard === card.id;
    const classes = [
      'playing-card',
      isRed ? 'red' : 'black',
      clickable ? 'clickable' : 'disabled',
      selected ? 'selected' : '',
    ].filter(Boolean).join(' ');

    return (
      <div
        key={card.id}
        className={classes}
        onClick={() => clickable && handleUseCard(card.id)}
        title={card.displayName}
      >
        {/* Top-left corner */}
        <div className="card-corner card-corner-top-left">
          <span className="card-corner-number">{card.numberDisplay}</span>
          <span className="card-corner-suit">{suitSym}</span>
        </div>
        {/* Center */}
        <div className="card-center">
          <span className="card-center-suit">{suitSym}</span>
          <span className="card-center-name">{card.displayName}</span>
        </div>
        {/* Bottom-right corner */}
        <div className="card-corner card-corner-bottom-right">
          <span className="card-corner-number">{card.numberDisplay}</span>
          <span className="card-corner-suit">{suitSym}</span>
        </div>
      </div>
    );
  };

  const renderEquipment = (label: string, card: CardDTO | null, icon: string) => {
    if (!card) return null;
    const isRed = isRedSuit(card.suitName);
    return (
      <div className="equip-slot" title={card.displayName}>
        <span className={`equip-suit ${isRed ? 'red' : 'black'}`}>{getSuitSymbol(card.suitName)}</span>
        <span className="equip-name">{icon} {card.displayName}</span>
      </div>
    );
  };

  return (
    <div className="page game-page">
      {/* Header */}
      <div className="game-header">
        <div className="game-header-left">
          <span className="game-phase">
            {PHASE_LABELS[gs.phase] || gs.phase}
          </span>
          <span className="game-turn">
            第{gs.turnNumber}回合 · {isMyTurn ? '🎯 你的回合' : `${currentPlayer?.username || '对手'}的回合`}
          </span>
        </div>
        <div className="game-header-right">
          <button className="btn btn-sm" onClick={handleBackToLobby}>
            返回大厅
          </button>
        </div>
      </div>

      <div className="game-board">
        {/* Opponent area */}
        {opponent && (
          <div className="player-area opponent-area">
            <div className="player-info-row">
              <span className="player-name opponent">{opponent.username}</span>
              <div className="hp-bar">
                <div className="hp-fill" style={{ width: `${(opponent.currentHp / opponent.maxHp) * 100}%` }} />
                <span className="hp-text">{opponent.currentHp}/{opponent.maxHp}</span>
              </div>
              <span className="card-count-badge">🃏 {opponent.handCardCount ?? opponent.handCards?.length ?? 0}</span>
            </div>
            {/* Equipment */}
            <div className="equipment-area">
              {renderEquipment('武器', opponent.weapon, '🪓')}
              {renderEquipment('防具', opponent.armor, '🛡️')}
              {renderEquipment('+1马', opponent.plusHorse, '🐴')}
              {renderEquipment('-1马', opponent.minusHorse, '🐴')}
            </div>
            {/* Card backs */}
            <div className="opponent-cards">
              {Array.from({ length: opponent.handCardCount ?? 0 }).map((_, idx) => (
                <div key={idx} className="card-back" />
              ))}
            </div>
          </div>
        )}

        {/* Battlefield center */}
        <div className="battlefield">
          {selectedCard && !gameOver && (
            <div className="target-select">
              <p>🎯 选择目标:</p>
              {gs.players.filter((p) => p.alive).map((p) => (
                <button
                  key={p.userId}
                  className="btn btn-sm btn-primary"
                  onClick={() => handleTargetPlayer(p.userId)}
                >
                  {p.username}
                </button>
              ))}
            </div>
          )}
          {gameOver && (
            <div className="game-over-msg">
              <h2>🎉 游戏结束</h2>
              <p>{gs.winnerName ? `${gs.winnerName} 获胜！` : '平局'}</p>
              <button className="btn btn-primary" onClick={handleBackToLobby}>
                返回大厅
              </button>
            </div>
          )}
        </div>

        {/* My area */}
        {me && (
          <div className="player-area my-area">
            <div className="player-info-row">
              <span className="player-name me">{me.username}</span>
              <div className="hp-bar">
                <div className="hp-fill" style={{ width: `${(me.currentHp / me.maxHp) * 100}%` }} />
                <span className="hp-text">{me.currentHp}/{me.maxHp}</span>
              </div>
              <span className="card-count-badge">🃏 {myHandCards.length}张</span>
            </div>
            {/* Equipment */}
            <div className="equipment-area">
              {renderEquipment('武器', me.weapon, '🪓')}
              {renderEquipment('防具', me.armor, '🛡️')}
              {renderEquipment('+1马', me.plusHorse, '🐴')}
              {renderEquipment('-1马', me.minusHorse, '🐴')}
            </div>
            {/* Hand cards */}
            {!gameOver && (
              <div className="hand-cards">
                {myHandCards.map((card) => renderCard(card, isPlayPhase && isMyTurn))}
                {myHandCards.length === 0 && (
                  <span style={{ color: 'var(--text-dim)', fontSize: 13, padding: 8 }}>手牌为空</span>
                )}
              </div>
            )}
            {/* Actions */}
            <div className="game-actions">
              {isPlayPhase && isMyTurn && (
                <button className="btn btn-primary" onClick={handleEndTurn} disabled={gameOver}>
                  结束出牌
                </button>
              )}
              {!isPlayPhase && isMyTurn && (
                <button className="btn btn-secondary" onClick={handleEndTurn} disabled={gameOver}>
                  结束回合
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Game log */}
      <div className="game-log">
        <div className="log-messages">
          {logs.map((log, idx) => (
            <div key={idx} className="log-entry">{log}</div>
          ))}
          <div ref={logEndRef} />
        </div>
      </div>

      {/* Footer */}
      <div className="game-footer">
        <span>牌堆: {gs.drawPileCount}张</span>
        <span>弃牌堆: {gs.discardPileCount}张</span>
      </div>
    </div>
  );
}