import { useState } from "react";
import { ApiError, login } from "../../api.js";

const MESSAGES = {
  401: "Wrong username or password.",
  429: "Too many attempts — wait a minute and try again.",
};

/** W-01. Shown over the editor whenever the backend says there's no session (US-23). */
export function LoginPage({ onLoggedIn }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await login(username, password);
      setPassword("");
      onLoggedIn();
    } catch (failure) {
      setError((failure instanceof ApiError && MESSAGES[failure.status]) || failure.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-overlay">
      <form className="login" onSubmit={submit}>
        <h1>Nyx admin</h1>
        <label>
          Username
          <input
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" disabled={busy}>
          Log in
        </button>
      </form>
    </div>
  );
}
