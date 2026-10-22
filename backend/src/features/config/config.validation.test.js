import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { describe, test } from "node:test";
import { DEFAULT_CONFIG_PATH, FIXTURES_DIR } from "../../lib/sharedFiles.js";
import { BUBBLE_TRIGGERS, parseConfig, RULE, validateConfig } from "./config.validation.js";

const fixtureNames = (folder) => readdirSync(`${FIXTURES_DIR}${folder}`).filter((name) => name.endsWith(".json"));
const fixtureText = (folder, name) => readFileSync(`${FIXTURES_DIR}${folder}/${name}`, "utf8");
const minimal = () => JSON.parse(fixtureText("valid", "minimal.json"));
const rulesOf = (errors) => [...new Set(errors.map((error) => error.rule))];

// The contract with the phone (D-39, D-53): the same files accepted, the same files rejected for the same reason
describe("US-21 shared fixtures, same results as the phone", () => {
  test("both fixture folders have files, so a wrong path can't make the contract pass", () => {
    assert.ok(fixtureNames("valid").length >= 3);
    assert.ok(fixtureNames("invalid").length >= 10);
  });

  for (const name of fixtureNames("valid")) {
    test(`valid/${name} is accepted`, () => {
      assert.deepEqual(parseConfig(fixtureText("valid", name)).errors, []);
    });
  }

  for (const name of fixtureNames("invalid")) {
    const expectedRule = name.split("--")[0];
    test(`invalid/${name} is rejected with exactly ${expectedRule}`, () => {
      const { errors } = parseConfig(fixtureText("invalid", name));
      assert.deepEqual(rulesOf(errors), [expectedRule], JSON.stringify(errors));
    });
  }

  test("every rule has an invalid fixture", () => {
    const covered = new Set(fixtureNames("invalid").map((name) => name.split("--")[0]));
    assert.deepEqual(
      Object.values(RULE).filter((rule) => !covered.has(rule)),
      [],
    );
  });

  test("shared/default-config.json is valid", () => {
    assert.deepEqual(parseConfig(readFileSync(DEFAULT_CONFIG_PATH, "utf8")).errors, []);
  });
});

