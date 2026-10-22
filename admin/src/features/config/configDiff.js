/**
 * Every leaf that differs between two config documents, as `{ path, before, after }` with JSON Pointer paths.
 * Arrays are compared by index. A key present on one side only shows `undefined` on the other.
 */
export function diffConfigs(before, after, path = "") {
  if (isContainer(before) && isContainer(after) && Array.isArray(before) === Array.isArray(after)) {
    const keys = new Set([...Object.keys(before), ...Object.keys(after)]);
    return [...keys].flatMap((key) => diffConfigs(before[key], after[key], `${path}/${escapePointer(key)}`));
  }
  return Object.is(before, after) ? [] : [{ path: path || "/", before, after }];
}

/** Server error details (JSON Pointers) → RJSF's `extraErrors` tree, so each message shows under its field. */
export function errorsToErrorSchema(details) {
  const root = {};
  for (const { path, message } of details) {
    const segments = path === "/" ? [] : path.slice(1).split("/").map(unescapePointer);
    let node = root;
    for (const segment of segments) {
      node[segment] ??= {};
      node = node[segment];
    }
    node.__errors ??= [];
    node.__errors.push(message);
  }
  return root;
}

const isContainer = (value) => typeof value === "object" && value !== null;
// RFC 6901: "~" and "/" inside a key are written ~0 and ~1
const escapePointer = (key) => key.replaceAll("~", "~0").replaceAll("/", "~1");
const unescapePointer = (segment) => segment.replaceAll("~1", "/").replaceAll("~0", "~");
