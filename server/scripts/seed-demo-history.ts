// Backfills ~30 days of power history so charts aren't empty on a fresh install
// with an already-adopted fleet. Run with: npm run seed-demo-history --prefix server
import { initMainDb, getMainDb } from '../src/db/mainDb.js';
import { initPowerDb, getPowerDb } from '../src/db/powerDb.js';
import { APPLIANCE_PROFILES } from '../src/drivers/mock/applianceProfiles.js';
import { newId } from '../src/utils/id.js';
import type { StoredPowerSample } from '../src/db/schema.js';

const DAILY_ON_HOURS: Record<string, number> = {
  lamp: 6,
  charger: 3,
  fridge: 24,
  heater: 2,
  idle: 1,
};

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}
function round4(n: number): number {
  return Math.round(n * 10000) / 10000;
}

async function main(): Promise<void> {
  await Promise.all([initMainDb(), initPowerDb()]);
  const mainDb = getMainDb();
  const powerDb = getPowerDb();

  if (mainDb.data.strips.length === 0) {
    console.log('No strips in the fleet yet - scan and adopt at least one strip first, then re-run this script.');
    return;
  }

  const now = new Date();
  const samples: StoredPowerSample[] = [];

  for (const relay of mainDb.data.relays) {
    const profile = APPLIANCE_PROFILES[relay.icon as keyof typeof APPLIANCE_PROFILES] ?? APPLIANCE_PROFILES.idle;
    const onHours = DAILY_ON_HOURS[relay.icon] ?? 2;

    for (let daysAgo = 29; daysAgo >= 0; daysAgo--) {
      const bucketStart = new Date(now);
      bucketStart.setHours(0, 0, 0, 0);
      bucketStart.setDate(bucketStart.getDate() - daysAgo);
      const jitter = 0.85 + Math.random() * 0.3;
      const kwh = round4(((profile.baseWatts * onHours) / 1000) * jitter);
      const avgW = round2((kwh * 1000) / 24);
      samples.push({
        id: newId('pwr'),
        stripId: relay.stripId,
        relayIndex: relay.index,
        resolution: 'day',
        bucketStart: bucketStart.toISOString(),
        avgW,
        minW: round2(avgW * 0.4),
        maxW: round2(Math.max(avgW * 1.8, profile.baseWatts)),
        kwh,
      });

      if (daysAgo < 7) {
        for (let hour = 0; hour < 24; hour++) {
          const hourBucket = new Date(bucketStart);
          hourBucket.setHours(hour);
          const hourJitter = 0.7 + Math.random() * 0.6;
          const isOnThisHour = hour < onHours || relay.icon === 'fridge';
          const hourAvgW = isOnThisHour ? round2(profile.baseWatts * hourJitter) : round2(profile.standbyWatts);
          samples.push({
            id: newId('pwr'),
            stripId: relay.stripId,
            relayIndex: relay.index,
            resolution: 'hour',
            bucketStart: hourBucket.toISOString(),
            avgW: hourAvgW,
            minW: round2(hourAvgW * 0.6),
            maxW: round2(hourAvgW * 1.3),
            kwh: round4(hourAvgW / 1000),
          });
        }
      }
    }
  }

  powerDb.data.samples.push(...samples);
  await powerDb.write();
  console.log(`Seeded ${samples.length} power history rows for ${mainDb.data.relays.length} relay(s).`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
