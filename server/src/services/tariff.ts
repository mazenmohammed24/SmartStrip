import type { TariffInfoDTO } from '../../../shared/types.js';

/**
 * Egypt residential tariff, in force from April 2026 consumption (EgyptERA, egyptera.org
 * "التعريفة السارية بدءاً من استهلاك أبريل 2026"). Piastres per kWh; service charge in EGP/month
 * from the August 2024 schedule, unchanged since.
 *
 * The slab a household lands in is set by its whole monthly total:
 *   up to 100 kWh   progressive 0-50 @68, 51-100 @78
 *   101-650 kWh     progressive 0-200 @95, 201-350 @155, 351-650 @195 (a fresh ladder, not stacked on the one above)
 *   651-1000 kWh    every kWh @210
 *   over 1000 kWh   every kWh @258
 */
interface Bracket {
  upTo: number;
  steps: Array<{ upTo: number; piastres: number }>;
  service: number;
}

const BRACKETS: Bracket[] = [
  { upTo: 50, steps: [{ upTo: 50, piastres: 68 }], service: 1 },
  { upTo: 100, steps: [{ upTo: 50, piastres: 68 }, { upTo: 100, piastres: 78 }], service: 2 },
  { upTo: 200, steps: [{ upTo: 200, piastres: 95 }], service: 6 },
  { upTo: 350, steps: [{ upTo: 200, piastres: 95 }, { upTo: 350, piastres: 155 }], service: 11 },
  { upTo: 650, steps: [{ upTo: 200, piastres: 95 }, { upTo: 350, piastres: 155 }, { upTo: 650, piastres: 195 }], service: 15 },
  { upTo: 1000, steps: [{ upTo: Infinity, piastres: 210 }], service: 25 },
  { upTo: Infinity, steps: [{ upTo: Infinity, piastres: 258 }], service: 40 },
];

/** Full monthly bill in EGP for a month's total kWh. Zero usage bills nothing so partial-window deltas stay consistent. */
export function egyptMonthlyBill(kwh: number): number {
  if (kwh <= 0) return 0;
  const bracket = BRACKETS.find((b) => kwh <= b.upTo) ?? BRACKETS[BRACKETS.length - 1];
  let bill = bracket.service;
  let from = 0;
  for (const step of bracket.steps) {
    const slice = Math.min(kwh, step.upTo) - from;
    if (slice > 0) bill += (slice * step.piastres) / 100;
    from = step.upTo;
    if (kwh <= step.upTo) break;
  }
  return bill;
}

const LABELS = ['0 - 50', '51 - 100', '101 - 200', '201 - 350', '351 - 650', '651 - 1,000', 'Over 1,000'];

export function egyptTariffInfo(monthToDateKwh: number): TariffInfoDTO {
  const current = Math.max(0, BRACKETS.findIndex((b) => monthToDateKwh <= b.upTo));
  const index = monthToDateKwh > 0 ? current : -1;
  return {
    mode: 'egypt',
    name: 'Egypt residential (EgyptERA, Apr 2026)',
    monthToDateKwh: Math.round(monthToDateKwh * 100) / 100,
    currentLevel: index,
    levels: BRACKETS.map((b, i) => ({
      label: LABELS[i],
      rate: b.steps[b.steps.length - 1].piastres / 100,
      service: b.service,
    })),
    note: 'The slab is set by your whole monthly total. 101-650 kWh uses 0-200 @0.95, 201-350 @1.55, 351-650 @1.95. Above 650 every kWh costs the flat rate. Costs here cover the strip only.',
  };
}
