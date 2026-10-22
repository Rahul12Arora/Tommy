import { sectionForPath } from "./sections.js";

/**
 * What the backend rejected on save or publish, with the tab to look in: the cross-field rules (bands covering
 * 0–100, lines for every trigger…) exist only on the server.
 */
export function ServerErrors({ details }) {
  if (details.length === 0) return null;
  return (
    <div className="server-errors" role="alert">
      <strong>The server rejected this config:</strong>
      <ul>
        {details.map(({ path, message }) => (
          <li key={`${path} ${message}`}>
            <code>{path}</code> {message}
            {sectionForPath(path) && <span className="muted"> ({sectionForPath(path).label})</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}
