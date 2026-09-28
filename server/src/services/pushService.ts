import webpush from 'web-push';
import { env } from '../config/env.js';
import { getMainDb } from '../db/mainDb.js';
import type { StoredPushSubscription } from '../db/schema.js';
import { newId } from '../utils/id.js';
import { createLogger } from '../utils/logger.js';

const log = createLogger('pushService');

let configured = false;

export function isPushConfigured(): boolean {
  return configured;
}

export function configurePush(): void {
  if (!env.vapidPublicKey || !env.vapidPrivateKey) {
    log.warn('VAPID keys not set - Web Push disabled. Run "npm run generate-vapid-keys" in server/.');
    return;
  }
  webpush.setVapidDetails(env.vapidSubject, env.vapidPublicKey, env.vapidPrivateKey);
  configured = true;
}

export function getPublicKey(): string {
  return env.vapidPublicKey;
}

export async function subscribe(sub: { endpoint: string; keys: { p256dh: string; auth: string } }, userAgent: string | null): Promise<void> {
  const db = getMainDb();
  const exists = db.data.pushSubscriptions.some((s) => s.endpoint === sub.endpoint);
  if (exists) return;
  const record: StoredPushSubscription = {
    id: newId('push'),
    endpoint: sub.endpoint,
    keys: sub.keys,
    userAgent,
    createdAt: new Date().toISOString(),
  };
  db.data.pushSubscriptions.push(record);
  await db.write();
}

export async function unsubscribe(endpoint: string): Promise<void> {
  const db = getMainDb();
  const before = db.data.pushSubscriptions.length;
  db.data.pushSubscriptions = db.data.pushSubscriptions.filter((s) => s.endpoint !== endpoint);
  if (db.data.pushSubscriptions.length !== before) await db.write();
}

export async function sendPushToAll(payload: { title: string; body: string }): Promise<void> {
  if (!configured) return;
  const db = getMainDb();
  const subs = [...db.data.pushSubscriptions];
  if (subs.length === 0) return;
  const message = JSON.stringify(payload);
  const stale: string[] = [];
  await Promise.all(
    subs.map(async (sub) => {
      try {
        await webpush.sendNotification({ endpoint: sub.endpoint, keys: sub.keys }, message);
      } catch (err: unknown) {
        const statusCode = (err as { statusCode?: number }).statusCode;
        if (statusCode === 404 || statusCode === 410) {
          stale.push(sub.endpoint);
        } else {
          log.error('push send failed', err);
        }
      }
    }),
  );
  if (stale.length > 0) {
    db.data.pushSubscriptions = db.data.pushSubscriptions.filter((s) => !stale.includes(s.endpoint));
    await db.write();
  }
}
