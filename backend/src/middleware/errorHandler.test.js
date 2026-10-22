import assert from "node:assert/strict";
import { test } from "node:test";
import { TEST_AUTH } from "../../testing/testAuth.js";
import { buildApp } from "../app.js";
import { HttpError } from "../lib/httpError.js";

function testApp() {
  const app = buildApp({ auth: TEST_AUTH, logLevel: "silent" });
  app.get("/boom", async () => {
    throw new Error("secret detail");
  });
  app.get("/conflict", async () => {
    throw new HttpError(409, "CONFLICT", "Draft changed", [{ path: "/draft", message: "stale" }]);
  });
  app.get(
    "/needs-number",
    { schema: { querystring: { type: "object", required: ["n"], properties: { n: { type: "integer" } } } } },
    async () => ({ ok: true }),
  );
  app.post("/echo", async (request) => request.body);
  return app;
}

test("unknown route → 404 NOT_FOUND in the standard shape", async () => {
  const response = await testApp().inject({ method: "GET", url: "/nope" });
  assert.equal(response.statusCode, 404);
  assert.deepEqual(response.json(), { error: { code: "NOT_FOUND", message: "No such route" } });
});

test("unexpected error → 500 INTERNAL without its message", async () => {
  const response = await testApp().inject({ method: "GET", url: "/boom" });
  assert.equal(response.statusCode, 500);
  assert.deepEqual(response.json(), { error: { code: "INTERNAL", message: "Something went wrong" } });
  assert.doesNotMatch(response.body, /secret detail/);
});

test("HttpError → its own status, code, message and details", async () => {
  const response = await testApp().inject({ method: "GET", url: "/conflict" });
  assert.equal(response.statusCode, 409);
  assert.deepEqual(response.json(), {
    error: { code: "CONFLICT", message: "Draft changed", details: [{ path: "/draft", message: "stale" }] },
  });
});

test("route schema failure → 400 VALIDATION_FAILED with a pointer per problem", async () => {
  const response = await testApp().inject({ method: "GET", url: "/needs-number?n=abc" });
  assert.equal(response.statusCode, 400);
  const { error } = response.json();
  assert.equal(error.code, "VALIDATION_FAILED");
  assert.equal(error.details.length, 1);
  assert.equal(error.details[0].path, "/querystring/n");
});

test("other Fastify 4xx (bad JSON body) → BAD_REQUEST with Fastify's status", async () => {
  const response = await testApp().inject({
    method: "POST",
    url: "/echo",
    headers: { "content-type": "application/json" },
    payload: "{not json",
  });
  assert.equal(response.statusCode, 400);
  assert.equal(response.json().error.code, "BAD_REQUEST");
});

test("unsupported content type → 415 BAD_REQUEST", async () => {
  const response = await testApp().inject({
    method: "POST",
    url: "/echo",
    headers: { "content-type": "application/xml" },
    payload: "<a/>",
  });
  assert.equal(response.statusCode, 415);
  assert.equal(response.json().error.code, "BAD_REQUEST");
});
