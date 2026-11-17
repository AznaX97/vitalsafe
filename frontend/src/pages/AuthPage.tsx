import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation } from "react-router-dom";
import { errorMessage } from "../api/client";
import { useAuth } from "../auth/useAuth";

export default function AuthPage({ mode }: { mode: "login" | "register" }) {
  const isRegister = mode === "register";
  const { authenticated, loading, login, register } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [error, setError] = useState("");
  const notice = (useLocation().state as { notice?: string } | null)?.notice;
  const [busy, setBusy] = useState(false);

  if (loading) {
    return null;
  }
  if (authenticated) {
    return <Navigate to="/" replace />;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      if (isRegister) {
        await register(email, password, agreed);
      } else {
        await login(email, password);
      }
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <form className="card auth-card" onSubmit={handleSubmit}>
        <h1>VitalSafe</h1>
        <p className="muted">{isRegister ? "Create your private account" : "Log in to your private tracker"}</p>

        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        </label>

        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={isRegister ? "new-password" : "current-password"}
            minLength={isRegister ? 12 : undefined}
            maxLength={72}
            required
          />
        </label>
        {isRegister && <p className="hint">At least 12 characters.</p>}
        {!isRegister && (
          <p className="hint">
            <Link to="/forgot-password">Forgot your password?</Link>
          </p>
        )}

        {isRegister && (
          <label className="consent">
            <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} required />
            <span>
              I have read the{" "}
              <Link to="/privacy" target="_blank" rel="noopener noreferrer">
                privacy notice
              </Link>
              . I understand that this app stores health information, and that it records and charts what I enter
              without giving medical advice.
            </span>
          </label>
        )}

        {notice && !error && <p role="status">{notice}</p>}

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" disabled={busy}>
          {busy ? "Please wait..." : isRegister ? "Create account" : "Log in"}
        </button>

        <p className="muted center">
          {isRegister ? (
            <>
              Already have an account? <Link to="/login">Log in</Link>
            </>
          ) : (
            <>
              New here? <Link to="/register">Create an account</Link>
            </>
          )}
        </p>
      </form>
    </main>
  );
}