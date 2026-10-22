import { seedInitialConfig } from "../seeds/initial-config.js";
import { buildApp } from "./app.js";
import { createPool } from "./lib/db.js";
import { readEnv } from "./lib/env.js";
import { runMigrations } from "./lib/migrate.js";

const env = readEnv(process.env);
// The pool is made before the app (which needs it), so its error handler reaches the app's logger through a closure
const pool = createPool(env.databaseUrl, (error) => app.log.error({ err: error }, "idle database connection lost"));
const app = buildApp({ pool, auth: env.auth, logLevel: env.logLevel, trustProxy: env.trustProxy });

// Before listening, so no request ever sees an old schema (D-126)
const applied = await runMigrations(pool);
app.log.info({ applied }, "migrations done");
const seeded = await seedInitialConfig(pool);
app.log.info({ seeded }, "initial config checked");

// The container stops us with SIGTERM: finish open requests, then release the database connections
for (const signal of ["SIGTERM", "SIGINT"]) {
  process.once(signal, async () => {
    app.log.info({ signal }, "shutting down");
    await app.close();
    await pool.end();
    process.exit(0);
  });
}

// 0.0.0.0, not localhost: the port has to be reachable from outside the container
await app.listen({ host: "0.0.0.0", port: env.port });
