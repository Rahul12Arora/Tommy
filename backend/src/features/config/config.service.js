import { readFileSync } from "node:fs";
import { withTransaction } from "../../lib/db.js";
import { HttpError } from "../../lib/httpError.js";
import { CONFIG_SCHEMA_PATH } from "../../lib/sharedFiles.js";
import {
  findDraft,
  findPublished,
  insertDraft,
  lockConfigWrites,
  markPublished,
  nextVersionId,
  supersedePublished,
  updateDraft,
} from "./config.model.js";
import { validateConfig } from "./config.validation.js";

/** The schema version this backend edits: `schemaVersion.const` in shared/config.schema.json (D-136). */
export const CURRENT_SCHEMA_VERSION = JSON.parse(readFileSync(CONFIG_SCHEMA_PATH, "utf8")).properties.schemaVersion
  .const;

/** The live document for a schema version, or 404 — the phone then keeps its last good config (US-21). */
export async function publishedConfig(pool, schemaVersion) {
  const row = await findPublished(pool, schemaVersion);
  if (!row) throw new HttpError(404, "NOT_FOUND", `No published config for schema version ${schemaVersion}`);
  return { version: row.id, document: row.document };
}

/** 404 when there's no draft: the console then starts from the published document. */
export async function draftConfig(pool) {
  const row = await findDraft(pool, CURRENT_SCHEMA_VERSION);
  if (!row) throw new HttpError(404, "NOT_FOUND", "No draft");
  return row.document;
}

/**
 * Replaces the draft with `document` (US-19). The version numbers belong to the server: the existing draft's id,
 * or the next unused one. Validated like the phone validates; nothing is written when it fails.
 */
export async function saveDraft(pool, document) {
  return withTransaction(pool, async (client) => {
    await lockConfigWrites(client);
    const existing = await findDraft(client, CURRENT_SCHEMA_VERSION);
    const versioned = {
      ...document,
      schemaVersion: CURRENT_SCHEMA_VERSION,
      configVersion: existing ? existing.id : await nextVersionId(client),
    };
    throwIfInvalid(versioned);
    await (existing ? updateDraft(client, versioned) : insertDraft(client, versioned));
    return versioned;
  });
}

/**
 * The draft becomes the live version; the old one is kept as superseded. Validated again, since a deploy may
 * have tightened the rules after the draft was saved. Returns the new version number.
 */
export async function publishDraft(pool) {
  return withTransaction(pool, async (client) => {
    await lockConfigWrites(client);
    const draft = await findDraft(client, CURRENT_SCHEMA_VERSION);
    if (!draft) throw new HttpError(409, "CONFLICT", "There is no draft to publish");
    throwIfInvalid(draft.document);
    // Supersede first: at most one published row per schema version (unique index)
    await supersedePublished(client, CURRENT_SCHEMA_VERSION);
    await markPublished(client, draft.id);
    return draft.id;
  });
}

function throwIfInvalid(document) {
  const errors = validateConfig(document);
  if (errors.length > 0) {
    const details = errors.map(({ path, message }) => ({ path, message }));
    throw new HttpError(400, "VALIDATION_FAILED", "Config is invalid", details);
  }
}
