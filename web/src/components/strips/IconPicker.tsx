import type { ApplianceProfileName } from '@shared/types';
import {
    AirVent, Coffee, Computer, Fan, Gamepad2, Heater, Laptop, LampDesk,
    Microwave, Monitor, PlugZap, Printer, Radio, Refrigerator, Smartphone,
    Speaker, Tv, WashingMachine, Zap,
} from 'lucide-react';
import { Modal } from '../ui/Modal';

export const ICON_OPTIONS: Array<{ value: ApplianceProfileName; label: string; Icon: typeof Zap }> = [
    { value: 'power-strip', label: 'Power strip', Icon: Zap }, { value: 'plug', label: 'Plug', Icon: PlugZap },
    { value: 'lamp', label: 'Lamp', Icon: LampDesk }, { value: 'charger', label: 'Charger', Icon: Zap },
    { value: 'computer', label: 'Computer', Icon: Computer }, { value: 'laptop', label: 'Laptop', Icon: Laptop },
    { value: 'monitor', label: 'Monitor', Icon: Monitor }, { value: 'printer', label: 'Printer', Icon: Printer },
    { value: 'phone', label: 'Phone', Icon: Smartphone }, { value: 'router', label: 'Router', Icon: Radio },
    { value: 'tv', label: 'TV', Icon: Tv }, { value: 'speaker', label: 'Speaker', Icon: Speaker },
    { value: 'gamepad', label: 'Game console', Icon: Gamepad2 }, { value: 'fridge', label: 'Fridge', Icon: Refrigerator },
    { value: 'heater', label: 'Heater', Icon: Heater }, { value: 'fan', label: 'Fan', Icon: Fan },
    { value: 'air-conditioner', label: 'Air conditioner', Icon: AirVent }, { value: 'coffee', label: 'Coffee machine', Icon: Coffee },
    { value: 'kettle', label: 'Kettle', Icon: Coffee }, { value: 'microwave', label: 'Microwave', Icon: Microwave },
    { value: 'washer', label: 'Washer', Icon: WashingMachine }, { value: 'idle', label: 'General', Icon: Zap },
];

export function DeviceIcon({ icon, size = 20 }: { icon: ApplianceProfileName; size?: number }) {
    const Icon = ICON_OPTIONS.find((option) => option.value === icon)?.Icon ?? PlugZap;
    return <Icon size={size} strokeWidth={1.8} aria-hidden="true" />;
}

export function IconPicker({
    open,
    value,
    onClose,
    onSelect,
}: {
    open: boolean;
    value: ApplianceProfileName;
    onClose: () => void;
    onSelect: (icon: ApplianceProfileName) => void;
}) {
    return (
        <Modal open={open} onClose={onClose} title="Choose icon">
            <div className="grid grid-cols-4 gap-2 sm:grid-cols-5">
                {ICON_OPTIONS.map((option) => (
                    <button
                        key={option.value}
                        type="button"
                        title={option.label}
                        onClick={() => onSelect(option.value)}
                        className={`flex min-h-18 flex-col items-center justify-center gap-1 rounded-lg border px-1 py-2 text-xs ${value === option.value ? 'border-primary bg-primary/10 text-text' : 'border-border text-text-muted hover:bg-surface-hover'
                            }`}
                    >
                        <option.Icon size={22} strokeWidth={1.8} />
                        <span className="w-full truncate text-center">{option.label}</span>
                    </button>
                ))}
            </div>
        </Modal>
    );
}