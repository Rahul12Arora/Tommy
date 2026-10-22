import { isPasswordHash } from "./password.js";

const LOG_LEVELS = ["fatal", "error", "warn", "info", "debug", "trace", "silent"];
const NODE_ENVS = ["development", "production"];
const MIN_SECRET_LENGTH = 32;

/**
 * Reads and checks the environment variables (ARCHITECTURE §12, §14.10, §14.11).
 * Throws one error listing every problem, so a bad deploy says everything that's wrong at once.
 */
export function readEnv(env) {
  const problems = [];

  const port = Number(env.PORT ?? "3000");
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    problems.push(`PORT must be an integer 1–65535, got '${env.PORT}'`);
  }

  const databaseUrl = env.DATABASE_URL;
  if (!databaseUrl) problems.push("DATABASE_URL is required");

  const trustProxy = env.TRUST_PROXY ?? "false";
  if (trustProxy !== "true" && trustProxy !== "false") {
    problems.push(`TRUST_PROXY must be 'true' or 'false', got '${trustProxy}'`);
  }

  const logLevel = env.LOG_LEVEL ?? "info";
  if (!LOG_LEVELS.includes(logLevel)) {
    problems.push(`LOG_LEVEL must be one of ${LOG_LEVELS.join(", ")}, got '${logLevel}'`);
  }

  // Required, not defaulted: a production deploy that forgot it would send the admin cookie without Secure (D-137)
  const nodeEnv = env.NODE_ENV;
  if (!NODE_ENVS.includes(nodeEnv)) {
    problems.push(`NODE_ENV must be ${NODE_ENVS.join(" or ")}, got '${nodeEnv ?? ""}'`);
  }

  const adminUsername = env.ADMIN_USERNAME ?? "";
  if (adminUsername.length < 1 || adminUsername.length > 200)
    problems.push("ADMIN_USERNAME is required (1–200 characters)");

  const adminPasswordHash = env.ADMIN_PASSWORD_HASH ?? "";
  if (!isPasswordHash(adminPasswordHash)) {
    problems.push("ADMIN_PASSWORD_HASH must be a hash from `npm run hash-password`");
  }

  const sessionSecret = env.SESSION_SECRET ?? "";
  if (sessionSecret.length < MIN_SECRET_LENGTH) {
    problems.push(`SESSION_SECRET must be at least ${MIN_SECRET_LENGTH} characters`);
  }

  if (problems.length > 0) throw new Error(`Invalid environment: ${problems.join("; ")}`);

  return {
    port,
    databaseUrl,
    trustProxy: trustProxy === "true",
    logLevel,
    auth: {
      username: adminUsername,
      passwordHash: adminPasswordHash,
      sessionSecret,
      secureCookie: nodeEnv === "production",
    },
  };
}
