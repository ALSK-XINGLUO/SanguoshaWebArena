import { useEffect, useState, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { send, on } from '../api/websocket';

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

// Suit color helper
function isRedSuit(suitName: string): boolean {
  return suitName === 'HEART' || suitName === 'DIAMOND';
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

    // Request current game state (handles page refresh / delayed navigation)
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

  return (
    <div className="page game-page">
      <div className="game-header">
        <span className="game-phase">
          第{gs.turnNumber}回合 · {PHASE_LABELS[gs.phase] || gs.phase}
        </span>
        <span className="game-turn">
          {isMyTurn ? '🎯 你的回合' : `${currentPlayer?.username || '对手'}的回合`}
        </span>
        <button className="btn btn-sm" onClick={handleBackToLobby}>
          返回大厅
        </button>
      </div>

      <div className="game-board">
        {opponent && (
          <div className="player-area opponent-area">
            <div className="player-info">
              <div className="player-name">{opponent.username}</div>
              <div className="hp-bar">
                <div className="hp-fill" style={{ width: `${(opponent.currentHp / opponent.maxHp) * 100}%` }} />
                <span className="hp-text">{opponent.currentHp}/{opponent.maxHp}</span>
              </div>
              <div className="card-count">手牌: {opponent.handCardCount ?? opponent.handCards?.length ?? 0}</div>
            </div>
            {/* Equipment display */}
            <div className="equipment-area">
              {opponent.weapon && (
                <div className="equip-slot" title={opponent.weapon.displayName}>
                  <span className={`equip-suit ${isRedSuit(opponent.weapon.suitName) ? 'red' : ''}`}>{opponent.weapon.suit}</span>
                  <span className="equip-name">🪓 {opponent.weapon.displayName}</span>
                </div>
              )}
              {opponent.armor && (
                <div className="equip-slot" title={opponent.armor.displayName}>
                  <span className={`equip-suit ${isRedSuit(opponent.armor.suitName) ? 'red' : ''}`}>{opponent.armor.suit}</span>
                  <span className="equip-name">🛡️ {opponent.armor.displayName}</span>
                </div>
              )}
              {opponent.plusHorse && (
                <div className="equip-slot" title={opponent.plusHorse.displayName}>
                  <span className={`equip-suit ${isRedSuit(opponent.plusHorse.suitName) ? 'red' : ''}`}>{opponent.plusHorse.suit}</span>
                  <span className="equip-name">🐴 {opponent.plusHorse.displayName}</span>
                </div>
              )}
              {opponent.minusHorse && (
                <div className="equip-slot" title={opponent.minusHorse.displayName}>
                  <span className={`equip-suit ${isRedSuit(opponent.minusHorse.suitName) ? 'red' : ''}`}>{opponent.minusHorse.suit}</span>
                  <span className="equip-name">🐴 {opponent.minusHorse.displayName}</span>
                </div>
              )}
            </div>
            <div className="player-cards-back">
              {Array.from({ length: opponent.handCardCount ?? 0 }).map((_, idx) => (
                <div key={idx} className="card-back" />
              ))}
            </div>
          </div>
        )}

        <div className="battlefield">
          {selectedCard && !gameOver && (
            <div className="target-select">
              <p>选择目标:</p>
              {gs.players.filter((p) => p.alive).map((p) => (
                <button key={p.userId} className="btn btn-sm" onClick={() => handleTargetPlayer(p.userId)}>
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

        {me && (
          <div className="player-area my-area">
            <div className="player-info">
              <div className="player-name">{me.username}</div>
              <div className="hp-bar">
                <div className="hp-fill" style={{ width: `${(me.currentHp / me.maxHp) * 100}%` }} />
                <span className="hp-text">{me.currentHp}/{me.maxHp}</span>
              </div>
              <div className="card-count">手牌: {myHandCards.length}</div>
            </div>
            {/* Equipment display */}
            <div className="equipment-area">
              {me.weapon && (
                <div className="equip-slot" title={me.weapon.displayName}>
                  <span className={`equip-suit ${isRedSuit(me.weapon.suitName) ? 'red' : ''}`}>{me.weapon.suit}</span>
                  <span className="equip-name">🪓 {me.weapon.displayName}</span>
                </div>
              )}
              {me.armor && (
                <div className="equip-slot" title={me.armor.displayName}>
                  <span className={`equip-suit ${isRedSuit(me.armor.suitName) ? 'red' : ''}`}>{me.armor.suit}</span>
                  <span className="equip-name">🛡️ {me.armor.displayName}</span>
                </div>
              )}
              {me.plusHorse && (
                <div className="equip-slot" title={me.plusHorse.displayName}>
                  <span className={`equip-suit ${isRedSuit(me.plusHorse.suitName) ? 'red' : ''}`}>{me.plusHorse.suit}</span>
                  <span className="equip-name">🐴 {me.plusHorse.displayName}</span>
                </div>
              )}
              {me.minusHorse && (
                <div className="equip-slot" title={me.minusHorse.displayName}>
                  <span className={`equip-suit ${isRedSuit(me.minusHorse.suitName) ? 'red' : ''}`}>{me.minusHorse.suit}</span>
                  <span className="equip-name">🐴 {me.minusHorse.displayName}</span>
                </div>
              )}
            </div>
            {!gameOver && (
              <div className="hand-cards">
                {myHandCards.map((card) => (
                  <div
                    key={card.id}
                    className={`card ${selectedCard === card.id ? 'selected' : ''} ${isPlayPhase && isMyTurn ? 'clickable' : ''} ${isRedSuit(card.suitName) ? 'card-red' : 'card-black'}`}
                    onClick={() => handleUseCard(card.id)}
                  >
                    <div className="card-top-left">
                      <span className={`card-number ${isRedSuit(card.suitName) ? 'red' : ''}`}>{card.numberDisplay}</span>
                      <span className={`card-suit ${isRedSuit(card.suitName) ? 'red' : ''}`}>{card.suit}</span>
                    </div>
                    <div className="card-center-name">{card.displayName}</div>
                    <div className="card-bottom-right">
                      <span className={`card-suit ${isRedSuit(card.suitName) ? 'red' : ''}`}>{card.suit}</span>
                      <span className={`card-number ${isRedSuit(card.suitName) ? 'red' : ''}`}>{card.numberDisplay}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
            <div className="game-actions">
              {isPlayPhase && isMyTurn && (
                <button className="btn btn-primary" onClick={handleEndTurn} disabled={gameOver}>
                  结束出牌
                </button>
              )}
              {!isPlayPhase && (
                <button className="btn btn-secondary" onClick={handleEndTurn} disabled={!isMyTurn || gameOver}>
                  结束回合
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      <div className="game-log">
        <div className="log-messages">
          {logs.map((log, idx) => (
            <div key={idx} className="log-entry">{log}</div>
          ))}
          <div ref={logEndRef} />
        </div>
      </div>

      <div className="discard-pile">牌堆: {gs.drawPileCount}张 | 弃牌堆: {gs.discardPileCount}张</div>
    </div>
  );
}