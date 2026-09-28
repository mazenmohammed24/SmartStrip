import { useEffect } from 'react';
import { useSettingsStore } from '../store/settingsStore';

export function useThemeEffect(): void {
  const theme = useSettingsStore((s) => s.settings?.theme ?? 'system');
  const accentColor = useSettingsStore((s) => s.settings?.accentColor ?? '#0f766e');

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'system') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', theme);
    }

    root.style.setProperty('--color-primary', accentColor);
    root.style.setProperty('--color-primary-hover', shadeHex(accentColor, -18));
    root.style.setProperty('--color-success', accentColor);
    root.style.setProperty('--color-success-bg', tintHex(accentColor, 88));
  }, [theme, accentColor]);
}

function shadeHex(hex: string, percent: number): string {
  const amount = Math.round(2.55 * percent);
  const value = Number.parseInt(hex.slice(1), 16);
  const red = clamp((value >> 16) + amount);
  const green = clamp(((value >> 8) & 0xff) + amount);
  const blue = clamp((value & 0xff) + amount);
  return `#${((1 << 24) + (red << 16) + (green << 8) + blue).toString(16).slice(1)}`;
}

function tintHex(hex: string, percent: number): string {
  const value = Number.parseInt(hex.slice(1), 16);
  const red = Math.round(((value >> 16) * (100 - percent) + 255 * percent) / 100);
  const green = Math.round((((value >> 8) & 0xff) * (100 - percent) + 255 * percent) / 100);
  const blue = Math.round(((value & 0xff) * (100 - percent) + 255 * percent) / 100);
  return `#${((1 << 24) + (red << 16) + (green << 8) + blue).toString(16).slice(1)}`;
}

function clamp(value: number): number {
  return Math.max(0, Math.min(255, value));
}
