import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { connect, disconnect, send, on } from '../api/websocket';

interface RoomBrief {
  id: string;
  name: string;
  ownerName: string;
  hasPassword: boolean;
  playerCount: number;
  maxPlayers: number;
  status: string;
}

export default function LobbyPage() {
  const navigate = useNavigate();
  const { user, token, logout } = useAuthStore();
  const [rooms, setRooms] = useState<RoomBrief[]>([]);
  const [showCreate, setShowCreate] = useState(false);
  const [roomName, setRoomName] = useState('');
  const [roomPwd, setRoomPwd] = useState('');
  const [joinPwd, setJoinPwd] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!token) return;

    connect(token);

    const unsubRoomList = on('ROOM_LIST', (_, data) => {
      setRooms(data.rooms || []);
    });
    const unsubCreate = on('CREATE_ROOM', (_, data) => {
      navigate(`/room/${data.id}`);
    });
    const unsubRoomUpdate = on('ROOM_UPDATE', (_, data) => {
      // update room list if this user's room changed
      setRooms((prev) =>
        prev.map((r) => (r.id === data.id ? { ...r, playerCount: data.playerCount, status: data.status } : r))
      );
    });

    send('ROOM_LIST');

    return () => {
      unsubRoomList();
      unsubCreate();
      unsubRoomUpdate();
    };
  }, [token, navigate]);

  const handleLogout = () => {
    disconnect();
    logout();
    navigate('/login');
  };

  const handleCreateRoom = useCallback(() => {
    send('CREATE_ROOM', {
      name: roomName || undefined,
      password: roomPwd || undefined,
    });
    setShowCreate(false);
    setRoomName('');
    setRoomPwd('');
  }, [roomName, roomPwd]);

  const handleJoinRoom = useCallback(
    (roomId: string, hasPassword: boolean) => {
      if (hasPassword && !joinPwd[roomId]) {
        return;
      }
      send('JOIN_ROOM', {
        roomId,
        password: joinPwd[roomId] || undefined,
      });
    },
    [joinPwd]
  );

  const handleJoinPwdChange = (roomId: string, value: string) => {
    setJoinPwd((prev) => ({ ...prev, [roomId]: value }));
  };

  const handleRefreshList = () => {
    send('ROOM_LIST');
  };

  return (
    <div className="page lobby-page">
      <div className="lobby-header">
        <h1>三国杀·竞技</h1>
        <div className="user-info">
          <span>欢迎, {user?.nickname || user?.username}</span>
          <span className="level">Lv.{user?.level || 1}</span>
          <span className="record">
            {user?.winCount || 0}胜/{user?.loseCount || 0}负
          </span>
          <button className="btn btn-sm" onClick={handleLogout}>
            退出
          </button>
        </div>
      </div>

      <div className="lobby-actions">
        <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
          + 创建房间
        </button>
        <button className="btn" onClick={handleRefreshList}>
          刷新列表
        </button>
      </div>

      {showCreate && (
        <div className="create-room card">
          <h3>创建房间</h3>
          <div className="field">
            <label>房间名称</label>
            <input
              type="text"
              value={roomName}
              onChange={(e) => setRoomName(e.target.value)}
              placeholder="默认: xxx的房间"
            />
          </div>
          <div className="field">
            <label>密码（选填）</label>
            <input
              type="password"
              value={roomPwd}
              onChange={(e) => setRoomPwd(e.target.value)}
              placeholder="留空为公开房"
            />
          </div>
          <div className="form-actions">
            <button className="btn btn-primary" onClick={handleCreateRoom}>
              创建
            </button>
            <button className="btn" onClick={() => setShowCreate(false)}>
              取消
            </button>
          </div>
        </div>
      )}

      <div className="room-list">
        {rooms.length === 0 && <p className="empty">暂无可用房间，创建一个吧！</p>}
        {rooms.map((room) => (
          <div key={room.id} className="room-card card">
            <div className="room-info">
              <span className="room-name">{room.name}</span>
              <span className="room-owner">房主: {room.ownerName}</span>
              <span className="room-players">
                {room.playerCount}/{room.maxPlayers}人
              </span>
              {room.hasPassword && <span className="room-locked">🔒</span>}
            </div>
            <div className="room-join">
              {room.hasPassword && (
                <input
                  type="password"
                  placeholder="输入密码"
                  value={joinPwd[room.id] || ''}
                  onChange={(e) => handleJoinPwdChange(room.id, e.target.value)}
                  className="pwd-input"
                />
              )}
              <button
                className="btn btn-primary btn-sm"
                onClick={() => handleJoinRoom(room.id, room.hasPassword)}
                disabled={room.playerCount >= room.maxPlayers}
              >
                {room.playerCount >= room.maxPlayers ? '已满' : '加入'}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}