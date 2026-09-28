import 'dotenv/config';

function required(name: string, fallback?: string): string {
  const value = process.env[name] ?? fallback;
  if (value === undefined) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

export const env = {
  port: Number(process.env.PORT ?? 4001),
  jwtSecret: required('JWT_SECRET', 'dev-only-insecure-secret-change-me'),
  driver: process.env.DRIVER ?? 'mock',
  voltraAccessToken: process.env.VOLTRA_ACCESS_TOKEN ?? '',
  voltraBaseUrl: process.env.VOLTRA_BASE_URL ?? 'https://toi.ommeq.com',
  vapidPublicKey: process.env.VAPID_PUBLIC_KEY ?? '',
  vapidPrivateKey: process.env.VAPID_PRIVATE_KEY ?? '',
  vapidSubject: process.env.VAPID_SUBJECT ?? 'mailto:admin@example.com',
  isProduction: process.env.NODE_ENV === 'production',
};
