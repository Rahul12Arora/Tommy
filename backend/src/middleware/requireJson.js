import { HttpError } from "../lib/httpError.js";

/**
 * CSRF guard for everything under /admin (D-133), on top of the SameSite=Strict cookie: an HTML form can't send
 * application/json, and cross-origin fetch can't either without a CORS preflight, which fails (no CORS, ever).
 * Covers bodyless actions too: the console sends `{}` with logout and publish.
 */
export async function requireJson(request) {
  if (request.method === "GET" || request.method === "HEAD") return;
  const mediaType = (request.headers["content-type"] ?? "").split(";")[0].trim().toLowerCase();
  if (mediaType !== "application/json") {
    throw new HttpError(415, "BAD_REQUEST", "Admin requests must be sent as application/json");
  }
}
