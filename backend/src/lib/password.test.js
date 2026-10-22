import assert from "node:assert/strict";
import { test } from "node:test";
import { hashPassword, isPasswordHash, verifyPassword } from "./password.js";

test("a hash verifies its own password and nothing else", async () => {
  const hash = await hashPassword("correct horse battery");
  assert.equal(await verifyPassword("correct horse battery", hash), true);
  assert.equal(await verifyPassword("correct horse batterY", hash), false);
  assert.equal(await verifyPassword("", hash), false);
});

test("the format is scrypt:N:r:p:salt:key with D-138's parameters", async () => {
  const hash = await hashPassword("correct horse battery");
  assert.match(hash, /^scrypt:32768:8:3:[A-Za-z0-9_-]{22}:[A-Za-z0-9_-]{86}$/);
  assert.equal(isPasswordHash(hash), true);
});

test("two hashes of one password differ (random salt)", async () => {
  assert.notEqual(await hashPassword("same password"), await hashPassword("same password"));
});

test("malformed stored values are not hashes and never verify", async () => {
  const good = await hashPassword("pw-123456789");
  const [, N, r, p, salt, key] = good.split(":");
  const malformed = [
    undefined,
    "",
    "pw-123456789",
    `bcrypt:${N}:${r}:${p}:${salt}:${key}`,
    `scrypt:abc:${r}:${p}:${salt}:${key}`,
    `scrypt:1000:${r}:${p}:${salt}:${key}`, // not a power of two
    `scrypt:${N}:0:${p}:${salt}:${key}`,
    `scrypt:${N}:${r}:${p}:${salt}`,
    `scrypt$${N}$${r}$${p}$${salt}$${key}`,
  ];
  for (const stored of malformed) {
    assert.equal(isPasswordHash(stored), false, String(stored));
    assert.equal(await verifyPassword("pw-123456789", stored), false, String(stored));
  }
});
