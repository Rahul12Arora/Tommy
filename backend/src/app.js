import { randomUUID } from "node:crypto";
import cookie from "@fastify/cookie";
import helmet from "@fastify/helmet";
import rateLimit from "@fastify/rate-limit";
import fastifyStatic from "@fastify/static";
import Fastify from "fastify";
import { authRoutes } from "./features/auth/auth.routes.js";
import { adminConfigRoutes, configRoutes } from "./features/config/config.routes.js";
import { healthRoutes } from "./features/health/health.routes.js";
import { errorHandler, notFoundHandler } from "./middleware/errorHandler.js";
import { requireAdmin } from "./middleware/requireAdmin.js";
import { requireJson } from "./middleware/requireJson.js";

/**
 * Builds the API without starting it, so tests can send requests in-process (ARCHITECTURE §14.10, §14.11).
 * Fastify's default request serializer logs method, URL, host and remote address only: no headers, no bodies,
 * so no cookies or passwords reach the logs.
 * `auth` = { username, passwordHash, sessionSecret, secureCookie } from readEnv; tests use testing/testAuth.js.
 * `consoleDir`: the built console (only in the image, D-42); without it the console runs on Vite's dev server.
 */
export function buildApp({ pool, auth, consoleDir, logLevel = "info", trustProxy = false }) {
  const app = Fastify({
    logger: { level: logLevel },
    // Our own IDs only: a client can't pick the ID its request is logged under
    genReqId: () => randomUUID(),
    requestIdHeader: false,
    trustProxy,
  });

  app.setErrorHandler(errorHandler);
  app.setNotFoundHandler(notFoundHandler);

  app.register(helmet);
  app.register(cookie, { secret: auth.sessionSecret });
  // Only routes that opt in with config.rateLimit: login now, stats in M14
  app.register(rateLimit, { global: false });

  healthRoutes(app, pool);
  configRoutes(app, pool);

  // Scopes keep these hooks off /v1 and /health; onRequest runs before the body is read
  app.register(
    async (admin) => {
      admin.addHook("onRequest", requireJson);
      authRoutes(admin, auth);
      admin.register(async (signedIn) => {
        signedIn.addHook("onRequest", requireAdmin);
        adminConfigRoutes(signedIn, pool);
      });
    },
    { prefix: "/admin" },
  );

  if (consoleDir) {
    app.register(fastifyStatic, { root: consoleDir, prefix: "/console/" });
    // The prefix route doesn't match the bare path; index.html sits at /console/
    app.get("/console", (_request, reply) => reply.redirect("/console/", 301));
  }

  return app;
}
