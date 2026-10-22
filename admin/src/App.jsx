import { useState } from "react";
import { logout } from "./api.js";
import { ConfigEditor } from "./features/config/ConfigEditor.jsx";
import { LoginPage } from "./features/login/LoginPage.jsx";

/**
 * Three screens, no router (TECH_STACK §6). The editor always renders; the login form covers it whenever the
 * backend answers 401, so an expired session never throws away unsaved edits (ARCHITECTURE §14.11).
 */
export function App() {
  // Assume a session until the editor's first request says otherwise: one round trip, no extra endpoint
  const [loggedIn, setLoggedIn] = useState(true);
  const [loginCount, setLoginCount] = useState(0);

  async function handleLogout() {
    try {
      await logout();
      setLoggedIn(false);
    } catch (error) {
      window.alert(`Logout failed: ${error.message}`);
    }
  }

  return (
    <>
      <ConfigEditor loginCount={loginCount} onUnauthenticated={() => setLoggedIn(false)} onLogout={handleLogout} />
      {!loggedIn && (
        <LoginPage
          onLoggedIn={() => {
            setLoggedIn(true);
            setLoginCount((count) => count + 1);
          }}
        />
      )}
    </>
  );
}
