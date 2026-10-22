import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

export const MIGRATIONS_DIR = fileURLToPath(new URL("../../migrations/", import.meta.url));

// Any fixed number: every Nyx process takes the same lock, so two containers starting together take turns
const MIGRATION_LOCK = 7_420_001;

/**
 * Applies every `NNN_name.sql` in `dir` not yet listed in `schema_migrations`, in name order (D-126).
 * Each file runs in its own transaction with its bookkeeping row, so a failing file leaves no trace and
 * stops start-up. Forward only: a mistake is fixed by the next file. Returns the names it applied.
 */
export async function runMigrations(pool, dir = MIGRATIONS_DIR) {
  const client = await pool.connect();
  try {
    await client.query("SELECT pg_advisory_lock($1)", [MIGRATION_LOCK]);
    await client.query(
      "CREATE TABLE IF NOT EXISTS schema_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())",
    );
    const { rows } = await client.query("SELECT name FROM schema_migrations");
    const alreadyApplied = new Set(rows.map((row) => row.name));
    const pending = (await readdir(dir)).filter((name) => name.endsWith(".sql") && !alreadyApplied.has(name)).sort();

    for (const name of pending) {
      const sql = await readFile(join(dir, name), "utf8");
      await client.query("BEGIN");
      try {
        await client.query(sql);
        await client.query("INSERT INTO schema_migrations (name) VALUES ($1)", [name]);
        await client.query("COMMIT");
      } catch (error) {
        await client.query("ROLLBACK");
        throw new Error(`Migration ${name} failed: ${error.message}`, { cause: error });
      }
    }
    return pending;
  } finally {
    // Closing the connection instead of returning it to the pool also releases the advisory lock
    client.release(true);
  }
}
