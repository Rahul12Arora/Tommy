import assert from "node:assert/strict";
import { after, test } from "node:test";
import { testPool } from "../../../testing/testDb.js";
import { buildApp } from "../../app.js";

const pool = testPool();
after(() => pool.end());

test("database answers → 200 { status: ok }", async () => {
  const response = await buildApp({ pool, logLevel: "silent" }).inject({ method: "GET", url: "/health" });
  assert.equal(response.statusCode, 200);
  assert.deepEqual(response.json(), { status: "ok" });
});

test("database unreachable → 503 UNAVAILABLE in the standard shape", async () => {
  const brokenPool = {
    query: async () => {
      throw new Error("connect ECONNREFUSED");
    },
  };
  const response = await buildApp({ pool: brokenPool, logLevel: "silent" }).inject({ method: "GET", url: "/health" });
  assert.equal(response.statusCode, 503);
  assert.deepEqual(response.json(), { error: { code: "UNAVAILABLE", message: "Database unreachable" } });
});
