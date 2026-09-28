import { create } from 'zustand';
import type { NotificationDTO } from '@shared/types';
import { api } from '../lib/apiClient';

interface NotificationState {
  notifications: NotificationDTO[];
  loaded: boolean;
  fetchNotifications: () => Promise<void>;
  addNotification: (n: NotificationDTO) => void;
  markRead: (id: string) => Promise<void>;
  markAllRead: () => Promise<void>;
  remove: (id: string) => Promise<void>;
  unreadCount: () => number;
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  loaded: false,

  fetchNotifications: async () => {
    const { notifications } = await api.get<{ notifications: NotificationDTO[] }>('/notifications?limit=200');
    set({ notifications, loaded: true });
  },

  addNotification: (n) => {
    set({ notifications: [n, ...get().notifications] });
  },

  markRead: async (id) => {
    await api.patch(`/notifications/${id}/read`);
    set({
      notifications: get().notifications.map((n) => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n)),
    });
  },

  markAllRead: async () => {
    await api.post('/notifications/read-all');
    const now = new Date().toISOString();
    set({ notifications: get().notifications.map((n) => (n.readAt ? n : { ...n, readAt: now })) });
  },

  remove: async (id) => {
    await api.delete(`/notifications/${id}`);
    set({ notifications: get().notifications.filter((n) => n.id !== id) });
  },

  unreadCount: () => get().notifications.filter((n) => !n.readAt).length,
}));
