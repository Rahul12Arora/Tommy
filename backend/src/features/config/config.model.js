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
