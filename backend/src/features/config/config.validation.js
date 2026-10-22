import { readFileSync } from "node:fs";
import Ajv from "ajv";
import { CONFIG_SCHEMA_PATH } from "../../lib/sharedFiles.js";

/** Rule IDs, the same as the phone's `ConfigRule` and the invalid fixtures' file names (D-53, ARCHITECTURE §14.2). */
export const RULE = {
  SCHEMA: "schema",
  SETTING_RANGE: "setting-range",
  TICK_ORDER: "tick-order",
  DUPLICATE_ID: "duplicate-id",
  MOOD_BANDS_COVERAGE: "mood-bands-coverage",
  PET_FACES: "pet-faces",
  LINE_REFERENCES: "line-references",
  LINE_COVERAGE: "line-coverage",
  LINE_PLACEHOLDERS: "line-placeholders",
  NOTHING_ENABLED: "nothing-enabled",
};

/**
 * The triggers that show a bubble and the placeholders their lines may use: a copy of the phone's
 * `BubbleTrigger.kt` (§14.6). The fixture tests on both sides catch the two drifting apart.
 */
export const BUBBLE_TRIGGERS = {
  limit_reached: ["app", "category", "limit"],
  limit_repeat: ["app", "category", "limit"],
  stretch_reached: ["app", "minutes"],
  break_due: ["minutes"],
  break_taken: [],
  goal_met: [],
  focus_started: [],
  focus_drift: ["app"],
  focus_checkin: ["minutes"],
  focus_completed: [],
  focus_broken: [],
  idle: [],
  pet_returned: [],
};

const ANY_POOL = "any";
const MOOD_MAX = 100;
// Anything in braces, so a typo such as {App} is caught as well as an unknown name
const PLACEHOLDER = /\{([^{}]*)\}/g;

// Ajv's defaults otherwise: no type coercion ("3" stays wrong, D-54), no default filling, nothing removed (D-121)
const checkSchema = new Ajv({ allErrors: true }).compile(JSON.parse(readFileSync(CONFIG_SCHEMA_PATH, "utf8")));

/** Parses and validates a document's text. Never throws for a bad document. */
export function parseConfig(text) {
  let document;
  try {
    document = JSON.parse(text);
  } catch (error) {
    return { document: undefined, errors: [{ rule: RULE.SCHEMA, path: "/", message: error.message }] };
  }
  return { document, errors: validateConfig(document) };
}

/**
 * Every problem with a parsed document as `{ rule, path, message }`, path a JSON Pointer (D-123).
 * The schema first; the cross-field rules only once it passes, so they can trust the types (D-122).
 */
export function validateConfig(document) {
  if (!checkSchema(document)) {
    return checkSchema.errors.map((error) => ({
      rule: RULE.SCHEMA,
      path: error.instancePath || "/",
      message: error.message,
    }));
  }
  const errors = [];
  const fail = (rule, path, message) => errors.push({ rule, path, message });
  checkSettings(document, fail);
  checkMood(document.mood, fail);
  checkPetsAndPersonalities(document, fail);
  checkLines(document, fail);
  return errors;
}

function pointer(...parts) {
  return `/${parts.map((part) => String(part).replaceAll("~", "~0").replaceAll("/", "~1")).join("/")}`;
}

// The phone's SettingPaths: every { default, min, max, userEditable } in the document
function settingsOf(document) {
  const settings = [[["day", "resetHour"], document.day.resetHour]];
  for (const [category, limit] of Object.entries(document.limits.categories)) {
    settings.push([["limits", "categories", category, "dailyMinutes"], limit.dailyMinutes]);
  }
  settings.push(
    [["limits", "totalDailyMinutes"], document.limits.totalDailyMinutes],
    [["limits", "stretch", "minutes"], document.limits.stretch.minutes],
    [["nags", "repeatMinutes"], document.nags.repeatMinutes],
    [["nags", "snoozeMinutes"], document.nags.snoozeMinutes],
    [["breaks", "afterMinutes"], document.breaks.afterMinutes],
    [["focus", "durationMinutes"], document.focus.durationMinutes],
    [["focus", "checkinMinutes"], document.focus.checkinMinutes],
  );
  return settings;
}

function checkSettings(document, fail) {
  for (const [path, setting] of settingsOf(document)) {
    // Also catches min > max: the range is then empty
    if (setting.default < setting.min || setting.default > setting.max) {
      const message = `default ${setting.default} is not within min ${setting.min}..max ${setting.max}`;
      fail(RULE.SETTING_RANGE, pointer(...path), message);
    }
  }
  const { fastTickSeconds, slowTickSeconds } = document.tracking;
  if (fastTickSeconds > slowTickSeconds) {
    const message = `fastTickSeconds ${fastTickSeconds} is above slowTickSeconds ${slowTickSeconds}`;
    fail(RULE.TICK_ORDER, pointer("tracking"), message);
  }
}

