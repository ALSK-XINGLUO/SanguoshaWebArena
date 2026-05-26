import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { connect, disconnect, on } from './api/websocket';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import LobbyPage from './pages/LobbyPage';
import RoomPage from './pages/RoomPage';
import GamePage from './pages/GamePage';
import './App.css';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  if (!isLoggedIn) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

function AppRoutes() {
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.token);
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);

  // Connect WebSocket globally when token is available
  useEffect(() => {
    if (!token) return;

    connect(token);

    const unsubReconnect = on('RECONNECT_ROOM', (_, data) => {
      if (data.status === 'PLAYING') {
        navigate(`/game/${data.roomId}`, { replace: true });
      } else {
        navigate(`/room/${data.roomId}`, { replace: true });
      }
    });

    return () => {
      unsubReconnect();
      disconnect();
    };
  }, [token, navigate]);

  return (
    <Routes>
      <Route
        path="/login"
        element={isLoggedIn ? <Navigate to="/lobby" replace /> : <LoginPage />}
      />
      <Route
        path="/register"
        element={isLoggedIn ? <Navigate to="/lobby" replace /> : <RegisterPage />}
      />
      <Route
        path="/lobby"
        element={
          <PrivateRoute>
            <LobbyPage />
          </PrivateRoute>
        }
      />
      <Route
        path="/room/:roomId"
        element={
          <PrivateRoute>
            <RoomPage />
          </PrivateRoute>
        }
      />
      <Route
        path="/game/:roomId"
        element={
          <PrivateRoute>
            <GamePage />
          </PrivateRoute>
        }
      />
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

function App() {
  const loadFromStorage = useAuthStore((s) => s.loadFromStorage);

  useEffect(() => {
    loadFromStorage();
  }, [loadFromStorage]);

  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}

export default App;