/**
 * API root.
 *
 * Vite rewrites `import.meta.env.BASE_URL` from the `base` config option, so
 * this is '/api' for a normal build and '/SmartStrip/api' for a build made
 * with VITE_BASE_PATH=/SmartStrip/ (used when hosted under the Local Server
 * Manager reverse proxy).
 */
const BASE = `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api`;

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

let isRefreshing = false;
let refreshPromise: Promise<void> | null = null;

async function refreshToken(): Promise<void> {
  if (isRefreshing && refreshPromise) {
    return refreshPromise;
  }

  isRefreshing = true;
  refreshPromise = (async () => {
    try {
      await fetch(`${BASE}/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
      });
    } catch {
      // Refresh failed, user will be logged out by auth store
      throw new ApiError(401, 'Token refresh failed');
    } finally {
      isRefreshing = false;
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let res = await fetch(`${BASE}${path}`, {
    ...options,
    credentials: 'include',
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers,
    },
  });

  // If we get 401, try to refresh token once
  if (res.status === 401 && !path.includes('/auth/')) {
    try {
      await refreshToken();
      // Retry the original request with new token
      res = await fetch(`${BASE}${path}`, {
        ...options,
        credentials: 'include',
        headers: {
          ...(options.body ? { 'Content-Type': 'application/json' } : {}),
          ...options.headers,
        },
      });
    } catch (err) {
      // Refresh failed, let error handling below take over
    }
  }

  const isJson = res.headers.get('content-type')?.includes('application/json');
  const body = isJson ? await res.json().catch(() => null) : null;

  if (!res.ok) {
    const message = (body && typeof body === 'object' && 'error' in body ? String((body as { error: unknown }).error) : null) ?? res.statusText;
    throw new ApiError(res.status, message);
  }
  return body as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, data?: unknown) => request<T>(path, { method: 'POST', body: data !== undefined ? JSON.stringify(data) : undefined }),
  patch: <T>(path: string, data?: unknown) => request<T>(path, { method: 'PATCH', body: data !== undefined ? JSON.stringify(data) : undefined }),
  put: <T>(path: string, data?: unknown) => request<T>(path, { method: 'PUT', body: data !== undefined ? JSON.stringify(data) : undefined }),
  delete: <T>(path: string, data?: unknown) => request<T>(path, { method: 'DELETE', body: data !== undefined ? JSON.stringify(data) : undefined }),
};
