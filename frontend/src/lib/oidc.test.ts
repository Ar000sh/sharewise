// @vitest-environment node
import { afterEach, expect, it, vi } from "vitest";
import { authorizationUrl, endSessionUrl, exchangeCode, keycloakReachable, refresh } from "./oidc";

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

it("keeps browser redirects public while token exchange and refresh use Docker networking", async () => {
  vi.stubEnv("KEYCLOAK_ISSUER", "http://localhost:8180/realms/sharewise");
  vi.stubEnv("KEYCLOAK_INTERNAL_ISSUER", "http://keycloak:8080/realms/sharewise/");
  const request = vi.fn().mockImplementation(async () => Response.json({
    access_token: "access", refresh_token: "refresh", expires_in: 300,
  }));
  vi.stubGlobal("fetch", request);
  expect(new URL(authorizationUrl({ state: "state", challenge: "challenge", register: false })).origin)
    .toBe("http://localhost:8180");
  expect(new URL(endSessionUrl()).origin).toBe("http://localhost:8180");
  await exchangeCode("code", "verifier");
  await refresh("refresh");
  expect(await keycloakReachable()).toBe(true);
  expect(request.mock.calls.map(call => call[0])).toEqual([
    "http://keycloak:8080/realms/sharewise/protocol/openid-connect/token",
    "http://keycloak:8080/realms/sharewise/protocol/openid-connect/token",
    "http://keycloak:8080/realms/sharewise/.well-known/openid-configuration",
  ]);
});

it("uses the public issuer for server requests when no internal address is configured", async () => {
  vi.stubEnv("KEYCLOAK_ISSUER", "http://localhost:8180/realms/sharewise");
  vi.stubEnv("KEYCLOAK_INTERNAL_ISSUER", undefined);
  const request = vi.fn().mockResolvedValue(Response.json({}));
  vi.stubGlobal("fetch", request);
  expect(await keycloakReachable()).toBe(true);
  expect(request).toHaveBeenCalledWith(
    "http://localhost:8180/realms/sharewise/.well-known/openid-configuration", expect.any(Object),
  );
});
