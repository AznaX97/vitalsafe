import { useEffect, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { api, errorMessage } from "../api/client";

export default function ResetPasswordPage() {
  const location = useLocation();
  const navigate = useNavigate();
  // The token arrives in the URL fragment (#token=...). It is read once, then removed from the address bar.
  const [token] = useState(() => new URLSearchParams(location.hash.replace(/^#/, "")).get("token") ?? "");
  const [password, setPassword] = useState("");
  const [repeat, setRepeat] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (window.location.hash) {
      window.history.replaceState(window.history.state, "", window.location.pathname + window.location.search);
    }
  }, []);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError("");
    if (password !== repeat) {
      setError("The two passwords do not match.");
      return;
    }
    setBusy(true);
    try {
      await api("/auth/reset-password", { method: "POST", json: { token, newPassword: password } });
      navigate("/login", { state: { notice: "Your password was changed. Log in with the new one." } });
    } catch (caught) {
      setError(errorMessage(caught));
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <main className="auth-page">
        <section className="card auth-card">
          <h1>This link is not complete</h1>
          <p className="muted">Open the link from the email again, or ask for a new one.</p>
          <p className="muted center">
            <Link to="/forgot-password">Ask for a new link</Link>
          </p>
        </section>
      </main>
    );
  }

  return (
    <main className="auth-page">
      <form className="card auth-card" onSubmit={handleSubmit}>
        <h1>Choose a new password</h1>

        <label>
          New password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            minLength={12}
            maxLength={72}
            required
          />
        </label>
        <label>
          Repeat the new password
          <input
            type="password"
            value={repeat}
            onChange={(e) => setRepeat(e.target.value)}
            autoComplete="new-password"
            minLength={12}
            maxLength={72}
            required
          />
        </label>
        <p className="hint">At least 12 characters. Changing it signs you out everywhere.</p>

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" disabled={busy}>
          {busy ? "Please wait..." : "Change password"}
        </button>

        <p className="muted center">
          <Link to="/forgot-password">Ask for a new link</Link>
        </p>
      </form>
    </main>
  );
}