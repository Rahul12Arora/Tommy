import { createPool } from "../src/lib/db.js";

/**
 * Pool on the test database (`nyx_test`, D-129). DB tests need `docker compose up -d db` locally;
 * CI provides a Postgres service. Test files run one at a time, so they can reset it freely.
 */
export function testPool() {
  const url = process.env.TEST_DATABASE_URL;
  if (!url) throw new Error("TEST_DATABASE_URL is not set: copy backend/.env.example to backend/.env");
  return createPool(url, (error) => console.error("test pool: idle connection lost", error));
}

/** Drops every table, so the next `runMigrations` starts from nothing. Test database only. */
export async function emptyDatabase(pool) {
  await pool.query("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
}
