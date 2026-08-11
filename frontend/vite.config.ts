import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    // The backend is the only origin the dev server talks to. Requests are proxied so the
    // browser stays same-origin during development.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: true,
    restoreMocks: true,
  },
});
