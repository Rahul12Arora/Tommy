const MAX_VALUE_LENGTH = 80;

/** W-02's "draft vs published": every value the draft changes, published → draft (D-139). */
export function ChangesList({ changes }) {
  if (changes.length === 0) return <p className="muted">No changes from the live version.</p>;
  return (
    <details className="changes" open={changes.length <= 10}>
      <summary>
        {changes.length} change{changes.length === 1 ? "" : "s"} from the live version
      </summary>
      <ul>
        {changes.map(({ path, before, after }) => (
          <li key={path}>
            <code>{path}</code>: {show(before)} → <strong>{show(after)}</strong>
          </li>
        ))}
      </ul>
    </details>
  );
}

function show(value) {
  if (value === undefined) return "—";
  const text = JSON.stringify(value);
  return text.length > MAX_VALUE_LENGTH ? `${text.slice(0, MAX_VALUE_LENGTH)}…` : text;
}
