import assert from "node:assert/strict";
import { test } from "node:test";
import cookie from "@fastify/cookie";
import Fastify from "fastify";
import { TEST_AUTH } from "../../testing/testAuth.js";
import { errorHandler } from "./errorHandler.js";
import { requireAdmin } from "./requireAdmin.js";

// The hook on its own; the real admin routes are checked for 401 in config.admin.test.js
function testApp(secret = TEST_AUTH.sessionSecret) {
  const app = Fastify({ logger: false });
  app.setErrorHandler(errorHandler);
  app.register(cookie, { secret });
  app.register(async (scope) => {
    scope.addHook("onRequest", requireAdmin);
    scope.get("/admin/secret", async () => ({ ok: true }));
  });
  return app;
}

async function requestWith(cookieValue) {
  const app = testApp();
  const headers = cookieValue === undefined ? {} : { cookie: `nyx_session=${cookieValue}` };
  const response = await app.inject({ method: "GET", url: "/admin/secret", headers });
  await app.close();
  return response;
}

async function signedBy(secret, value) {
  const app = testApp(secret);
  await app.ready();
  const signed = app.signCookie(value);
  await app.close();
  return signed;
}

const inAnHour = () => String(Date.now() + 60 * 60 * 1000);

test("US-23 valid, unexpired cookie → passes", async () => {
  const response = await requestWith(await signedBy(TEST_AUTH.sessionSecret, inAnHour()));
  assert.equal(response.statusCode, 200);
});

test("US-23 no cookie → 401 UNAUTHENTICATED", async () => {
  const response = await requestWith(undefined);
  assert.equal(response.statusCode, 401);
  assert.equal(response.json().error.code, "UNAUTHENTICATED");
});

test("US-23 tampered value → 401", async () => {
  const signed = await signedBy(TEST_AUTH.sessionSecret, inAnHour());
  const tampered = signed.replace(/^\d+/, String(Date.now() + 365 * 24 * 60 * 60 * 1000));
  assert.equal((await requestWith(tampered)).statusCode, 401);
  assert.equal((await requestWith(inAnHour())).statusCode, 401); // unsigned
});

test("US-23 signed with another secret (SESSION_SECRET rotated) → 401", async () => {
  const response = await requestWith(await signedBy("another-secret-that-is-32-characters-long", inAnHour()));
  assert.equal(response.statusCode, 401);
});

test("US-23 signed but expired → 401 (D-131)", async () => {
  const response = await requestWith(await signedBy(TEST_AUTH.sessionSecret, String(Date.now() - 1)));
  assert.equal(response.statusCode, 401);
});

test("US-23 signed but not a time → 401", async () => {
  const response = await requestWith(await signedBy(TEST_AUTH.sessionSecret, "admin"));
  assert.equal(response.statusCode, 401);
});
