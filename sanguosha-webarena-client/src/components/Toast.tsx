import { useEffect } from 'react';
import { create } from 'zustand';

interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
  duration: number;
}

interface ToastState {
  toasts: Toast[];
  addToast: (message: string, type: Toast['type'], duration?: number) => void;
  removeToast: (id: number) => void;
}

let nextId = 0;

const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  addToast: (message, type, duration) => {
    const id = nextId++;
    set((s) => ({ toasts: [...s.toasts, { id, message, type, duration: duration ?? 3000 }] }));
    const ms = duration ?? (type === 'error' ? 5000 : 3000);
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
    }, ms);
  },
  removeToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

export function showToast(message: string, type: Toast['type'] = 'info', duration?: number) {
  useToastStore.getState().addToast(message, type, duration);
}

function ToastItem({ toast, onRemove }: { toast: Toast; onRemove: (id: number) => void }) {
  useEffect(() => {
    const timer = setTimeout(() => onRemove(toast.id), toast.duration);
    return () => clearTimeout(timer);
  }, [toast.id, toast.duration, onRemove]);

  const bgColor =
    toast.type === 'success' ? 'rgba(39,174,96,0.9)' :
    toast.type === 'error' ? 'rgba(231,76,60,0.9)' :
    'rgba(22,33,62,0.95)';

  const borderColor =
    toast.type === 'success' ? '#27ae60' :
    toast.type === 'error' ? '#e74c3c' :
    '#2a3a5a';

  return (
    <div
      style={{
        background: bgColor,
        border: `1px solid ${borderColor}`,
        color: toast.type === 'info' ? '#eaeaea' : '#fff',
        padding: '10px 18px',
        borderRadius: 8,
        fontSize: 14,
        boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
        pointerEvents: 'auto',
        cursor: 'pointer',
        animation: 'toastIn 0.25s ease-out',
      }}
      onClick={() => onRemove(toast.id)}
    >
      {toast.message}
    </div>
  );
}

export function ToastContainer() {
  const toasts = useToastStore((s) => s.toasts);
  const removeToast = useToastStore((s) => s.removeToast);

  if (toasts.length === 0) return null;

  return (
    <div
      style={{
        position: 'fixed',
        top: 16,
        right: 16,
        zIndex: 10000,
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        pointerEvents: 'none',
      }}
    >
      <style>{`
        @keyframes toastIn {
          from { opacity: 0; transform: translateX(40px); }
          to { opacity: 1; transform: translateX(0); }
        }
      `}</style>
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} onRemove={removeToast} />
      ))}
    </div>
  );
}
