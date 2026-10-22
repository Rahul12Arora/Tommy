import { HttpError } from "../../lib/httpError.js";

/** GET /health for the hosting platform: the process is up and the database answers within 5 s. */
export function healthRoutes(app, pool) {
  app.get("/health", async (request) => {
    try {
      await pool.query({ text: "SELECT 1", query_timeout: 5000 });
    } catch (error) {
      request.log.warn({ err: error }, "health check: database unreachable");
      throw new HttpError(503, "UNAVAILABLE", "Database unreachable");
    }
    return { status: "ok" };
  });
}
