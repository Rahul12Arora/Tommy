import pg from "pg";

/** One pool for the whole process. A slow or missing database fails a request in 5 s instead of hanging it. */
export function createPool(databaseUrl) {
  return new pg.Pool({ connectionString: databaseUrl, connectionTimeoutMillis: 5000 });
}
