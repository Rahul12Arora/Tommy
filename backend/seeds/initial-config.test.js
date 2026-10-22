import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { after, beforeEach, test } from "node:test";
import { runMigrations } from "../src/lib/migrate.js";
import { DEFAULT_CONFIG_PATH } from "../src/lib/sharedFiles.js";
import { emptyDatabase, testPool } from "../testing/testDb.js";
import { seedInitialConfig } from "./initial-config.js";

const pool = testPool();
after(() => pool.end());
beforeEach(async () => {
  await emptyDatabase(pool);
  await runMigrations(pool);
});

const allRows = async () =>
  (await pool.query("SELECT id, schema_version, status, document, published_at FROM config_versions ORDER BY id")).rows;

test("empty table → default-config.json published as version 1", async () => {
  assert.equal(await seedInitialConfig(pool), true);
  const rows = await allRows();
  assert.equal(rows.length, 1);
  assert.equal(rows[0].id, 1);
  assert.equal(rows[0].schema_version, 1);
  assert.equal(rows[0].status, "published");
  assert.ok(rows[0].published_at instanceof Date);
  assert.deepEqual(rows[0].document, JSON.parse(readFileSync(DEFAULT_CONFIG_PATH, "utf8")));
});

test("second run → no change", async () => {
  await seedInitialConfig(pool);
  assert.equal(await seedInitialConfig(pool), false);
  assert.equal((await allRows()).length, 1);
});

test("two runs at once → one row, no error", async () => {
  const results = await Promise.all([seedInitialConfig(pool), seedInitialConfig(pool)]);
  assert.deepEqual(results.toSorted(), [false, true]);
  assert.equal((await allRows()).length, 1);
});

test("a table that already has rows is left alone, even without a published one", async () => {
  await pool.query("INSERT INTO config_versions (id, schema_version, status, document) VALUES (5, 1, 'draft', $1)", [
    { configVersion: 5, schemaVersion: 1 },
  ]);
  assert.equal(await seedInitialConfig(pool), false);
  assert.deepEqual(
    (await allRows()).map((row) => row.id),
    [5],
  );
});
