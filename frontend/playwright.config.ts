import { defineConfig } from "@playwright/test";

/**
 * End-to-end test through a real browser. It needs Postgres, Keycloak (realm `sharewise`, which allows this port as
 * redirect URI) and the backend running:
 *   docker compose up -d db keycloak && (cd backend && ./mvnw spring-boot:run)
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
    env: {
      BACKEND_URL: process.env.BACKEND_URL ?? "http://localhost:8080",
      KEYCLOAK_ISSUER: process.env.KEYCLOAK_ISSUER ?? "http://localhost:8180/realms/sharewise",
      KEYCLOAK_CLIENT_ID: "sharewise-web",
      // Keycloak redirects back to this address; it must match the origin the browser uses.
      APP_URL: `http://localhost:${PORT}`,
    },
  },
});
