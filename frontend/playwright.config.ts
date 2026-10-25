import { defineConfig } from "@playwright/test";

/**
 * End-to-end test through a real browser. It needs the backend (no dev profile needed) and Postgres running:
 *   docker compose up -d db && (cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local)
 * The frontend is started here, on its own port, so a dev server you have open is never reused by accident.
 */
const PORT = 3100;

export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  workers: 1,
  expect: { timeout: 20_000 }, // the dev server compiles each page on first visit
  reporter: [["list"]],
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: {
    command: `npm run dev -- -p ${PORT}`,
    url: `http://localhost:${PORT}/login`,
    reuseExistingServer: false,
    timeout: 120_000,
    env: { BACKEND_URL: process.env.BACKEND_URL ?? "http://localhost:8080" },
  },
});
