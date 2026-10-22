import { readFile } from "node:fs/promises";
import { insertFirstPublished } from "../src/features/config/config.model.js";
import { parseConfig } from "../src/features/config/config.validation.js";
import { DEFAULT_CONFIG_PATH } from "../src/lib/sharedFiles.js";

/**
 * Publishes shared/default-config.json as version 1 when there's no config at all (D-43, D-126).
 * Runs at every start-up, in every environment; it never touches a table that already has rows.
 * Returns whether it inserted.
 */
export async function seedInitialConfig(pool) {
  const { document, errors } = parseConfig(await readFile(DEFAULT_CONFIG_PATH, "utf8"));
  if (errors.length > 0) {
    throw new Error(`shared/default-config.json is invalid: ${JSON.stringify(errors)}`);
  }
  return insertFirstPublished(pool, document);
}
