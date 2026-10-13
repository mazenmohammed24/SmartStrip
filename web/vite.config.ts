import path from 'node:path';
import { fileURLToPath } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

const thisDir = path.dirname(fileURLToPath(import.meta.url));

/**
 * Where the app is served from.
 *
 * Defaults to '/' so a normal build is unchanged and works standalone on its own
 * port. Behind the Local Server Manager reverse proxy (which mounts this app at a
 * sub-path on a shared origin) the build must use that sub-path instead, or every
 * asset URL resolves against the manager root and the page comes up blank with a
 * wall of 404s.
 *
 * `npm run build:portal` selects it via `--mode portal`, which does not depend on
 * an exported environment variable that a fresh shell silently drops. An explicit
 * VITE_BASE_PATH always wins.
 */
const PORTAL_BASE_PATH = '/SmartStrip/';

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const basePath = process.env.VITE_BASE_PATH || (mode === 'portal' ? PORTAL_BASE_PATH : '/');
  return {
  base: basePath,
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      injectRegister: 'auto',
      manifest: {
        name: 'SmartStrip',
        short_name: 'SmartStrip',
        description: 'Control your smart power strips - outlets, power monitoring, automations, notifications.',
        theme_color: '#0f172a',
        background_color: '#0f172a',
        display: 'standalone',
        start_url: basePath,
        scope: basePath,
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      devOptions: { enabled: false },
      injectManifest: { rollupFormat: 'iife' },
    }),
  ],
  server: {
    fs: { allow: ['..'] },
    proxy: {
      '/api': { target: 'http://localhost:4001', changeOrigin: true },
      '/ws': { target: 'ws://localhost:4001', ws: true },
    },
  },
  resolve: {
    alias: {
      '@shared': path.resolve(thisDir, '..', 'shared'),
    },
  },
  };
});
