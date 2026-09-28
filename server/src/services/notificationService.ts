import type { NotificationDTO, NotificationSeverity } from '../../../shared/types.js';
import { getMainDb } from '../db/mainDb.js';
import type { StoredNotification } from '../db/schema.js';
import { newId } from '../utils/id.js';
import { broadcast } from '../ws/broadcast.js';
import { sendPushToAll } from './pushService.js';

const MAX_NOTIFICATIONS = 1000;

export interface NotifyInput {
  type: StoredNotification['type'];
  title: string;
  body: string;
  severity: NotificationSeverity;
  relatedStripId?: string | null;
  relatedStripMac?: string | null;
  relatedRelayIndex?: number | null;
  push?: boolean;
}

function toDTO(n: StoredNotification): NotificationDTO {
  return {
    id: n.id,
    type: n.type,
    title: n.title,
    body: n.body,
    severity: n.severity,
    relatedStripId: n.relatedStripId,
    relatedStripMac: n.relatedStripMac,
    relatedRelayIndex: n.relatedRelayIndex,
    createdAt: n.createdAt,
    readAt: n.readAt,
  };
}

export async function notify(input: NotifyInput): Promise<NotificationDTO> {
  const db = getMainDb();
  const notification: StoredNotification = {
    id: newId('ntf'),
    type: input.type,
    title: input.title,
    body: input.body,
    severity: input.severity,
    relatedStripId: input.relatedStripId ?? null,
    relatedStripMac: input.relatedStripMac ?? null,
    relatedRelayIndex: input.relatedRelayIndex ?? null,
    createdAt: new Date().toISOString(),
    readAt: null,
  };
  db.data.notifications.unshift(notification);
  if (db.data.notifications.length > MAX_NOTIFICATIONS) {
    db.data.notifications.length = MAX_NOTIFICATIONS;
  }
  await db.write();
  const dto = toDTO(notification);
  broadcast({ type: 'notification', notification: dto });
  if (input.push !== false) {
    void sendPushToAll({ title: input.title, body: input.body });
  }
  return dto;
}

export function listNotifications(opts: { unreadOnly?: boolean; limit?: number }): NotificationDTO[] {
  const db = getMainDb();
  let items = db.data.notifications;
  if (opts.unreadOnly) items = items.filter((n) => n.readAt === null);
  const limit = Math.min(opts.limit ?? 100, 500);
  return items.slice(0, limit).map(toDTO);
}

export async function markRead(id: string): Promise<void> {
  const db = getMainDb();
  const n = db.data.notifications.find((x) => x.id === id);
  if (n && !n.readAt) {
    n.readAt = new Date().toISOString();
    await db.write();
  }
}

export async function markAllRead(): Promise<void> {
  const db = getMainDb();
  const now = new Date().toISOString();
  let dirty = false;
  for (const n of db.data.notifications) {
    if (!n.readAt) {
      n.readAt = now;
      dirty = true;
    }
  }
  if (dirty) await db.write();
}

export async function removeNotification(id: string): Promise<void> {
  const db = getMainDb();
  const before = db.data.notifications.length;
  db.data.notifications = db.data.notifications.filter((n) => n.id !== id);
  if (db.data.notifications.length !== before) await db.write();
}
