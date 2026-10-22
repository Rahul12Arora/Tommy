import assert from "node:assert/strict";
import { after, test } from "node:test";
import { testPool } from "../../testing/testDb.js";
import { createPool } from "./db.js";

const admin = testPool();
after(() => admin.end());

// BUG-05: stopping the database killed the API — a dropped idle connection was an unhandled 'error' event
test("BUG-05: a dropped idle connection is reported, not fatal, and the pool keeps working", async () => {
  const reported = [];
  const pool = createPool(process.env.TEST_DATABASE_URL, (error) => reported.push(error));
  try {
    const client = await pool.connect();
    const { rows } = await client.query("SELECT pg_backend_pid() AS pid");
    client.release();

    await admin.query("SELECT pg_terminate_backend($1)", [rows[0].pid]);
    for (let waited = 0; reported.length === 0 && waited < 3000; waited += 50) {
      await new Promise((resolve) => setTimeout(resolve, 50));
    }

    assert.equal(reported.length, 1);
    assert.match(reported[0].message, /terminating connection/);
    assert.deepEqual((await pool.query("SELECT 1 AS one")).rows, [{ one: 1 }]);
  } finally {
    await pool.end();
  }
});
