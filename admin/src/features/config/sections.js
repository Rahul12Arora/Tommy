import schema from "../../../../shared/config.schema.json";

export { schema };

// The server sets these (D-136); the header shows them instead of a form field
export const VERSION_KEYS = ["schemaVersion", "configVersion"];

// W-03: content. Faces per mood band (the mood → emoji mapping) live inside each pet
const CONTENT_KEYS = ["pets", "personalities", "lines", "categoryDefaults", "androidCategoryMap"];

/**
 * The editor's two tabs (D-139). Settings (W-02) takes every other top-level key, so a section added to the
 * schema shows up in the console without a code change (D-33).
 */
export const SECTIONS = [
  {
    id: "settings",
    label: "Settings",
    keys: Object.keys(schema.properties).filter((key) => !CONTENT_KEYS.includes(key) && !VERSION_KEYS.includes(key)),
  },
  { id: "content", label: "Pets & lines", keys: CONTENT_KEYS },
];

/** The tab a JSON Pointer from the server belongs to, so its error can say where to look. */
export function sectionForPath(path) {
  const topKey = path.split("/")[1];
  return SECTIONS.find((section) => section.keys.includes(topKey));
}