function checkUniqueIds(path, items, fail) {
  const seen = new Set();
  const reported = new Set();
  for (const { id } of items) {
    if (seen.has(id) && !reported.has(id)) {
      fail(RULE.DUPLICATE_ID, path, `id '${id}' is used more than once`);
      reported.add(id);
    }
    seen.add(id);
  }
}

function checkMood(mood, fail) {
  checkUniqueIds(pointer("mood", "bands"), mood.bands, fail);
  // Sorted by start, each band must begin right after the previous one ends, covering 0..100 exactly
  const path = pointer("mood", "bands");
  let next = 0;
  for (const band of mood.bands.toSorted((a, b) => a.from - b.from)) {
    if (band.from > band.to) fail(RULE.MOOD_BANDS_COVERAGE, path, `band '${band.id}' starts after it ends`);
    else if (band.from > next) fail(RULE.MOOD_BANDS_COVERAGE, path, `no band covers ${next}..${band.from - 1}`);
    else if (band.from < next) fail(RULE.MOOD_BANDS_COVERAGE, path, `band '${band.id}' overlaps at ${band.from}`);
    next = Math.max(next, band.to + 1);
  }
  if (next <= MOOD_MAX) fail(RULE.MOOD_BANDS_COVERAGE, path, `no band covers ${next}..${MOOD_MAX}`);
}

function checkPetsAndPersonalities(document, fail) {
  const { pets, personalities, mood } = document;
  checkUniqueIds(pointer("pets"), pets, fail);
  checkUniqueIds(pointer("personalities"), personalities, fail);
  pets.forEach((pet, i) => {
    for (const { id } of mood.bands) {
      if (!Object.hasOwn(pet.faces, id)) fail(RULE.PET_FACES, pointer("pets", i, "faces"), `no face for band '${id}'`);
    }
  });
  if (!pets.some((pet) => pet.enabled)) fail(RULE.NOTHING_ENABLED, pointer("pets"), "no pet is enabled");
  if (!personalities.some((personality) => personality.enabled)) {
    fail(RULE.NOTHING_ENABLED, pointer("personalities"), "no personality is enabled");
  }
}

function checkLines(document, fail) {
  const { lines } = document;
  const personalityIds = new Set(document.personalities.map(({ id }) => id));
  const poolKeys = new Set([...document.mood.bands.map(({ id }) => id), ANY_POOL]);

  // Unknown trigger names are fine (D-52), but their pool keys still have to make sense
  for (const [personality, triggers] of Object.entries(lines)) {
    if (!personalityIds.has(personality)) {
      fail(RULE.LINE_REFERENCES, pointer("lines", personality), `no personality '${personality}'`);
    }
    for (const [trigger, pools] of Object.entries(triggers)) {
      for (const pool of Object.keys(pools)) {
        if (!poolKeys.has(pool)) {
          const message = `'${pool}' is neither '${ANY_POOL}' nor a mood band`;
          fail(RULE.LINE_REFERENCES, pointer("lines", personality, trigger, pool), message);
        }
      }
    }
  }

  for (const personality of personalityIds) {
    for (const trigger of Object.keys(BUBBLE_TRIGGERS)) {
      const anyPool = ownValue(ownValue(ownValue(lines, personality), trigger), ANY_POOL);
      if (!anyPool?.length) {
        fail(RULE.LINE_COVERAGE, pointer("lines", personality, trigger), `needs at least one '${ANY_POOL}' line`);
      }
    }
  }

  checkPlaceholders(lines, fail);
}

// Only for triggers this backend knows: a newer config may use placeholders a newer trigger allows (D-52)
function checkPlaceholders(lines, fail) {
  for (const [personality, triggers] of Object.entries(lines)) {
    for (const [trigger, allowed] of Object.entries(BUBBLE_TRIGGERS)) {
      for (const [pool, poolLines] of Object.entries(ownValue(triggers, trigger) ?? {})) {
        poolLines.forEach((line, i) => {
          for (const [, name] of line.matchAll(PLACEHOLDER)) {
            if (allowed.includes(name)) continue;
            const allowedText = allowed.map((placeholder) => `{${placeholder}}`).join(", ") || "none";
            const message = `{${name}} can't be used in ${trigger} (allowed: ${allowedText})`;
            fail(RULE.LINE_PLACEHOLDERS, pointer("lines", personality, trigger, pool, i), message);
          }
        });
      }
    }
  }
}

// A key's own value only: an id such as "constructor" must not find Object.prototype's
function ownValue(object, key) {
  return object !== undefined && Object.hasOwn(object, key) ? object[key] : undefined;
}
