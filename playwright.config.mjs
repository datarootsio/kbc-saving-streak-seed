import { defineConfig } from '@playwright/test';

const baseURL = 'http://127.0.0.1:18081';

export default defineConfig({
  testDir: './tests/browser',
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  reporter: 'list',
  outputDir: 'output/playwright/test-results',
  use: {
    baseURL,
    browserName: 'chromium',
    timezoneId: 'UTC',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: {
    command: 'node tests/browser/server.mjs',
    url: baseURL + '/api/reminders',
    reuseExistingServer: false,
    timeout: 180000,
    gracefulShutdown: { signal: 'SIGTERM', timeout: 10000 }
  }
});
