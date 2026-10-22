import { createHash, timingSafeEqual } from "node:crypto";
import { verifyPassword } from "../../lib/password.js";

export const SESSION_COOKIE = "nyx_session";
const SESSION_SECONDS = 12 * 60 * 60;

/** D-132: never sent to /v1, /health or the console files; Secure only in production (dev is plain http://localhost). */
export function sessionCookieOptions(auth) {
  return {
    path: "/admin",
    httpOnly: true,
    sameSite: "strict",
    secure: auth.secureCookie,
    maxAge: SESSION_SECONDS,
    signed: true,
  };
}

/**
 * Both checks always run, so the response time doesn't tell whether the username or the password was wrong.
 * The usernames are compared as SHA-256 digests: equal lengths, so timingSafeEqual neither throws nor leaks length.
 */
export async function checkCredentials(auth, username, password) {
  const usernameMatches = timingSafeEqual(sha256(username), sha256(auth.username));
  const passwordMatches = await verifyPassword(password, auth.passwordHash);
  return usernameMatches && passwordMatches;
}

/** The cookie holds only its expiry time; @fastify/cookie signs it with SESSION_SECRET (D-131). */
export function newSessionValue(now) {
  return String(now + SESSION_SECONDS * 1000);
}

/** Max-Age is only an instruction to the browser: a copied cookie stops working once the time inside it passes. */
export function isSessionValid(value, now) {
  const expiresAt = Number(value);
  return Number.isSafeInteger(expiresAt) && expiresAt > now;
}

function sha256(text) {
  return createHash("sha256").update(text).digest();
}
