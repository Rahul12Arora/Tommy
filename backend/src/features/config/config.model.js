/** The SQL for `config_versions` (ARCHITECTURE §14.10). Parameterised queries only. */

export async function findPublished(pool, schemaVersion) {
  const { rows } = await pool.query(
    "SELECT id, document FROM config_versions WHERE schema_version = $1 AND status = 'published'",
    [schemaVersion],
  );
  return rows[0];
}

/**
 * Inserts `document` as its own version, published, but only into an empty table. Returns whether it did.
 * ON CONFLICT covers two containers starting at once: the second insert is skipped, not an error.
 */
export async function insertFirstPublished(pool, document) {
  const { rowCount } = await pool.query(
    `INSERT INTO config_versions (id, schema_version, status, document, published_at)
     SELECT $1, $2, 'published', $3, now()
     WHERE NOT EXISTS (SELECT 1 FROM config_versions)
     ON CONFLICT DO NOTHING`,
    [document.configVersion, document.schemaVersion, document],
  );
  return rowCount === 1;
}

// Any fixed number, different from the migration lock: draft saves and publishes take turns (D-136)
const CONFIG_WRITE_LOCK = 7_420_002;

/** Held until the transaction ends. Every draft save and publish takes it first. */
export async function lockConfigWrites(client) {
  await client.query("SELECT pg_advisory_xact_lock($1)", [CONFIG_WRITE_LOCK]);
}

export async function findDraft(db, schemaVersion) {
  const { rows } = await db.query(
    "SELECT id, document FROM config_versions WHERE schema_version = $1 AND status = 'draft'",
    [schemaVersion],
  );
  return rows[0];
}

/** Over every row, superseded ones included, so a version number is never reused. */
export async function nextVersionId(client) {
  const { rows } = await client.query("SELECT COALESCE(MAX(id), 0) + 1 AS id FROM config_versions");
  return rows[0].id;
}

export async function insertDraft(client, document) {
  await client.query(
    "INSERT INTO config_versions (id, schema_version, status, document) VALUES ($1, $2, 'draft', $3)",
    [document.configVersion, document.schemaVersion, document],
  );
}

export async function updateDraft(client, document) {
  await client.query("UPDATE config_versions SET document = $2 WHERE id = $1 AND status = 'draft'", [
    document.configVersion,
    document,
  ]);
}

export async function supersedePublished(client, schemaVersion) {
  await client.query(
    "UPDATE config_versions SET status = 'superseded' WHERE schema_version = $1 AND status = 'published'",
    [schemaVersion],
  );
}

export async function markPublished(client, id) {
  await client.query("UPDATE config_versions SET status = 'published', published_at = now() WHERE id = $1", [id]);
}
