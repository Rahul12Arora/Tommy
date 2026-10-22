import { HttpError } from "../lib/httpError.js";

// Fastify's own 4xx errors that have a code of their own in ARCHITECTURE §7; any other 4xx is BAD_REQUEST (D-127)
const CODE_FOR_STATUS = { 404: "NOT_FOUND", 429: "RATE_LIMITED" };

function send(reply, statusCode, code, message, details) {
  const error = details ? { code, message, details } : { code, message };
  return reply.code(statusCode).send({ error });
}

/** Turns every error into `{ error: { code, message, details? } }`. Internal details never leave the server. */
export function errorHandler(error, request, reply) {
  if (error instanceof HttpError) {
    return send(reply, error.statusCode, error.code, error.message, error.details);
  }

  if (error.validation) {
    const details = error.validation.map((issue) => ({
      path: `/${error.validationContext}${issue.instancePath}`,
      message: issue.message,
    }));
    return send(reply, 400, "VALIDATION_FAILED", "Request is invalid", details);
  }

  const statusCode = error.statusCode ?? 500;
  if (statusCode >= 400 && statusCode < 500) {
    return send(reply, statusCode, CODE_FOR_STATUS[statusCode] ?? "BAD_REQUEST", error.message);
  }

  request.log.error({ err: error }, "unhandled error");
  return send(reply, 500, "INTERNAL", "Something went wrong");
}

export function notFoundHandler(_request, reply) {
  return send(reply, 404, "NOT_FOUND", "No such route");
}
