import { randomBytes, scrypt, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";

const scryptAsync = promisify(scrypt);

// One of OWASP's scrypt settings: ~32 MiB per check (D-138). Node refuses when 128 × N × r reaches maxmem
const COST = 2 ** 15;
const BLOCK_SIZE = 8;
const PARALLELIZATION = 3;
const MAX_MEMORY = 64 * 1024 * 1024;
const SALT_BYTES = 16;
const KEY_BYTES = 64;

/**
 * `scrypt:N:r:p:<salt>:<key>`, base64url. No `$`, so it pastes into a shell, a `.env` file or a host's dashboard
 * without quoting; the parameters travel with the hash, so they can be raised later without breaking old hashes.
 */
const HASH_FORMAT = /^scrypt:(\d+):(\d+):(\d+):([A-Za-z0-9_-]+):([A-Za-z0-9_-]+)$/;

export async function hashPassword(password) {
  const salt = randomBytes(SALT_BYTES);
  const key = await derive(password, salt, { N: COST, r: BLOCK_SIZE, p: PARALLELIZATION }, KEY_BYTES);
  return ["scrypt", COST, BLOCK_SIZE, PARALLELIZATION, salt.toString("base64url"), key.toString("base64url")].join(":");
}

/** False for a wrong password and for a stored value that isn't a hash we made (start-up rejects those anyway). */
export async function verifyPassword(password, stored) {
  const parsed = parseHash(stored);
  if (!parsed) return false;
  const actual = await derive(password, parsed.salt, parsed.params, parsed.key.length);
  return timingSafeEqual(actual, parsed.key);
}

export function isPasswordHash(stored) {
  return parseHash(stored) !== null;
}

function parseHash(stored) {
  const match = HASH_FORMAT.exec(stored ?? "");
  if (!match) return null;
  const [N, r, p] = match.slice(1, 4).map(Number);
  const salt = Buffer.from(match[4], "base64url");
  const key = Buffer.from(match[5], "base64url");
  // N must be a power of two above 1 for scrypt; anything else is a corrupted or hand-edited value
  if (N < 2 || (N & (N - 1)) !== 0 || r < 1 || p < 1 || salt.length === 0 || key.length === 0) return null;
  return { params: { N, r, p }, salt, key };
}

function derive(password, salt, { N, r, p }, keyLength) {
  return scryptAsync(password, salt, keyLength, { N, r, p, maxmem: MAX_MEMORY });
}
