import { Router } from 'express';
import { z } from 'zod';
import type { UserPublicDTO } from '../../../shared/types.js';
import { getMainDb } from '../db/mainDb.js';
import { newId } from '../utils/id.js';
import { requireAuth } from './authMiddleware.js';
import { authCookie, signAccessToken, signRefreshToken, verifyToken } from './jwt.js';
import { hashPassword, verifyPassword } from './passwordUtils.js';

export const authRoutes = Router();

const credentialsSchema = z.object({
  username: z.string().trim().min(3).max(64),
  password: z.string().min(8).max(200),
});

function toPublic(user: { id: string; username: string; createdAt: string; lastLoginAt: string | null }): UserPublicDTO {
  return { id: user.id, username: user.username, createdAt: user.createdAt, lastLoginAt: user.lastLoginAt };
}

function setAuthCookies(res: import('express').Response, userId: string): void {
  const accessToken = signAccessToken({ userId });
  const refreshToken = signRefreshToken({ userId });

  res.cookie(authCookie.accessTokenName, accessToken, {
    httpOnly: true,
    sameSite: 'lax',
    secure: false,
    maxAge: authCookie.accessTokenMaxAgeMs,
  });

  res.cookie(authCookie.refreshTokenName, refreshToken, {
    httpOnly: true,
    sameSite: 'lax',
    secure: false,
    maxAge: authCookie.refreshTokenMaxAgeMs,
  });
}

authRoutes.get('/setup-required', async (_req, res) => {
  const db = getMainDb();
  res.json({ setupRequired: db.data.users.length === 0 });
});

authRoutes.post('/setup', async (req, res) => {
  const db = getMainDb();
  if (db.data.users.length > 0) {
    res.status(409).json({ error: 'Admin account already exists' });
    return;
  }
  const parsed = credentialsSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid username or password', details: parsed.error.issues });
    return;
  }
  const { username, password } = parsed.data;
  const user = {
    id: newId('user'),
    username,
    passwordHash: await hashPassword(password),
    createdAt: new Date().toISOString(),
    lastLoginAt: new Date().toISOString(),
  };
  db.data.users.push(user);
  await db.write();
  setAuthCookies(res, user.id);
  res.status(201).json({ user: toPublic(user) });
});

authRoutes.post('/login', async (req, res) => {
  const parsed = credentialsSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid username or password' });
    return;
  }
  const db = getMainDb();
  const user = db.data.users.find((u) => u.username === parsed.data.username);
  if (!user || !(await verifyPassword(parsed.data.password, user.passwordHash))) {
    res.status(401).json({ error: 'Incorrect username or password' });
    return;
  }
  user.lastLoginAt = new Date().toISOString();
  await db.write();
  setAuthCookies(res, user.id);
  res.json({ user: toPublic(user) });
});

authRoutes.post('/refresh', async (req, res) => {
  const refreshToken = req.cookies?.[authCookie.refreshTokenName];
  if (!refreshToken) {
    res.status(401).json({ error: 'No refresh token' });
    return;
  }

  const payload = verifyToken(refreshToken);
  if (!payload) {
    res.clearCookie(authCookie.accessTokenName);
    res.clearCookie(authCookie.refreshTokenName);
    res.status(401).json({ error: 'Refresh token expired' });
    return;
  }

  const db = getMainDb();
  const user = db.data.users.find((u) => u.id === payload.userId);
  if (!user) {
    res.clearCookie(authCookie.accessTokenName);
    res.clearCookie(authCookie.refreshTokenName);
    res.status(401).json({ error: 'User not found' });
    return;
  }

  // Issue new access token
  const accessToken = signAccessToken({ userId: user.id });
  res.cookie(authCookie.accessTokenName, accessToken, {
    httpOnly: true,
    sameSite: 'lax',
    secure: false,
    maxAge: authCookie.accessTokenMaxAgeMs,
  });

  res.json({ ok: true });
});

authRoutes.post('/logout', (_req, res) => {
  res.clearCookie(authCookie.accessTokenName);
  res.clearCookie(authCookie.refreshTokenName);
  res.json({ ok: true });
});

authRoutes.get('/me', requireAuth, (req, res) => {
  const db = getMainDb();
  const user = db.data.users.find((u) => u.id === req.userId);
  if (!user) {
    res.status(401).json({ error: 'Not authenticated' });
    return;
  }
  res.json({ user: toPublic(user) });
});

const passwordChangeSchema = z.object({
  currentPassword: z.string().min(1),
  newPassword: z.string().min(8).max(200),
});

authRoutes.put('/password', requireAuth, async (req, res) => {
  const db = getMainDb();
  const user = db.data.users.find((u) => u.id === req.userId);
  if (!user) {
    res.status(401).json({ error: 'Not authenticated' });
    return;
  }
  const parsed = passwordChangeSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid request' });
    return;
  }
  if (!(await verifyPassword(parsed.data.currentPassword, user.passwordHash))) {
    res.status(401).json({ error: 'Current password is incorrect' });
    return;
  }
  user.passwordHash = await hashPassword(parsed.data.newPassword);
  await db.write();
  res.json({ ok: true });
});
