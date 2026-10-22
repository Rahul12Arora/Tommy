import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import { seedInitialConfig } from "../../../seeds/initial-config.js";
import { loginCookie, TEST_AUTH } from "../../../testing/testAuth.js";
import { emptyDatabase, testPool } from "../../../testing/testDb.js";
import { buildApp } from "../../app.js";
import { runMigrations } from "../../lib/migrate.js";
import { DEFAULT_CONFIG_PATH } from "../../lib/sharedFiles.js";

const pool = testPool();
const app = buildApp({ pool, auth: TEST_AUTH, logLevel: "silent" });
const defaultConfig = JSON.parse(readFileSync(DEFAULT_CONFIG_PATH, "utf8"));
let cookie;

before(async () => {
  await emptyDatabase(pool);
  await runMigrations(pool);
  cookie = await loginCookie(app);
});
beforeEach(async () => {
  await pool.query("TRUNCATE config_versions");
  await seedInitialConfig(pool); // version 1, published
});
after(async () => {
  await app.close();
  await pool.end();
});

const admin = (method, url, payload) =>
  app.inject({
    method,
    url,
    payload,
    headers: payload === undefined ? { cookie } : { cookie, "content-type": "application/json" },
  });
const withSocialDefault = (minutes) => {
  const document = structuredClone(defaultConfig);
  document.limits.categories.social.dailyMinutes.default = minutes;
  return document;
};
const statuses = async () =>
  (await pool.query("SELECT id, status FROM config_versions ORDER BY id")).rows.map((row) => `${row.id}:${row.status}`);

describe("US-23 every admin config route needs a session", () => {
  const routes = [
    ["GET", "/admin/config/published"],
    ["GET", "/admin/config/draft"],
    ["PUT", "/admin/config/draft"],
    ["POST", "/admin/config/publish"],
  ];
  for (const [method, url] of routes) {
    test(`${method} ${url} without a cookie → 401`, async () => {
      const payload = method === "GET" ? undefined : {};
      const response = await app.inject({ method, url, payload });
      assert.equal(response.statusCode, 401);
      assert.equal(response.json().error.code, "UNAUTHENTICATED");
    });
  }
});

describe("US-19 draft and published", () => {
  test("GET published → the live document", async () => {
    const response = await admin("GET", "/admin/config/published");
    assert.equal(response.statusCode, 200);
    assert.deepEqual(response.json(), defaultConfig);
  });

  test("GET draft → 404 when there is none", async () => {
    const response = await admin("GET", "/admin/config/draft");
    assert.equal(response.statusCode, 404);
    assert.equal(response.json().error.code, "NOT_FOUND");
  });

  test("PUT a valid draft → 200; the server sets both version numbers (D-136)", async () => {
    const response = await admin("PUT", "/admin/config/draft", { ...withSocialDefault(45), configVersion: 99 });
    assert.equal(response.statusCode, 200);
    assert.equal(response.json().configVersion, 2);
    assert.equal(response.json().schemaVersion, 1);
    const draft = await admin("GET", "/admin/config/draft");
    assert.equal(draft.json().limits.categories.social.dailyMinutes.default, 45);
    assert.deepEqual(await statuses(), ["1:published", "2:draft"]);
  });

  test("a second PUT updates the same draft", async () => {
    await admin("PUT", "/admin/config/draft", withSocialDefault(45));
    const response = await admin("PUT", "/admin/config/draft", withSocialDefault(50));
    assert.equal(response.json().configVersion, 2);
    assert.deepEqual(await statuses(), ["1:published", "2:draft"]);
    assert.equal((await admin("GET", "/admin/config/draft")).json().limits.categories.social.dailyMinutes.default, 50);
  });

  test("an invalid draft is rejected with every problem as a pointer; nothing is stored", async () => {
    const document = withSocialDefault(500); // max is 240
    document.nags.snoozeMinutes.min = 40; // above its max of 30
    const response = await admin("PUT", "/admin/config/draft", document);
    assert.equal(response.statusCode, 400);
    const { error } = response.json();
    assert.equal(error.code, "VALIDATION_FAILED");
    const paths = error.details.map((detail) => detail.path);
    assert.ok(paths.includes("/limits/categories/social/dailyMinutes"), paths.join(", "));
    assert.ok(paths.includes("/nags/snoozeMinutes"), paths.join(", "));
    for (const detail of error.details) assert.deepEqual(Object.keys(detail).sort(), ["message", "path"]);
    assert.deepEqual(await statuses(), ["1:published"]);
  });

  test("a schema error (a quoted number) is rejected too", async () => {
    const document = withSocialDefault("45");
    const response = await admin("PUT", "/admin/config/draft", document);
    assert.equal(response.statusCode, 400);
    assert.equal(response.json().error.details[0].path, "/limits/categories/social/dailyMinutes/default");
  });

  test("a body that isn't an object → 400", async () => {
    const response = await admin("PUT", "/admin/config/draft", [1, 2]);
    assert.equal(response.statusCode, 400);
  });

  test("not sent as JSON → 415 (D-133)", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/admin/config/publish",
      headers: { cookie, "content-type": "text/plain" },
      payload: "{}",
    });
    assert.equal(response.statusCode, 415);
  });
});

describe("US-19 publish", () => {
  test("the draft goes live: GET /v1/config serves it with the new ETag", async () => {
    await admin("PUT", "/admin/config/draft", withSocialDefault(45));
    const response = await admin("POST", "/admin/config/publish", {});
    assert.equal(response.statusCode, 200);
    assert.deepEqual(response.json(), { configVersion: 2 });

    const live = await app.inject({ method: "GET", url: "/v1/config?schema=1" });
    assert.equal(live.headers.etag, '"2"');
    assert.equal(live.json().limits.categories.social.dailyMinutes.default, 45);
    assert.deepEqual(await statuses(), ["1:superseded", "2:published"]);
    assert.equal((await admin("GET", "/admin/config/draft")).statusCode, 404);
  });

  test("the next draft gets the next number", async () => {
    await admin("PUT", "/admin/config/draft", withSocialDefault(45));
    await admin("POST", "/admin/config/publish", {});
    const response = await admin("PUT", "/admin/config/draft", withSocialDefault(30));
    assert.equal(response.json().configVersion, 3);
  });

  test("no draft → 409 CONFLICT", async () => {
    const response = await admin("POST", "/admin/config/publish", {});
    assert.equal(response.statusCode, 409);
    assert.equal(response.json().error.code, "CONFLICT");
    assert.deepEqual(await statuses(), ["1:published"]);
  });

  test("a stored draft that no longer passes → 400 and nothing changes", async () => {
    const broken = withSocialDefault(500);
    broken.configVersion = 2;
    await pool.query("INSERT INTO config_versions (id, schema_version, status, document) VALUES (2, 1, 'draft', $1)", [
      broken,
    ]);
    const response = await admin("POST", "/admin/config/publish", {});
    assert.equal(response.statusCode, 400);
    assert.equal(response.json().error.code, "VALIDATION_FAILED");
    assert.deepEqual(await statuses(), ["1:published", "2:draft"]);
  });

  test("two publishes at once: one wins, the other finds no draft", async () => {
    await admin("PUT", "/admin/config/draft", withSocialDefault(45));
    const results = await Promise.all([
      admin("POST", "/admin/config/publish", {}),
      admin("POST", "/admin/config/publish", {}),
    ]);
    assert.deepEqual(results.map((response) => response.statusCode).sort(), [200, 409]);
    assert.deepEqual(await statuses(), ["1:superseded", "2:published"]);
  });
});
