import { isSessionValid, SESSION_COOKIE } from "../features/auth/auth.service.js";
import { HttpError } from "../lib/httpError.js";

/** onRequest hook for the admin config routes: a valid signature and an unexpired time inside it (D-131). */
export async function requireAdmin(request) {
  const cookie = request.cookies[SESSION_COOKIE];
  const { valid, value } = cookie ? request.unsignCookie(cookie) : { valid: false };
  if (!valid || !isSessionValid(value, Date.now())) {
    throw new HttpError(401, "UNAUTHENTICATED", "Not logged in, or the session has expired");
  }
}
