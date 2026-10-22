-- Every config document: drafts, the live one and older ones (ARCHITECTURE §6, §14.10)
CREATE TABLE config_versions (
  id             integer PRIMARY KEY CHECK (id >= 1),          -- = configVersion = the ETag
  schema_version integer NOT NULL CHECK (schema_version >= 1),
  status         text    NOT NULL CHECK (status IN ('draft', 'published', 'superseded')),
  document       jsonb   NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now(),
  published_at   timestamptz,
  -- The row and the document it holds can't disagree about the version phones see
  CHECK ((document->>'configVersion')::integer = id),
  CHECK ((document->>'schemaVersion')::integer = schema_version)
);

-- At most one live and one draft document per schema version; the first also serves GET /v1/config
CREATE UNIQUE INDEX config_versions_one_published ON config_versions (schema_version) WHERE status = 'published';
CREATE UNIQUE INDEX config_versions_one_draft ON config_versions (schema_version) WHERE status = 'draft';
