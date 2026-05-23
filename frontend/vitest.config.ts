import { defineConfig } from 'vitest/config';
import path from 'node:path';

export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  test: {
    include: ['tests/unit/**/*.test.ts'],
    passWithNoTests: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'lcov'],
      reportsDirectory: 'coverage',
      include: ['src/lib/**/*.{ts,tsx}', 'src/stores/**/*.{ts,tsx}', 'src/hooks/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.d.ts',
        'src/lib/api-client.ts',
      ],
      thresholds: {
        statements: 0.1,
        branches: 0.1,
        functions: 0.1,
        lines: 0.1,
      },
    },
  },
});
