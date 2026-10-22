import assert from "node:assert/strict";
import { before, test } from "node:test";
import { readEnv } from "./env.js";
import { hashPassword } from "./password.js";

let required;
before(async () => {
  required = {
    DATABASE_URL: "postgres://x",
    NODE_ENV: "development",
    ADMIN_USERNAME: "admin",
    ADMIN_PASSWORD_HASH: await hashPassword("dev-password-123"),
    SESSION_SECRET: "s".repeat(32),
  };
});

test("only the required variables set → defaults for the rest", () => {
  assert.deepEqual(readEnv(required), {
    port: 3000,
    databaseUrl: "postgres://x",
    trustProxy: false,
    logLevel: "info",
    auth: {
      username: "admin",
      passwordHash: required.ADMIN_PASSWORD_HASH,
      sessionSecret: "s".repeat(32),
      secureCookie: false,
    },
  });
});

test("every value read when set", () => {
  const env = readEnv({ ...required, PORT: "8080", TRUST_PROXY: "true", LOG_LEVEL: "debug", NODE_ENV: "production" });
  assert.equal(env.port, 8080);
  assert.equal(env.trustProxy, true);
  assert.equal(env.logLevel, "debug");
  assert.equal(env.auth.secureCookie, true);
});

test("every bad or missing value is reported together", () => {
  assert.throws(
    () => readEnv({ PORT: "0", TRUST_PROXY: "yes", LOG_LEVEL: "loud" }),
    (error) => {
      for (const name of [
        "PORT",
        "DATABASE_URL",
        "TRUST_PROXY",
        "LOG_LEVEL",
        "NODE_ENV",
        "ADMIN_USERNAME",
        "ADMIN_PASSWORD_HASH",
        "SESSION_SECRET",
      ]) {
        assert.match(error.message, new RegExp(name));
      }
      return true;
    },
  );
});

test("PORT must be a whole number in range", () => {
  for (const port of ["abc", "3000.5", "65536"]) {
    assert.throws(() => readEnv({ ...required, PORT: port }), /PORT must be/);
  }
});

test("NODE_ENV must be development or production (D-137)", () => {
  for (const nodeEnv of ["test", "prod", ""]) {
    assert.throws(() => readEnv({ ...required, NODE_ENV: nodeEnv }), /NODE_ENV must be/);
  }
});

test("a short SESSION_SECRET and a malformed hash are rejected", () => {
  assert.throws(() => readEnv({ ...required, SESSION_SECRET: "s".repeat(31) }), /SESSION_SECRET must be/);
  assert.throws(() => readEnv({ ...required, ADMIN_PASSWORD_HASH: "plain-password" }), /ADMIN_PASSWORD_HASH must be/);
  assert.throws(() => readEnv({ ...required, ADMIN_USERNAME: "a".repeat(201) }), /ADMIN_USERNAME is required/);
});
