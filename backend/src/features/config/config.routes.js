import { CURRENT_SCHEMA_VERSION, draftConfig, publishDraft, publishedConfig, saveDraft } from "./config.service.js";

const querySchema = {
  type: "object",
  required: ["schema"],
  // Up to Postgres' integer limit, so a huge number is a 400, not a database error
  properties: { schema: { type: "integer", minimum: 1, maximum: 2147483647 } },
};

/**
 * GET /v1/config?schema=<n> (US-19, D-124). Public, no identifiers.
 * No response schema on purpose: Fastify's serializer would drop keys the schema doesn't list, and unknown
 * keys must reach phones (D-52). The document comes out of jsonb re-serialised, so 3.0 is sent as 3 (D-54).
 */
export function configRoutes(app, pool) {
  app.get("/v1/config", { schema: { querystring: querySchema } }, async (request, reply) => {
    const { version, document } = await publishedConfig(pool, request.query.schema);
    const etag = `"${version}"`;
    reply.header("etag", etag).header("cache-control", "no-cache");
    if (matchesEtag(request.headers["if-none-match"], etag)) return reply.code(304).send();
    return document;
  });
}

/**
 * The console's config routes (US-19, US-20, ARCHITECTURE §14.11), registered in the scope behind requireAdmin.
 * No response schemas, for the same reason as /v1/config. Saves and publishes are logged: v1's audit trail.
 */
export function adminConfigRoutes(signedIn, pool) {
  signedIn.get("/config/published", async () => {
    const { document } = await publishedConfig(pool, CURRENT_SCHEMA_VERSION);
    return document;
  });

  signedIn.get("/config/draft", () => draftConfig(pool));

  // Only "is it an object" here; validateConfig reports everything else with JSON Pointers, like the phone
  signedIn.put("/config/draft", { schema: { body: { type: "object" } } }, async (request) => {
    const saved = await saveDraft(pool, request.body);
    request.log.info({ version: saved.configVersion }, "config draft saved");
    return saved;
  });

  signedIn.post("/config/publish", async (request) => {
    const configVersion = await publishDraft(pool);
    request.log.info({ version: configVersion }, "config published");
    return { configVersion };
  });
}

// If-None-Match compares weakly (RFC 9110): W/"7" matches "7"; a list matches if any entry does
function matchesEtag(ifNoneMatch, etag) {
  if (!ifNoneMatch) return false;
  return ifNoneMatch
    .split(",")
    .map((value) => value.trim().replace(/^W\//, ""))
    .some((value) => value === "*" || value === etag);
}
