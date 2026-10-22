import { HttpError } from "../../lib/httpError.js";
import { findPublished } from "./config.model.js";

/** The live document for a schema version, or 404 — the phone then keeps its last good config (US-21). */
export async function publishedConfig(pool, schemaVersion) {
  const row = await findPublished(pool, schemaVersion);
  if (!row) throw new HttpError(404, "NOT_FOUND", `No published config for schema version ${schemaVersion}`);
  return { version: row.id, document: row.document };
}
