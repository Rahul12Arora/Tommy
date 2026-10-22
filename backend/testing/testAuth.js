import { hashPassword } from "../src/lib/password.js";

export const TEST_USERNAME = "admin";
export const TEST_PASSWORD = "test-password-123";

/** What readEnv would return as `auth`, for buildApp in tests. Hashed once per test file (~0.1 s). */
export const TEST_AUTH = {
  username: TEST_USERNAME,
  passwordHash: await hashPassword(TEST_PASSWORD),
  sessionSecret: "test-session-secret-at-least-32-characters",
  secureCookie: false,
};

/** Logs in through the real route and returns the Cookie header value to send with admin requests. */
export async function loginCookie(app) {
  const response = await app.inject({
    method: "POST",
    url: "/admin/login",
    payload: { username: TEST_USERNAME, password: TEST_PASSWORD },
  });
  if (response.statusCode !== 204) throw new Error(`test login failed: ${response.statusCode} ${response.body}`);
  const { name, value } = response.cookies[0];
  return `${name}=${value}`;
}
