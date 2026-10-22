import { fileURLToPath } from "node:url";

// backend/src/lib → shared/. The image keeps the repo's layout (/app/backend, /app/shared), so this works in both
const SHARED_DIR = fileURLToPath(new URL("../../../shared/", import.meta.url));

export const CONFIG_SCHEMA_PATH = `${SHARED_DIR}config.schema.json`;
export const DEFAULT_CONFIG_PATH = `${SHARED_DIR}default-config.json`;
/** Tests only: the image doesn't include the fixtures. */
export const FIXTURES_DIR = `${SHARED_DIR}fixtures/`;
