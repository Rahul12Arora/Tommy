import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, test } from "node:test";
import { TEST_AUTH } from "../testing/testAuth.js";
import { buildApp } from "./app.js";

// Stands in for the console build the image copies to backend/console/ (D-42)
const consoleDir = mkdtempSync(join(tmpdir(), "nyx-console-"));
writeFileSync(join(consoleDir, "index.html"), "<!doctype html><title>Nyx admin</title>");
const app = buildApp({ pool: null, auth: TEST_AUTH, consoleDir, logLevel: "silent" });
after(async () => {
  await app.close();
  rmSync(consoleDir, { recursive: true });
});

test("the built console is served at /console/ with the strict CSP", async () => {
  const response = await app.inject({ method: "GET", url: "/console/" });
  assert.equal(response.statusCode, 200);
  assert.match(response.headers["content-type"], /^text\/html/);
  assert.match(response.body, /Nyx admin/);
  assert.match(response.headers["content-security-policy"], /script-src 'self';/);
  assert.doesNotMatch(response.headers["content-security-policy"], /unsafe-eval/);
});

test("/console redirects to /console/", async () => {
  const response = await app.inject({ method: "GET", url: "/console" });
  assert.equal(response.statusCode, 301);
  assert.equal(response.headers.location, "/console/");
});

test("no console build → no /console route", async () => {
  const withoutConsole = buildApp({ pool: null, auth: TEST_AUTH, logLevel: "silent" });
  const response = await withoutConsole.inject({ method: "GET", url: "/console/" });
  assert.equal(response.statusCode, 404);
  await withoutConsole.close();
});
