import { HttpError } from "../../lib/httpError.js";
import { checkCredentials, newSessionValue, SESSION_COOKIE, sessionCookieOptions } from "./auth.service.js";

const loginBodySchema = {
  type: "object",
  required: ["username", "password"],
  additionalProperties: false,
  properties: {
    username: { type: "string", minLength: 1, maxLength: 200 },
    password: { type: "string", minLength: 1, maxLength: 1000 },
  },
};

/**
 * POST /admin/login and /admin/logout (US-23, ARCHITECTURE §14.11). Registered inside the /admin scope.
 * Every login attempt counts towards the limit, not only failed ones (D-134).
 */
export function authRoutes(admin, auth) {
  admin.post(
    "/login",
    {
      // Fastify's default Ajv drops keys the schema doesn't list (removeAdditional), so extra keys are ignored
      schema: { body: loginBodySchema },
      config: { rateLimit: { max: 5, timeWindow: "1 minute" } },
    },
    async (request, reply) => {
      const { username, password } = request.body;
      if (!(await checkCredentials(auth, username, password))) {
        request.log.warn("admin login failed");
        throw new HttpError(401, "UNAUTHENTICATED", "Wrong username or password");
      }
      request.log.info("admin login");
      reply.setCookie(SESSION_COOKIE, newSessionValue(Date.now()), sessionCookieOptions(auth));
      return reply.code(204).send();
    },
  );

  // No session needed: clearing a cookie can't hurt anyone, and an expired admin still gets a clean logout (D-135)
  admin.post("/logout", async (_request, reply) => {
    reply.clearCookie(SESSION_COOKIE, { path: "/admin" });
    return reply.code(204).send();
  });
}
