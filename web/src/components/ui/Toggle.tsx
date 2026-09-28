interface ToggleProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  size?: 'md' | 'lg';
  label?: string;
}

export function Toggle({ checked, onChange, disabled, size = 'md', label }: ToggleProps) {
  const dims = size === 'lg' ? { track: 'h-8 w-14', knob: 'h-6 w-6', translate: 'translate-x-6' } : { track: 'h-6 w-11', knob: 'h-4 w-4', translate: 'translate-x-5' };
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex shrink-0 items-center rounded-full transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${dims.track} ${checked ? 'bg-primary' : 'bg-surface-hover'}`}
    >
      <span
        className={`inline-block transform rounded-full bg-white shadow transition-transform ${dims.knob} ${checked ? dims.translate : 'translate-x-1'}`}
      />
    </button>
  );
}
