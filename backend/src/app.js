import { randomUUID } from "node:crypto";
import Fastify from "fastify";
import { errorHandler, notFoundHandler } from "./middleware/errorHandler.js";

/**
 * Builds the API without starting it, so tests can send requests in-process (ARCHITECTURE §14.10).
 * Fastify's default request serializer logs method, URL, host and remote address only: no headers, no bodies.
 */
export function buildApp({ logLevel = "info", trustProxy = false } = {}) {
  const app = Fastify({
    logger: { level: logLevel },
    // Our own IDs only: a client can't pick the ID its request is logged under
    genReqId: () => randomUUID(),
    requestIdHeader: false,
    trustProxy,
  });

  app.setErrorHandler(errorHandler);
  app.setNotFoundHandler(notFoundHandler);

  return app;
}
