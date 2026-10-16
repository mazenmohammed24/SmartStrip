import { useMemo, useState } from 'react';
import type { ApplianceProfileName } from '@shared/types';
import { DEVICE_ICONS, type DeviceIconId } from '@shared/deviceIcons';
import {
    AirVent, Baby, Bath, BatteryCharging, Bed, Bell, Bike, Blender, Building2, Camera, Car, Cctv, Clock, Coffee,
    CookingPot, Cog, Computer, Dog, DoorOpen, Drill, Droplet, Droplets, Drumstick, Factory, Fan, Fence, Fish, Flame,
    Flashlight, Fuel, Gamepad2, Gauge, GlassWater, Guitar, HardDrive, Headphones, Heater, Keyboard, LampCeiling,
    LampDesk, Laptop, Lock, Microwave, Monitor, Moon, Music, Piano, Plug, PlugZap, Printer, Projector, Radio,
    Refrigerator, Router, Scissors, Server, ShowerHead, Siren, Smartphone, Snowflake, Sofa, Soup, Sparkle, Sparkles,
    Speaker, Sprout, Store, Sun, Tablet, ToggleLeft, Tv, UtensilsCrossed, Brush, Watch, Waves, WashingMachine, Wifi,
    Wind, Wrench, Zap, Shirt, Sandwich,
} from 'lucide-react';
import { Modal } from '../ui/Modal';

type LucideIcon = typeof Zap;

const GLYPHS: Record<DeviceIconId, LucideIcon> = {
    'power-strip': Zap, plug: PlugZap, charger: BatteryCharging, solar: Sun, 'ev-charger': Car, 'e-bike': Bike,
    generator: Factory, idle: Cog,
    lamp: LampDesk, 'ceiling-light': LampCeiling, 'night-light': Moon, 'led-strip': Sparkles, lantern: Flashlight,
    fridge: Refrigerator, freezer: Snowflake, microwave: Microwave, oven: Flame, kettle: Soup, coffee: Coffee,
    toaster: Sandwich, blender: Blender, dishwasher: UtensilsCrossed, 'rice-cooker': CookingPot, 'air-fryer': Drumstick,
    'water-dispenser': GlassWater,
    washer: WashingMachine, dryer: Shirt, iron: Sparkle, vacuum: Brush,
    'air-conditioner': AirVent, fan: Fan, heater: Heater, 'water-heater': ShowerHead, dehumidifier: Droplet,
    humidifier: Droplets, 'air-purifier': Wind,
    computer: Computer, laptop: Laptop, monitor: Monitor, keyboard: Keyboard, printer: Printer, router: Router,
    modem: Wifi, server: Server, 'hard-drive': HardDrive, phone: Smartphone, tablet: Tablet, smartwatch: Watch,
    camera: Camera, cctv: Cctv,
    tv: Tv, speaker: Speaker, soundbar: Music, headphones: Headphones, gamepad: Gamepad2, projector: Projector,
    radio: Radio, piano: Piano, guitar: Guitar,
    tool: Wrench, drill: Drill, compressor: Gauge, pump: Fuel, sewing: Scissors,
    bed: Bed, sofa: Sofa, door: DoorOpen, lock: Lock, bell: Bell, gate: Fence, garden: Sprout, fountain: Waves,
    pool: Bath, aquarium: Fish, pet: Dog, 'baby-monitor': Baby, alarm: Siren, clock: Clock, switch: ToggleLeft,
    office: Building2, shop: Store,
};

export const ICON_OPTIONS = DEVICE_ICONS.map((i) => ({ value: i.id as ApplianceProfileName, label: i.label, group: i.group, Icon: GLYPHS[i.id] }));

export function DeviceIcon({ icon, size = 20 }: { icon: ApplianceProfileName; size?: number }) {
    const Icon = GLYPHS[icon] ?? Plug;
    return <Icon size={size} strokeWidth={1.8} aria-hidden="true" />;
}

export function IconGrid({ value, onSelect, maxHeight = '55vh' }: { value: ApplianceProfileName; onSelect: (icon: ApplianceProfileName) => void; maxHeight?: string }) {
    const [query, setQuery] = useState('');
    const groups = useMemo(() => {
        const q = query.trim().toLowerCase();
        const map = new Map<string, typeof ICON_OPTIONS>();
        for (const o of ICON_OPTIONS) {
            if (q && !o.label.toLowerCase().includes(q) && !o.group.toLowerCase().includes(q)) continue;
            map.set(o.group, [...(map.get(o.group) ?? []), o]);
        }
        return [...map.entries()];
    }, [query]);

    return (
        <>
            <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder={`Search ${ICON_OPTIONS.length} icons`}
                aria-label="Search icons"
                className="mb-3 w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text outline-none focus:border-primary"
            />
            <div className="flex flex-col gap-4 overflow-y-auto pr-1" style={{ maxHeight }}>
                {groups.length === 0 && <p className="py-6 text-center text-sm text-text-muted">No icons match.</p>}
                {groups.map(([group, options]) => (
                    <section key={group}>
                        <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-primary">{group}</p>
                        <div className="grid grid-cols-5 gap-1.5 sm:grid-cols-6">
                            {options.map((option) => (
                                <button
                                    key={option.value}
                                    type="button"
                                    title={option.label}
                                    onClick={() => {
                                        setQuery('');
                                        onSelect(option.value);
                                    }}
                                    className={`flex min-h-14 flex-col items-center justify-center gap-0.5 rounded-lg border px-1 py-1.5 text-[11px] ${value === option.value ? 'border-primary bg-primary/10 text-text' : 'border-border text-text-muted hover:bg-surface-hover'}`}
                                >
                                    <option.Icon size={20} strokeWidth={1.8} />
                                    <span className="w-full truncate text-center">{option.label}</span>
                                </button>
                            ))}
                        </div>
                    </section>
                ))}
            </div>
        </>
    );
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
            <IconGrid value={value} onSelect={onSelect} />
        </Modal>
    );
}
