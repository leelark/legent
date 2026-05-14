import { defineConfig, devices } from '@playwright/test';

const isVisualSmoke =
  process.env.npm_lifecycle_event === 'test:e2e:visual' ||
  process.argv.some((arg) => arg.includes('visual-smoke.spec'));
const isSanitizeSpec =
  process.env.npm_lifecycle_event === 'test:e2e:sanitize' ||
  process.argv.some((arg) => arg.includes('sanitize-html.spec'));
const defaultPort = isVisualSmoke ? '3011' : '3010';
const port = process.env.PLAYWRIGHT_PORT || process.env.PORT || defaultPort;
const baseURL = process.env.PLAYWRIGHT_BASE_URL || `http://127.0.0.1:${port}`;
const outputLabel = (process.env.npm_lifecycle_event || 'manual').replace(/[^A-Za-z0-9_-]/g, '-');
const outputDir = process.env.PLAYWRIGHT_OUTPUT_DIR || `test-results/${outputLabel}-${port}`;
const webServer = process.env.PLAYWRIGHT_SKIP_WEB_SERVER === '1' || isSanitizeSpec
  ? undefined
  : {
      command: process.env.PLAYWRIGHT_WEB_SERVER_COMMAND || 'npm run start',
      url: `${baseURL}/api/health`,
      reuseExistingServer: process.env.PLAYWRIGHT_REUSE_SERVER === '1',
      timeout: 120_000,
      env: {
        ...process.env,
        PORT: port,
        HOSTNAME: process.env.HOSTNAME || '127.0.0.1',
      },
    };

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  outputDir,
  webServer,
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
});
