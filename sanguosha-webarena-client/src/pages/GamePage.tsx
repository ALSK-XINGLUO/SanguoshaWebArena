import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { send, on } from '../api/websocket';

interface Card {
  id: string;
  name: string;
  suit: string;
  number: number;
  type: string;
}

interface GameState {
  roomId: string;
  currentTurnUserId: number;
  phase: string;
  players: {
    userId: number;
    username: string;
    hp: number;
    maxHp: number;
    handCardCount: number;
    isAlive: boolean;
  }[];
  handCards: Card[];
  equipCards: Card[];
  discardCount: number;
}

export default function GamePage() {
  const navigate = useNavigate();
  const [gameState, setGameState] = useState<GameState | null>(null);
  const [selectedCard, setSelectedCard] = useState<string | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const logEndRef = useRef<HTMLDivElement>(null);

  const isMyTurn = gameState?.currentTurnUserId === gameState?.players.find((p) => p.userId === gameState?.currentTurnUserId)?.userId;

  useEffect(() => {
    const unsubGameUpdate = on('GAME_STATE', (_, data: GameState) => {
      setGameState(data);
    });

    const unsubGameLog = on('GAME_LOG', (_, data) => {
      setLogs((prev) => [...prev, data.message]);
    });

    const unsubGameEnd = on('GAME_END', (_, data) => {
      if (data.winner) {
        setLogs((prev) => [...prev, `🏆 游戏结束！${data.winner} 获胜！`]);
      } else {
        setLogs((prev) => [...prev, '游戏结束']);
      }
    });

    return () => {
      unsubGameUpdate();
      unsubGameLog();
      unsubGameEnd();
    };
  }, []);

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  const handleUseCard = (cardId: string) => {
    if (!isMyTurn) return;

    if (selectedCard === cardId) {
      setSelectedCard(null);
      return;
    }

    setSelectedCard(cardId);
  };

  const handleTargetPlayer = (userId: number) => {
    if (!selectedCard || !isMyTurn) return;

    send('USE_CARD', {
      cardId: selectedCard,
      targetUserId: userId,
    });

    setSelectedCard(null);
  };

  const handleEndTurn = () => {
    if (!isMyTurn) return;
    send('END_TURN');
    setSelectedCard(null);
  };

  const handleBackToLobby = () => {
    navigate('/lobby');
  };

  if (!gameState) {
    return (
      <div className="page game-page">
        <div className="loading-screen">
          <h2>等待游戏开始...</h2>
          <div className="spinner" />
        </div>
      </div>
    );
  }

  const me = gameState.players.find((p) => p.userId === gameState.players[0]?.userId);
  const opponent = gameState.players.find((p) => p.userId !== me?.userId);

  return (
    <div className="page game-page">
      <div className="game-header">
        <span className="game-phase">
          {gameState.phase === 'PLAYING' ? '游戏进行中' : gameState.phase === 'WAITING' ? '等待中' : gameState.phase}
        </span>
        <span className="game-turn">
          {isMyTurn ? '🎯 你的回合' : `${gameState.players.find((p) => p.userId === gameState.currentTurnUserId)?.username || '对手'}的回合`}
        </span>
        <button className="btn btn-sm" onClick={handleBackToLobby}>
          返回大厅
        </button>
      </div>

      <div className="game-board">
        {/* Opponent area */}
        {opponent && (
          <div className="player-area opponent-area">
            <div className="player-info">
              <div className="player-name">{opponent.username}</div>
              <div className="hp-bar">
                <div
                  className="hp-fill"
                  style={{ width: `${(opponent.hp / opponent.maxHp) * 100}%` }}
                />
                <span className="hp-text">{opponent.hp}/{opponent.maxHp}</span>
              </div>
              <div className="card-count">手牌: {opponent.handCardCount}</div>
            </div>
            <div className="player-cards-back">
              {Array.from({ length: opponent.handCardCount }).map((_, idx) => (
                <div key={idx} className="card-back" />
              ))}
            </div>
          </div>
        )}

        {/* Center battlefield */}
        <div className="battlefield">
          {selectedCard && (
            <div className="target-select">
              <p>选择目标:</p>
              {gameState.players
                .filter((p) => p.isAlive)
                .map((p) => (
                  <button
                    key={p.userId}
                    className="btn btn-sm"
                    onClick={() => handleTargetPlayer(p.userId)}
                  >
                    {p.username}
                  </button>
                ))}
            </div>
          )}
        </div>

        {/* Player area */}
        {me && (
          <div className="player-area my-area">
            <div className="player-info">
              <div className="player-name">{me.username}</div>
              <div className="hp-bar">
                <div
                  className="hp-fill"
                  style={{ width: `${(me.hp / me.maxHp) * 100}%` }}
                />
                <span className="hp-text">{me.hp}/{me.maxHp}</span>
              </div>
              <div className="card-count">手牌: {gameState.handCards.length}</div>
            </div>
            <div className="hand-cards">
              {gameState.handCards.map((card) => (
                <div
                  key={card.id}
                  className={`card ${selectedCard === card.id ? 'selected' : ''}`}
                  onClick={() => handleUseCard(card.id)}
                >
                  <div className="card-name">{card.name}</div>
                  <div className="card-suit">{card.suit}{card.number}</div>
                </div>
              ))}
            </div>
            <div className="game-actions">
              <button
                className="btn btn-primary"
                onClick={handleEndTurn}
                disabled={!isMyTurn}
              >
                结束回合
              </button>
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

      {/* Discard pile */}
      <div className="discard-pile">
        弃牌堆: {gameState.discardCount}张
      </div>
    </div>
  );
}