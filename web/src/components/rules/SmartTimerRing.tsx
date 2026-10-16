import { useRef } from 'react';

const SIZE = 320;
const C = SIZE / 2;
const R_OUT = 126;
const R_IN = 84;
const R_LABEL = 144;

function point(r: number, slot: number) {
  const t = (slot / 96) * Math.PI * 2;
  return [C + r * Math.sin(t), C - r * Math.cos(t)] as const;
}

function arcPath(slot: number) {
  const [x0, y0] = point(R_OUT, slot);
  const [x1, y1] = point(R_OUT, slot + 1);
  const [x2, y2] = point(R_IN, slot + 1);
  const [x3, y3] = point(R_IN, slot);
  return `M${x0} ${y0}A${R_OUT} ${R_OUT} 0 0 1 ${x1} ${y1}L${x2} ${y2}A${R_IN} ${R_IN} 0 0 0 ${x3} ${y3}Z`;
}

const PATHS = Array.from({ length: 96 }, (_, i) => arcPath(i));

export function SmartTimerRing({ value, onChange }: { value: string; onChange: (next: string) => void }) {
  const ref = useRef<SVGSVGElement>(null);
  const drag = useRef<{ mode: '0' | '1'; last: number; cells: string[] } | null>(null);

  const slotAt = (e: React.PointerEvent): number | null => {
    const rect = ref.current?.getBoundingClientRect();
    if (!rect) return null;
    const scale = SIZE / rect.width;
    const dx = (e.clientX - rect.left) * scale - C;
    const dy = (e.clientY - rect.top) * scale - C;
    const dist = Math.hypot(dx, dy);
    if (dist < R_IN - 18 || dist > R_OUT + 24) return null;
    const angle = (Math.atan2(dx, -dy) + Math.PI * 2) % (Math.PI * 2);
    return Math.min(95, Math.floor((angle / (Math.PI * 2)) * 96));
  };

  const paint = (slot: number) => {
    const d = drag.current;
    if (!d) return;
    const forward = (slot - d.last + 96) % 96;
    const backward = (d.last - slot + 96) % 96;
    const step = forward <= backward ? 1 : -1;
    const count = Math.min(forward, backward);
    for (let i = 0; i <= count; i++) d.cells[(d.last + step * i + 96) % 96] = d.mode;
    d.last = slot;
    onChange(d.cells.join(''));
  };

  const onDown = (e: React.PointerEvent) => {
    const slot = slotAt(e);
    if (slot === null) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    drag.current = { mode: value[slot] === '1' ? '0' : '1', last: slot, cells: value.split('') };
    paint(slot);
  };

  const onMove = (e: React.PointerEvent) => {
    if (!drag.current) return;
    const slot = slotAt(e);
    if (slot !== null) paint(slot);
  };

  const hoursOn = (value.split('1').length - 1) / 4;
  const h = Math.floor(hoursOn);
  const m = Math.round((hoursOn - h) * 60);

  return (
    <svg
      ref={ref}
      viewBox={`0 0 ${SIZE} ${SIZE}`}
      className="mx-auto aspect-square w-full max-w-[340px] touch-none select-none"
      onPointerDown={onDown}
      onPointerMove={onMove}
      onPointerUp={() => (drag.current = null)}
      onPointerCancel={() => (drag.current = null)}
      role="img"
      aria-label="24 hour smart timer"
    >
      {PATHS.map((d, i) => (
        <path key={i} d={d} className={value[i] === '1' ? 'fill-primary' : 'fill-surface-hover'} stroke="var(--color-surface)" strokeWidth={1} />
      ))}
      {[0, 3, 6, 9, 12, 15, 18, 21].map((hour) => {
        const [x, y] = point(R_LABEL, hour * 4);
        return (
          <text key={hour} x={x} y={y} textAnchor="middle" dominantBaseline="central" className="fill-text-muted font-mono text-[11px]">
            {String(hour).padStart(2, '0')}
          </text>
        );
      })}
      <text x={C} y={C - 8} textAnchor="middle" className="fill-text font-mono text-[28px] font-semibold">
        {h}h{m ? ` ${m}m` : ''}
      </text>
      <text x={C} y={C + 18} textAnchor="middle" className="fill-text-muted text-[12px]">
        on per day
      </text>
    </svg>
  );
}

export const PRESETS: Array<{ label: string; from: number; to: number }> = [
  { label: 'Evening', from: 72, to: 92 },
  { label: 'Night', from: 88, to: 28 },
  { label: 'Work hours', from: 32, to: 68 },
  { label: 'All day', from: 0, to: 96 },
];

export function presetSlots({ from, to }: { from: number; to: number }): string {
  if (to - from >= 96) return '1'.repeat(96);
  const cells = Array<string>(96).fill('0');
  for (let i = from; i % 96 !== to % 96; i++) cells[i % 96] = '1';
  return cells.join('');
}
