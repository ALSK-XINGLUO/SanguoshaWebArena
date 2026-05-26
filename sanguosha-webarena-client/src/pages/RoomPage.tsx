import { useEffect, useState, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { send, on } from '../api/websocket';

interface PlayerSlot {
  userId: number;
  username: string;
  ready: boolean;
  slotIndex: number;
}

interface RoomDetail {
  id: string;
  name: string;
  ownerName: string;
  ownerId: number;
  hasPassword: boolean;
  playerCount: number;
  maxPlayers: number;
  status: string;
  players: PlayerSlot[];
}

export default function RoomPage() {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();
  useAuthStore();
  const [room, setRoom] = useState<RoomDetail | null>(null);
  const [messages, setMessages] = useState<{ senderName: string; content: string; timestamp: number }[]>([]);
  const [chatInput, setChatInput] = useState('');
  const [isReady, setIsReady] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!roomId) return;

    const unsubRoomUpdate = on('ROOM_UPDATE', (_, data) => {
      setRoom(data);
    });

    const unsubLeave = on('LEAVE_ROOM', () => {
      navigate('/lobby');
    });

    const unsubChat = on('CHAT', (_, data) => {
      setMessages((prev) => [...prev, data]);
    });

    const unsubError = on('ERROR', (_, data) => {
      alert(data.message);
    });

    const unsubGameStart = on('GAME_START', () => {
      navigate(`/game/${roomId}`);
    });

    // Ask server for current room state by sending JOIN_ROOM (if not already in it)
    // The server will respond with ROOM_UPDATE
    send('JOIN_ROOM', { roomId });

    return () => {
      unsubRoomUpdate();
      unsubLeave();
      unsubChat();
      unsubError();
      unsubGameStart();
    };
  }, [roomId, navigate]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleReady = () => {
    const newReady = !isReady;
    setIsReady(newReady);
    send('PLAYER_READY', { ready: newReady });
  };

  const handleLeave = () => {
    send('LEAVE_ROOM');
    navigate('/lobby');
  };

  const handleChat = (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatInput.trim()) return;
    send('CHAT', { content: chatInput });
    setChatInput('');
  };

  if (!room) {
    return (
      <div className="page room-page">
        <p>加载中...</p>
      </div>
    );
  }

  return (
    <div className="page room-page">
      <div className="room-header">
        <h2>{room.name}</h2>
        <span className="room-id">ID: {room.id}</span>
        <button className="btn btn-sm" onClick={handleLeave}>
          离开房间
        </button>
      </div>

      <div className="room-content">
        <div className="players-section">
          <h3>玩家列表 ({room.playerCount}/{room.maxPlayers})</h3>
          <div className="player-slots">
            {Array.from({ length: room.maxPlayers }).map((_, idx) => {
              const player = room.players.find((p) => p.slotIndex === idx);
              return (
                <div key={idx} className={`player-slot ${player ? 'occupied' : ''}`}>
                  {player ? (
                    <>
                      <div className="player-avatar">{player.username.charAt(0).toUpperCase()}</div>
                      <div className="player-name">
                        {player.username}
                        {player.userId === room.ownerId && <span className="owner-badge">房主</span>}
                      </div>
                      <div className={`player-ready ${player.ready ? 'ready' : 'not-ready'}`}>
                        {player.ready ? '已准备' : '未准备'}
                      </div>
                    </>
                  ) : (
                    <div className="empty-slot">等待加入</div>
                  )}
                </div>
              );
            })}
          </div>
          <button
            className={`btn ${isReady ? 'btn-cancel' : 'btn-primary'}`}
            onClick={handleReady}
          >
            {isReady ? '取消准备' : '准备'}
          </button>
        </div>

        <div className="chat-section">
          <h3>房间聊天</h3>
          <div className="chat-messages">
            {messages.map((msg, idx) => (
              <div key={idx} className="chat-msg">
                <span className="chat-sender">{msg.senderName}: </span>
                <span className="chat-content">{msg.content}</span>
              </div>
            ))}
            <div ref={chatEndRef} />
          </div>
          <form className="chat-input" onSubmit={handleChat}>
            <input
              type="text"
              value={chatInput}
              onChange={(e) => setChatInput(e.target.value)}
              placeholder="输入聊天内容..."
              maxLength={200}
            />
            <button type="submit" className="btn btn-sm btn-primary">
              发送
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}