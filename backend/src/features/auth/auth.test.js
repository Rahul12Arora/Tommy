import assert from "node:assert/strict";
import { afterEach, describe, test } from "node:test";
import { TEST_AUTH, TEST_PASSWORD, TEST_USERNAME } from "../../../testing/testAuth.js";
import { buildApp } from "../../app.js";

// No database needed: login and logout never touch it
let app;
function newApp(auth = TEST_AUTH) {
  app = buildApp({ pool: null, auth, logLevel: "silent" });
  return app;
}
afterEach(() => app?.close());

const login = (body, headers = {}) => app.inject({ method: "POST", url: "/admin/login", payload: body, headers });

describe("US-23 POST /admin/login", () => {
  test("right username and password → 204 with the session cookie (D-132)", async () => {
    newApp();
    const response = await login({ username: TEST_USERNAME, password: TEST_PASSWORD });
    assert.equal(response.statusCode, 204);
    const setCookie = response.headers["set-cookie"];
    assert.match(setCookie, /^nyx_session=\d+\.[^;]+;/);
    assert.match(setCookie, /; HttpOnly/);
    assert.match(setCookie, /; SameSite=Strict/);
    assert.match(setCookie, /; Path=\/admin/);
    assert.match(setCookie, /; Max-Age=43200/);
    assert.doesNotMatch(setCookie, /Secure/);
  });

  test("the cookie is Secure in production", async () => {
    newApp({ ...TEST_AUTH, secureCookie: true });
    const response = await login({ username: TEST_USERNAME, password: TEST_PASSWORD });
    assert.match(response.headers["set-cookie"], /; Secure/);
  });

  test("wrong password and wrong username → the same 401, no cookie", async () => {
    newApp();
    for (const body of [
      { username: TEST_USERNAME, password: "wrong-password" },
      { username: "root", password: TEST_PASSWORD },
    ]) {
      const response = await login(body);
      assert.equal(response.statusCode, 401);
      assert.deepEqual(response.json(), {
        error: { code: "UNAUTHENTICATED", message: "Wrong username or password" },
      });
      assert.equal(response.headers["set-cookie"], undefined);
    }
  });

  test("missing or empty fields → 400 VALIDATION_FAILED", async () => {
    newApp();
    for (const body of [{ username: TEST_USERNAME }, { username: "", password: "x" }, {}]) {
      const response = await login(body);
      assert.equal(response.statusCode, 400);
      assert.equal(response.json().error.code, "VALIDATION_FAILED");
    }
  });

  test("not sent as JSON → 415 BAD_REQUEST (D-133)", async () => {
    newApp();
    const response = await app.inject({
      method: "POST",
      url: "/admin/login",
      payload: `username=${TEST_USERNAME}&password=${TEST_PASSWORD}`,
      headers: { "content-type": "application/x-www-form-urlencoded" },
    });
    assert.equal(response.statusCode, 415);
    assert.equal(response.json().error.code, "BAD_REQUEST");
  });

  test("the 6th attempt within a minute → 429 RATE_LIMITED, even with the right password (D-134)", async () => {
    newApp();
    for (let attempt = 1; attempt <= 5; attempt++) {
      const response = await login({ username: TEST_USERNAME, password: "wrong-password" });
      assert.equal(response.statusCode, 401, `attempt ${attempt}`);
    }
    const sixth = await login({ username: TEST_USERNAME, password: TEST_PASSWORD });
    assert.equal(sixth.statusCode, 429);
    assert.equal(sixth.json().error.code, "RATE_LIMITED");
    assert.ok(Number(sixth.headers["retry-after"]) > 0);
  });

  test("the limit is per IP", async () => {
    newApp();
    for (let attempt = 1; attempt <= 5; attempt++) {
      await login({ username: TEST_USERNAME, password: "wrong-password" });
    }
    const otherIp = await app.inject({
      method: "POST",
      url: "/admin/login",
      payload: { username: TEST_USERNAME, password: TEST_PASSWORD },
      remoteAddress: "203.0.113.7",
    });
    assert.equal(otherIp.statusCode, 204);
  });
});

describe("US-23 POST /admin/logout", () => {
  test("clears the cookie → 204, with or without a session (D-135)", async () => {
    newApp();
    const response = await app.inject({ method: "POST", url: "/admin/logout", payload: {} });
    assert.equal(response.statusCode, 204);
    const setCookie = response.headers["set-cookie"];
    assert.match(setCookie, /^nyx_session=;/);
    assert.match(setCookie, /; Path=\/admin/);
    assert.match(setCookie, /Expires=Thu, 01 Jan 1970/);
  });

  test("not sent as JSON → 415", async () => {
    newApp();
    const response = await app.inject({ method: "POST", url: "/admin/logout" });
    assert.equal(response.statusCode, 415);
  });
});
