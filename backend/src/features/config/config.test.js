import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { TEST_AUTH } from "../../../testing/testAuth.js";
import { emptyDatabase, testPool } from "../../../testing/testDb.js";
import { buildApp } from "../../app.js";
import { runMigrations } from "../../lib/migrate.js";
import { DEFAULT_CONFIG_PATH } from "../../lib/sharedFiles.js";

const pool = testPool();
const app = buildApp({ pool, auth: TEST_AUTH, logLevel: "silent" });
const defaultConfig = JSON.parse(readFileSync(DEFAULT_CONFIG_PATH, "utf8"));

before(async () => {
  await emptyDatabase(pool);
  await runMigrations(pool);
});
beforeEach(() => pool.query("TRUNCATE config_versions"));
after(async () => {
  await app.close();
  await pool.end();
});

// Raw SQL text for the document, so a test can store exactly what it wants (e.g. 3.0)
function insert(id, status, documentText, schemaVersion = 1) {
  return pool.query(
    "INSERT INTO config_versions (id, schema_version, status, document, published_at) VALUES ($1, $2, $3, $4::jsonb, now())",
    [id, schemaVersion, status, documentText],
  );
}
const documentText = (version, extra = {}) => JSON.stringify({ ...defaultConfig, configVersion: version, ...extra });
const getConfig = (url, headers = {}) => app.inject({ method: "GET", url, headers });

describe("US-19 GET /v1/config", () => {
  test("200 with the published document, its ETag and no-cache", async () => {
    await insert(1, "published", documentText(1));
    const response = await getConfig("/v1/config?schema=1");
    assert.equal(response.statusCode, 200);
    assert.equal(response.headers.etag, '"1"');
    assert.equal(response.headers["cache-control"], "no-cache");
    assert.match(response.headers["content-type"], /^application\/json/);
    assert.deepEqual(response.json(), defaultConfig);
  });

  test('If-None-Match "1", W/"1", a list containing it, or * → 304 with no body', async () => {
    await insert(1, "published", documentText(1));
    for (const ifNoneMatch of ['"1"', 'W/"1"', '"0", "1"', "*"]) {
      const response = await getConfig("/v1/config?schema=1", { "if-none-match": ifNoneMatch });
      assert.equal(response.statusCode, 304, ifNoneMatch);
      assert.equal(response.body, "", ifNoneMatch);
      assert.equal(response.headers.etag, '"1"', ifNoneMatch);
    }
  });

  test("an older ETag → 200 with the newer document", async () => {
    await insert(6, "superseded", documentText(6));
    await insert(7, "published", documentText(7));
    const response = await getConfig("/v1/config?schema=1", { "if-none-match": '"6"' });
    assert.equal(response.statusCode, 200);
    assert.equal(response.headers.etag, '"7"');
    assert.equal(response.json().configVersion, 7);
  });

  test("drafts and superseded rows are never served", async () => {
    await insert(1, "superseded", documentText(1));
    await insert(2, "draft", documentText(2));
    const response = await getConfig("/v1/config?schema=1");
    assert.equal(response.statusCode, 404);
  });

  test("no published document for that schema version → 404 NOT_FOUND", async () => {
    await insert(1, "published", documentText(1));
    const response = await getConfig("/v1/config?schema=2");
    assert.equal(response.statusCode, 404);
    assert.deepEqual(response.json(), {
      error: { code: "NOT_FOUND", message: "No published config for schema version 2" },
    });
  });

  test("schema missing, not a number, 0 or too big → 400 VALIDATION_FAILED", async () => {
    for (const url of ["/v1/config", "/v1/config?schema=abc", "/v1/config?schema=0", "/v1/config?schema=99999999999"]) {
      const response = await getConfig(url);
      assert.equal(response.statusCode, 400, url);
      const { error } = response.json();
      assert.equal(error.code, "VALIDATION_FAILED", url);
      assert.match(error.details[0].path, /^\/querystring/, url);
    }
  });

  test("D-54: a document stored with 3.0 is served with 3", async () => {
    const text = documentText(1).replace('"maxSnoozesPerDay":3', '"maxSnoozesPerDay":3.0');
    assert.ok(text.includes("3.0"));
    await insert(1, "published", text);
    const stored = await pool.query("SELECT document->'nags'->>'maxSnoozesPerDay' AS value FROM config_versions");
    assert.equal(stored.rows[0].value, "3.0", "Postgres keeps 3.0 as written");

    const response = await getConfig("/v1/config?schema=1");
    assert.match(response.body, /"maxSnoozesPerDay":3[,}]/);
    assert.doesNotMatch(response.body, /3\.0/);
  });

  test("unknown keys reach the phone (D-52)", async () => {
    await insert(1, "published", documentText(1, { futureBlock: { nested: [1, 2] } }));
    const response = await getConfig("/v1/config?schema=1");
    assert.deepEqual(response.json().futureBlock, { nested: [1, 2] });
  });

  test("HEAD works too, with the ETag", async () => {
    await insert(1, "published", documentText(1));
    const response = await app.inject({ method: "HEAD", url: "/v1/config?schema=1" });
    assert.equal(response.statusCode, 200);
    assert.equal(response.headers.etag, '"1"');
  });
});
