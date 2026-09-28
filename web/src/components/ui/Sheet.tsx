import type { ReactNode } from 'react';
import { createPortal } from 'react-dom';

export function Sheet({ open, onClose, title, children }: { open: boolean; onClose: () => void; title?: string; children: ReactNode }) {
  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end bg-black/40" onClick={onClose}>
      <div
        className="pb-safe w-full max-h-[80vh] overflow-y-auto rounded-t-2xl bg-surface p-4 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mx-auto mb-3 h-1.5 w-10 rounded-full bg-surface-hover" />
        {title && <h2 className="mb-3 text-base font-semibold text-text">{title}</h2>}
        {children}
      </div>
    </div>,
    document.body,
  );
}
