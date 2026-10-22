import assert from "node:assert/strict";
import { test } from "node:test";
import { readEnv } from "./env.js";

test("only DATABASE_URL set → defaults for the rest", () => {
  assert.deepEqual(readEnv({ DATABASE_URL: "postgres://x" }), {
    port: 3000,
    databaseUrl: "postgres://x",
    trustProxy: false,
    logLevel: "info",
  });
});

test("every value read when set", () => {
  const env = readEnv({ PORT: "8080", DATABASE_URL: "postgres://x", TRUST_PROXY: "true", LOG_LEVEL: "debug" });
  assert.deepEqual(env, { port: 8080, databaseUrl: "postgres://x", trustProxy: true, logLevel: "debug" });
});

test("every bad value is reported together", () => {
  assert.throws(
    () => readEnv({ PORT: "0", TRUST_PROXY: "yes", LOG_LEVEL: "loud" }),
    (error) => {
      assert.match(error.message, /PORT must be/);
      assert.match(error.message, /DATABASE_URL is required/);
      assert.match(error.message, /TRUST_PROXY must be/);
      assert.match(error.message, /LOG_LEVEL must be/);
      return true;
    },
  );
});

test("PORT must be a whole number in range", () => {
  for (const port of ["abc", "3000.5", "65536"]) {
    assert.throws(() => readEnv({ PORT: port, DATABASE_URL: "postgres://x" }), /PORT must be/);
  }
});
