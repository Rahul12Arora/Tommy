const LOG_LEVELS = ["fatal", "error", "warn", "info", "debug", "trace", "silent"];

/**
 * Reads and checks the environment variables (ARCHITECTURE §12, §14.10).
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

  if (problems.length > 0) throw new Error(`Invalid environment: ${problems.join("; ")}`);

  return { port, databaseUrl, trustProxy: trustProxy === "true", logLevel };
}
