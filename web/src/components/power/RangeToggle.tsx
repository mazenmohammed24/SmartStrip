import type { PowerRange } from '@shared/types';

const OPTIONS: { value: PowerRange; label: string }[] = [
  { value: '7d', label: '7 days' },
  { value: '30d', label: '30 days' },
  { value: '90d', label: '90 days' },
];

export function RangeToggle({ value, onChange }: { value: PowerRange; onChange: (r: PowerRange) => void }) {
  return (
    <div className="grid w-full grid-cols-3 rounded-xl border border-border bg-surface p-1 shadow-sm sm:w-auto">
      {OPTIONS.map((opt) => (
        <button
          key={opt.value}
          type="button"
          onClick={() => onChange(opt.value)}
          className={`rounded-lg px-4 py-2 text-sm font-semibold transition-colors ${value === opt.value ? 'bg-success-bg text-success shadow-sm' : 'text-text-muted hover:bg-surface-hover'
            }`}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}
