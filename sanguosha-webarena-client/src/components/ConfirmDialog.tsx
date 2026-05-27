import { create } from 'zustand';

interface ConfirmState {
  isOpen: boolean;
  message: string;
  title: string;
  resolve: ((value: boolean) => void) | null;
}

const useConfirmStore = create<ConfirmState>(() => ({
  isOpen: false,
  message: '',
  title: '确认',
  resolve: null,
}));

export function confirm(message: string, title = '确认'): Promise<boolean> {
  return new Promise((resolve) => {
    useConfirmStore.setState({ isOpen: true, message, title, resolve });
  });
}

function handleAnswer(answer: boolean) {
  const state = useConfirmStore.getState();
  state.resolve?.(answer);
  useConfirmStore.setState({ isOpen: false, resolve: null });
}

export function ConfirmDialog() {
  const isOpen = useConfirmStore((s) => s.isOpen);
  const message = useConfirmStore((s) => s.message);
  const title = useConfirmStore((s) => s.title);

  if (!isOpen) return null;

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 9999,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: 'rgba(0,0,0,0.6)',
      }}
      onClick={() => handleAnswer(false)}
    >
      <div
        style={{
          background: '#16213e',
          border: '1px solid #2a3a5a',
          borderRadius: 12,
          padding: '24px 28px',
          minWidth: 320,
          boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ fontSize: 18, fontWeight: 600, marginBottom: 12, color: '#eaeaea' }}>
          {title}
        </div>
        <div style={{ fontSize: 15, color: '#8892a4', marginBottom: 24 }}>
          {message}
        </div>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
          <button
            className="btn"
            onClick={() => handleAnswer(false)}
          >
            取消
          </button>
          <button
            className="btn btn-primary"
            onClick={() => handleAnswer(true)}
          >
            确定
          </button>
        </div>
      </div>
    </div>
  );
}
