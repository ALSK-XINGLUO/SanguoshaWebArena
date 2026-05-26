const WS_URL = 'ws://localhost:8080/ws/game';

export type MessageHandler = (type: string, data: any) => void;

let ws: WebSocket | null = null;
let handlers = new Map<string, MessageHandler[]>();
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;

export function connect(token: string) {
  if (ws && ws.readyState === WebSocket.OPEN) return;

  ws = new WebSocket(`${WS_URL}?token=${token}`);

  ws.onopen = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    // heartbeat every 30s
    if (heartbeatTimer) clearInterval(heartbeatTimer);
    heartbeatTimer = setInterval(() => {
      if (ws?.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'PING' }));
      }
    }, 30000);
  };

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data);
      const { type, data } = msg;
      const typeHandlers = handlers.get(type) || [];
      typeHandlers.forEach((fn) => fn(type, data));
      // also notify wildcard handlers
      const wildcardHandlers = handlers.get('*') || [];
      wildcardHandlers.forEach((fn) => fn(type, data));
    } catch (e) {
      console.error('Failed to parse WS message:', e);
    }
  };

  ws.onclose = () => {
    if (heartbeatTimer) clearInterval(heartbeatTimer);
    // auto reconnect after 3s
    if (token) {
      reconnectTimer = setTimeout(() => connect(token), 3000);
    }
  };

  ws.onerror = () => {
    ws?.close();
  };
}

export function disconnect() {
  if (heartbeatTimer) clearInterval(heartbeatTimer);
  if (reconnectTimer) clearTimeout(reconnectTimer);
  if (ws) {
    ws.onclose = null;
    ws.close();
    ws = null;
  }
  handlers.clear();
}

export function send(type: string, data?: any) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type, data: data || {} }));
  }
}

export function on(type: string, handler: MessageHandler) {
  if (!handlers.has(type)) {
    handlers.set(type, []);
  }
  handlers.get(type)!.push(handler);
  return () => {
    const list = handlers.get(type);
    if (list) {
      const idx = list.indexOf(handler);
      if (idx > -1) list.splice(idx, 1);
    }
  };
}