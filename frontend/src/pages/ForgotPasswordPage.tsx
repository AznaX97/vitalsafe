import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, errorMessage } from "../api/client";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await api("/auth/forgot-password", { method: "POST", json: { email } });
      setSent(true);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <main className="auth-page">
        <section className="card auth-card">
          <h1>Check your email</h1>
          <p role="status">If that email address has an account, a reset link is on its way. The link works once, for 30 minutes.</p>
          <p className="hint">Nothing arrived? Check your spam folder, or ask again in a little while.</p>
          <p className="muted center">
            <Link to="/login">Back to log in</Link>
          </p>
        </section>
      </main>
    );
  }

  return (
    <main className="auth-page">
      <form className="card auth-card" onSubmit={handleSubmit}>
        <h1>Reset your password</h1>
        <p className="muted">Enter your email address and we will send you a link to choose a new password.</p>

        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        </label>

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" disabled={busy}>
          {busy ? "Please wait..." : "Send reset link"}
        </button>

        <p className="muted center">
          <Link to="/login">Back to log in</Link>
        </p>
      </form>
    </main>
  );
}