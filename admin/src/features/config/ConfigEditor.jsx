import { useEffect, useMemo, useRef, useState } from "react";
import { ApiError, getDraft, getPublished, publish, saveDraft } from "../../api.js";
import { ChangesList } from "./ChangesList.jsx";
import { ConfigForm } from "./ConfigForm.jsx";
import { diffConfigs, errorsToErrorSchema } from "./configDiff.js";
import { ServerErrors } from "./ServerErrors.jsx";
import { SECTIONS } from "./sections.js";

const changesBetween = (before, after) =>
  before && after ? diffConfigs(before, after).filter((change) => change.path !== "/configVersion") : [];

/**
 * W-02 + W-03 (ARCHITECTURE §14.11): edit the draft, save it, publish it. Stays mounted while the login form
 * covers it, so unsaved edits survive an expired session; `loginCount` changing means "logged in again".
 */
export function ConfigEditor({ loginCount, onUnauthenticated, onLogout }) {
  const [published, setPublished] = useState(null);
  const [savedDraft, setSavedDraft] = useState(null);
  const [draft, setDraft] = useState(null);
  const [tabId, setTabId] = useState(SECTIONS[0].id);
  const [serverErrors, setServerErrors] = useState([]);
  const [status, setStatus] = useState("");
  const [busy, setBusy] = useState(false);
  const formRef = useRef(null);

  const changes = useMemo(() => changesBetween(published, draft), [published, draft]);
  const unsaved = useMemo(
    () => changesBetween(savedDraft ?? published, draft).length > 0,
    [savedDraft, published, draft],
  );
  const extraErrors = useMemo(() => errorsToErrorSchema(serverErrors), [serverErrors]);

  async function run(action) {
    setBusy(true);
    setStatus("");
    try {
      await action();
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) onUnauthenticated();
      else setStatus(error.message);
    } finally {
      setBusy(false);
    }
  }

  const load = () =>
    run(async () => {
      const [live, saved] = await Promise.all([getPublished(), getDraft()]);
      setPublished(live);
      setSavedDraft(saved);
      setDraft(saved ?? live);
      setServerErrors([]);
    });

  // First load, and again after logging in if nothing was loaded yet; edits already in the form are kept
  // biome-ignore lint/correctness/useExhaustiveDependencies: runs on login only, by design
  useEffect(() => {
    if (!draft) load();
  }, [loginCount]);

  const save = () => {
    if (!formRef.current.validateForm()) return; // the schema in the browser first, both tabs
    return run(async () => {
      try {
        const saved = await saveDraft(draft);
        setSavedDraft(saved);
        setDraft(saved);
        setServerErrors([]);
        setStatus(`Draft v${saved.configVersion} saved.`);
      } catch (error) {
        if (!(error instanceof ApiError && error.code === "VALIDATION_FAILED")) throw error;
        setServerErrors(error.details);
      }
    });
  };

  const publishDraft = () => {
    const count = changes.length;
    if (!window.confirm(`Publish version ${savedDraft.configVersion} to every phone? ${count} change(s).`)) return;
    return run(async () => {
      try {
        const { configVersion } = await publish();
        await load();
        setStatus(`Version ${configVersion} is live.`);
      } catch (error) {
        if (!(error instanceof ApiError && error.code === "VALIDATION_FAILED")) throw error;
        setServerErrors(error.details);
      }
    });
  };

  const resetToPublished = () => {
    setDraft(published);
    setServerErrors([]);
  };

  if (!draft) return <main className="page">{status ? <p className="error">{status}</p> : <p>Loading…</p>}</main>;

  const tab = SECTIONS.find((section) => section.id === tabId);
  return (
    <main className="page">
      <header className="toolbar">
        <h1>Nyx config</h1>
        <span className="versions">
          Live v{published.configVersion} · {savedDraft ? `draft v${savedDraft.configVersion} saved` : "no draft saved"}
          {unsaved && <strong> · unsaved edits</strong>}
        </span>
        <button type="button" onClick={save} disabled={busy || !unsaved}>
          Save draft
        </button>
        <button type="button" onClick={publishDraft} disabled={busy || !savedDraft || unsaved}>
          Publish
        </button>
        <button type="button" onClick={resetToPublished} disabled={busy || changes.length === 0}>
          Reset to published
        </button>
        <button type="button" onClick={onLogout} className="link">
          Log out
        </button>
      </header>
      {status && <p className="status">{status}</p>}
      <ServerErrors details={serverErrors} />
      <ChangesList changes={changes} />
      <nav className="tabs">
        {SECTIONS.map((section) => (
          <button
            type="button"
            key={section.id}
            className={section.id === tabId ? "active" : ""}
            onClick={() => setTabId(section.id)}
          >
            {section.label}
          </button>
        ))}
      </nav>
      <ConfigForm ref={formRef} section={tab} draft={draft} onChange={setDraft} extraErrors={extraErrors} />
    </main>
  );
}
