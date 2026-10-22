import { publishedConfig } from "./config.service.js";

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

// If-None-Match compares weakly (RFC 9110): W/"7" matches "7"; a list matches if any entry does
function matchesEtag(ifNoneMatch, etag) {
  if (!ifNoneMatch) return false;
  return ifNoneMatch
    .split(",")
    .map((value) => value.trim().replace(/^W\//, ""))
    .some((value) => value === "*" || value === etag);
}
