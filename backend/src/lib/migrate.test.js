import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, beforeEach, describe, test } from "node:test";
import { emptyDatabase, testPool } from "../../testing/testDb.js";
import { runMigrations } from "./migrate.js";

const pool = testPool();
after(() => pool.end());
beforeEach(() => emptyDatabase(pool));

async function tableExists(name) {
  const { rows } = await pool.query("SELECT to_regclass($1) AS table", [`public.${name}`]);
  return rows[0].table !== null;
}

async function migrationsDir(files) {
  const dir = await mkdtemp(join(tmpdir(), "nyx-migrations-"));
  for (const [name, sql] of Object.entries(files)) await writeFile(join(dir, name), sql);
  return dir;
}

describe("runMigrations", () => {
  test("applies the repo's migrations once; a second run applies nothing", async () => {
    assert.deepEqual(await runMigrations(pool), ["001_init.sql"]);
    assert.ok(await tableExists("config_versions"));
    assert.deepEqual(await runMigrations(pool), []);
  });

  test("applies files in name order, ignoring anything that isn't .sql", async () => {
    const dir = await migrationsDir({
      "002_second.sql": "ALTER TABLE first ADD COLUMN b int;",
      "001_first.sql": "CREATE TABLE first (a int);",
      "README.md": "not a migration",
    });
    try {
      assert.deepEqual(await runMigrations(pool, dir), ["001_first.sql", "002_second.sql"]);
    } finally {
      await rm(dir, { recursive: true });
    }
  });

  test("a failing file rolls back completely, isn't recorded and stops the run", async () => {
    const dir = await migrationsDir({
      "001_ok.sql": "CREATE TABLE ok (a int);",
      "002_bad.sql": "CREATE TABLE half_done (a int); SELECT no_such_column FROM ok;",
      "003_later.sql": "CREATE TABLE later (a int);",
    });
    try {
      await assert.rejects(runMigrations(pool, dir), /Migration 002_bad.sql failed/);
      assert.ok(await tableExists("ok"));
      assert.equal(await tableExists("half_done"), false);
      assert.equal(await tableExists("later"), false);
      const { rows } = await pool.query("SELECT name FROM schema_migrations ORDER BY name");
      assert.deepEqual(
        rows.map((row) => row.name),
        ["001_ok.sql"],
      );
    } finally {
      await rm(dir, { recursive: true });
    }
  });

  test("two runs at once (two containers starting) apply each file exactly once", async () => {
    const results = await Promise.all([runMigrations(pool), runMigrations(pool)]);
    assert.deepEqual(results.flat(), ["001_init.sql"]);
  });
});

describe("001_init: config_versions", () => {
  beforeEach(() => runMigrations(pool));

  const insert = (id, schemaVersion, status, document) =>
    pool.query("INSERT INTO config_versions (id, schema_version, status, document) VALUES ($1, $2, $3, $4)", [
      id,
      schemaVersion,
      status,
      document,
    ]);

  test("a row whose document disagrees on configVersion or schemaVersion is refused", async () => {
    await assert.rejects(insert(1, 1, "published", { configVersion: 2, schemaVersion: 1 }), /check constraint/);
    await assert.rejects(insert(1, 1, "published", { configVersion: 1, schemaVersion: 2 }), /check constraint/);
  });

  test("an unknown status is refused", async () => {
    await assert.rejects(insert(1, 1, "live", { configVersion: 1, schemaVersion: 1 }), /check constraint/);
  });

  test("one published and one draft per schema version; superseded rows are unlimited", async () => {
    await insert(1, 1, "superseded", { configVersion: 1, schemaVersion: 1 });
    await insert(2, 1, "superseded", { configVersion: 2, schemaVersion: 1 });
    await insert(3, 1, "published", { configVersion: 3, schemaVersion: 1 });
    await insert(4, 1, "draft", { configVersion: 4, schemaVersion: 1 });
    await assert.rejects(
      insert(5, 1, "published", { configVersion: 5, schemaVersion: 1 }),
      /config_versions_one_published/,
    );
    await assert.rejects(insert(6, 1, "draft", { configVersion: 6, schemaVersion: 1 }), /config_versions_one_draft/);
    await insert(7, 2, "published", { configVersion: 7, schemaVersion: 2 });
  });
});
