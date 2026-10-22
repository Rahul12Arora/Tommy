import { buildApp } from "./app.js";
import { createPool } from "./lib/db.js";
import { readEnv } from "./lib/env.js";

const env = readEnv(process.env);
const pool = createPool(env.databaseUrl);
const app = buildApp({ logLevel: env.logLevel, trustProxy: env.trustProxy });

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