describe("validateConfig", () => {
  test("reports every problem at once, each with its pointer", () => {
    const document = minimal();
    document.nags.snoozeMinutes.default = 40;
    document.tracking.fastTickSeconds = 50;
    document.pets[0].enabled = false;
    assert.deepEqual(validateConfig(document), [
      {
        rule: RULE.SETTING_RANGE,
        path: "/nags/snoozeMinutes",
        message: "default 40 is not within min 5..max 30",
      },
      { rule: RULE.TICK_ORDER, path: "/tracking", message: "fastTickSeconds 50 is above slowTickSeconds 30" },
      { rule: RULE.NOTHING_ENABLED, path: "/pets", message: "no pet is enabled" },
    ]);
  });

  test("schema errors stop the cross-field rules (D-122)", () => {
    const document = minimal();
    document.nags.snoozeMinutes.default = 40; // would be setting-range
    document.mood.start = "high";
    assert.deepEqual(rulesOf(validateConfig(document)), [RULE.SCHEMA]);
  });

  test("schema errors carry Ajv's pointer, all of them", () => {
    const document = minimal();
    document.mood.start = 101;
    document.pets[0].name = "  ";
    const paths = validateConfig(document).map((error) => error.path);
    assert.ok(paths.includes("/mood/start"), paths.join());
    assert.ok(paths.includes("/pets/0/name"), paths.join());
  });

  test("D-54: a quoted number is rejected; 3.0 is accepted (served as 3, see config.test.js)", () => {
    const quoted = minimal();
    quoted.nags.maxSnoozesPerDay = "3";
    assert.deepEqual(rulesOf(validateConfig(quoted)), [RULE.SCHEMA]);

    const text = fixtureText("valid", "minimal.json").replace('"maxSnoozesPerDay": 3', '"maxSnoozesPerDay": 3.0');
    assert.ok(text.includes("3.0"));
    assert.deepEqual(parseConfig(text).errors, []);
  });

  test("not JSON → one schema error at /", () => {
    const { errors } = parseConfig("{ nope");
    assert.equal(errors.length, 1);
    assert.equal(errors[0].rule, RULE.SCHEMA);
    assert.equal(errors[0].path, "/");
  });

  test("setting-range: min above max, and a category limit, each by path", () => {
    const document = minimal();
    document.focus.durationMinutes = { default: 25, min: 30, max: 20, userEditable: true };
    document.limits.categories.social.dailyMinutes.default = 300;
    assert.deepEqual(
      validateConfig(document).map((error) => error.path),
      ["/limits/categories/social/dailyMinutes", "/focus/durationMinutes"],
    );
  });

  test("setting-range: exactly at min and at max passes", () => {
    const document = minimal();
    document.nags.repeatMinutes.default = 5;
    document.nags.snoozeMinutes.default = 30;
    assert.deepEqual(validateConfig(document), []);
  });

  test("duplicate-id: personalities and mood bands, each reported once", () => {
    const document = minimal();
    document.personalities.push({ ...document.personalities[0] }, { ...document.personalities[0] });
    document.mood.bands = [
      { id: "ran_away", from: 0, to: 0 },
      { id: "fine", from: 1, to: 50 },
      { id: "fine", from: 51, to: 100 },
    ];
    const duplicates = validateConfig(document).filter((error) => error.rule === RULE.DUPLICATE_ID);
    assert.deepEqual(
      duplicates.map((error) => error.path),
      ["/mood/bands", "/personalities"],
    );
  });

  test("mood-bands-coverage: a band ending early, and one that runs backwards", () => {
    const early = minimal();
    early.mood.bands[1].to = 90;
    assert.deepEqual(
      validateConfig(early).map((error) => error.message),
      ["no band covers 91..100"],
    );

    const backwards = minimal();
    backwards.mood.bands[1] = { id: "fine", from: 100, to: 1 };
    const messages = validateConfig(backwards).map((error) => error.message);
    assert.ok(messages.includes("band 'fine' starts after it ends"), messages.join());
  });

  test("nothing-enabled: personalities as well as pets", () => {
    const document = minimal();
    document.personalities[0].enabled = false;
    assert.deepEqual(validateConfig(document), [
      { rule: RULE.NOTHING_ENABLED, path: "/personalities", message: "no personality is enabled" },
    ]);
  });

  test("line-references: a pool key that's neither any nor a band", () => {
    const document = minimal();
    document.lines.plain.idle.sleepy = ["Zzz."];
    assert.deepEqual(validateConfig(document), [
      {
        rule: RULE.LINE_REFERENCES,
        path: "/lines/plain/idle/sleepy",
        message: "'sleepy' is neither 'any' nor a mood band",
      },
    ]);
  });

  test("line-coverage: an empty any pool counts as missing", () => {
    const document = minimal();
    document.lines.plain.goal_met.any = [];
    assert.deepEqual(rulesOf(validateConfig(document)), [RULE.LINE_COVERAGE]);
  });

  test("line-placeholders: one another trigger allows, an unknown one, a typo; allowed ones pass", () => {
    const document = minimal();
    document.lines.plain.limit_reached.any = ["{app}: {limit} of {category}."];
    document.lines.plain.break_due.any = ["{app} again?"];
    document.lines.plain.idle.any = ["{mood}"];
    document.lines.plain.focus_drift.fine = ["{App}?"];
    assert.deepEqual(
      validateConfig(document).map((error) => [error.path, error.message]),
      [
        ["/lines/plain/break_due/any/0", "{app} can't be used in break_due (allowed: {minutes})"],
        ["/lines/plain/focus_drift/fine/0", "{App} can't be used in focus_drift (allowed: {app})"],
        ["/lines/plain/idle/any/0", "{mood} can't be used in idle (allowed: none)"],
      ],
    );
  });

  test("unknown keys and unknown triggers are ignored (D-52)", () => {
    const document = minimal();
    document.futureBlock = { anything: true };
    document.lines.plain.future_trigger = { any: ["{whatever}"] };
    assert.deepEqual(validateConfig(document), []);
  });

  test("an id like 'constructor' doesn't find Object.prototype's members", () => {
    const document = minimal();
    document.mood.bands[0].id = "constructor";
    document.pets[0].faces = { fine: "😺" };
    assert.deepEqual(
      validateConfig(document).map((error) => error.message),
      ["no face for band 'constructor'"],
    );
  });

  test("the trigger list matches the phone's 13 bubble triggers", () => {
    assert.equal(Object.keys(BUBBLE_TRIGGERS).length, 13);
  });
});
