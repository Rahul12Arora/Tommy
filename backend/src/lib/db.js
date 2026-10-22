import pg from "pg";

/**
 * One pool for the whole process. A slow or missing database fails a request in 5 s instead of hanging it.
 * `onIdleError` gets connections that drop while idle (database restart, network blip). Without a listener that's
 * an unhandled 'error' event and the whole process dies (BUG-05); the pool replaces the connection by itself.
 */
export function createPool(databaseUrl, onIdleError) {
  const pool = new pg.Pool({ connectionString: databaseUrl, connectionTimeoutMillis: 5000 });
  pool.on("error", onIdleError);
  return pool;
}
