import jwt from 'jsonwebtoken';
import { env } from '../config/env.js';

export interface TokenPayload {
  userId: string;
}

const ACCESS_TOKEN_COOKIE_NAME = 'smartstrip_access_token';
const REFRESH_TOKEN_COOKIE_NAME = 'smartstrip_refresh_token';
const ACCESS_TOKEN_TTL = '15m'; // Short-lived access token
const REFRESH_TOKEN_TTL = '30d'; // Long-lived refresh token
const ACCESS_TOKEN_MAX_AGE_MS = 15 * 60 * 1000; // 15 minutes
const REFRESH_TOKEN_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

export function signAccessToken(payload: TokenPayload): string {
  return jwt.sign(payload, env.jwtSecret, { expiresIn: ACCESS_TOKEN_TTL });
}

export function signRefreshToken(payload: TokenPayload): string {
  return jwt.sign(payload, env.jwtSecret, { expiresIn: REFRESH_TOKEN_TTL });
}

export function verifyToken(token: string): TokenPayload | null {
  try {
    return jwt.verify(token, env.jwtSecret) as TokenPayload;
  } catch {
    return null;
  }
}

export const authCookie = {
  accessTokenName: ACCESS_TOKEN_COOKIE_NAME,
  refreshTokenName: REFRESH_TOKEN_COOKIE_NAME,
  accessTokenMaxAgeMs: ACCESS_TOKEN_MAX_AGE_MS,
  refreshTokenMaxAgeMs: REFRESH_TOKEN_MAX_AGE_MS,
};
