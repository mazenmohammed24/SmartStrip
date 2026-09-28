import { env } from '../../config/env.js';
import { getMainDb } from '../../db/mainDb.js';
import { createLogger } from '../../utils/logger.js';

const log = createLogger('voltra-auth');
const TOKEN_LIFETIME_MS = 24 * 60 * 60 * 1000;
const REFRESH_BEFORE_EXPIRY_MS = 30 * 60 * 1000;
const CHECK_INTERVAL_MS = 5 * 60 * 1000;

interface StoredVoltraTokens {
    accessToken: string;
    refreshToken: string;
    expiresAt: string | null;
}

interface VoltraRefreshResponse {
    accessToken?: unknown;
    access_token?: unknown;
    token?: unknown;
    refreshToken?: unknown;
    refresh_token?: unknown;
    expiresIn?: unknown;
    expires_in?: unknown;
}

class VoltraTokenManager {
    private currentAccessToken = env.voltraAccessToken;
    private currentRefreshToken = process.env.VOLTRA_REFRESH_TOKEN ?? '';
    private tokenExpiresAt = Date.now() + TOKEN_LIFETIME_MS;
    private refreshHandle: ReturnType<typeof setInterval> | null = null;
    private isRefreshing = false;

    async init(): Promise<void> {
        const storedTokens = this.getStoredTokens();
        if (storedTokens) {
            this.applyTokens(storedTokens);
        }

        if (!this.currentAccessToken && this.currentRefreshToken) {
            await this.refresh();
        }

        if (this.refreshHandle) clearInterval(this.refreshHandle);
        this.refreshHandle = setInterval(() => {
            const timeUntilExpiry = this.tokenExpiresAt - Date.now();
            if (timeUntilExpiry < REFRESH_BEFORE_EXPIRY_MS) {
                void this.refresh();
            }
        }, CHECK_INTERVAL_MS);
        this.refreshHandle.unref();
    }

    async saveTokens(tokens: { accessToken: string; refreshToken: string }): Promise<void> {
        const storedTokens = {
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken,
            expiresAt: new Date(Date.now() + TOKEN_LIFETIME_MS).toISOString(),
        };
        this.applyTokens(storedTokens);
        await this.persistTokens(storedTokens);
        log.info('Voltra tokens saved');
    }

    getToken(): string {
        return this.currentAccessToken;
    }

    async onTokenRejected(): Promise<void> {
        log.warn('Voltra access token was rejected; refreshing with refresh token');
        await this.refresh();
    }

    shutdown(): void {
        if (this.refreshHandle) clearInterval(this.refreshHandle);
    }

    private async refresh(): Promise<void> {
        if (this.isRefreshing) return;
        if (!this.currentRefreshToken) {
            log.warn('No Voltra refresh token available; cannot refresh access token automatically');
            return;
        }

        this.isRefreshing = true;
        try {
            const refreshedTokens = await this.refreshWithVoltra(this.currentRefreshToken);
            this.applyTokens(refreshedTokens);
            await this.persistTokens(refreshedTokens);
            log.info('Voltra access token refreshed successfully');
        } catch (err) {
            log.error('Failed to refresh Voltra access token', err);
        } finally {
            this.isRefreshing = false;
        }
    }

    private async refreshWithVoltra(refreshToken: string): Promise<StoredVoltraTokens> {
        const response = await fetch(`${env.voltraBaseUrl}/api/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken }),
            signal: AbortSignal.timeout(10_000),
        });

        if (!response.ok) {
            const bodyText = await response.text().catch(() => '');
            const detail = bodyText.trim() ? ` - ${bodyText.trim().slice(0, 200)}` : '';
            if (response.status === 404) {
                throw new Error(
                    `Voltra token refresh failed (404): refresh endpoint not found at ${env.voltraBaseUrl}/api/refresh. ` +
                    'Check VOLTRA_BASE_URL.',
                );
            }
            if (response.status === 400 || response.status === 401) {
                throw new Error(
                    `Voltra token refresh failed (${response.status})${detail}. ` +
                    'The stored refresh token is missing, expired, or invalid; re-authenticate with Voltra.',
                );
            }
            throw new Error(`Voltra token refresh failed (${response.status})${detail}`);
        }

        const data = (await response.json()) as VoltraRefreshResponse;
        const accessToken = data.accessToken ?? data.access_token ?? data.token;
        const nextRefreshToken = data.refreshToken ?? data.refresh_token ?? refreshToken;
        const expiresInSeconds = data.expiresIn ?? data.expires_in;

        if (typeof accessToken !== 'string') {
            throw new Error('Voltra refresh response did not include an access token');
        }
        if (typeof nextRefreshToken !== 'string') {
            throw new Error('Voltra refresh response did not include a refresh token');
        }

        const lifetimeMs = typeof expiresInSeconds === 'number' ? expiresInSeconds * 1000 : TOKEN_LIFETIME_MS;
        return {
            accessToken,
            refreshToken: nextRefreshToken,
            expiresAt: new Date(Date.now() + lifetimeMs).toISOString(),
        };
    }

    private applyTokens(tokens: StoredVoltraTokens): void {
        this.currentAccessToken = tokens.accessToken;
        this.currentRefreshToken = tokens.refreshToken;
        this.tokenExpiresAt = tokens.expiresAt ? new Date(tokens.expiresAt).getTime() : Date.now() + TOKEN_LIFETIME_MS;
    }

    private getStoredTokens(): StoredVoltraTokens | null {
        const db = getMainDb();
        const tokens = db.data.voltraTokens;
        if (tokens?.accessToken && tokens.refreshToken) {
            return tokens;
        }
        if (this.currentAccessToken && this.currentRefreshToken) {
            return {
                accessToken: this.currentAccessToken,
                refreshToken: this.currentRefreshToken,
                expiresAt: new Date(this.tokenExpiresAt).toISOString(),
            };
        }
        return null;
    }

    private async persistTokens(tokens: StoredVoltraTokens): Promise<void> {
        const db = getMainDb();
        db.data.voltraTokens = tokens;
        await db.write();
    }
}

export const voltraTokenManager = new VoltraTokenManager();
