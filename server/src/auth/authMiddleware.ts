import type { NextFunction, Request, Response } from 'express';
import { verifyToken, authCookie, signAccessToken } from './jwt.js';

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      userId?: string;
    }
  }
}

export function requireAuth(req: Request, res: Response, next: NextFunction): void {
  const accessToken = req.cookies?.[authCookie.accessTokenName];
  const payload = accessToken ? verifyToken(accessToken) : null;
  if (payload) {
    req.userId = payload.userId;
    next();
    return;
  }

  // Try to refresh if access token is missing or invalid
  const refreshToken = req.cookies?.[authCookie.refreshTokenName];
  const refreshPayload = refreshToken ? verifyToken(refreshToken) : null;
  if (!refreshPayload) {
    res.status(401).json({ error: 'Not authenticated' });
    return;
  }

  // Refresh token is valid, issue new access token
  const newAccessToken = signAccessToken({ userId: refreshPayload.userId });
  res.cookie(authCookie.accessTokenName, newAccessToken, {
    httpOnly: true,
    sameSite: 'lax',
    secure: false,
    maxAge: authCookie.accessTokenMaxAgeMs,
  });

  req.userId = refreshPayload.userId;
  next();
}
