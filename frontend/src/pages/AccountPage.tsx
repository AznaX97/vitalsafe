import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, errorMessage } from "../api/client";
import { useAuth } from "../auth/useAuth";
import { setFlash } from "../lib/flash";

export default function AccountPage() {
  const { email, endSession } = useAuth();
  const navigate = useNavigate();

  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [repeat, setRepeat] = useState("");
  const [changeError, setChangeError] = useState("");
  const [changing, setChanging] = useState(false);

  const [deletePassword, setDeletePassword] = useState("");
  const [understood, setUnderstood] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [deleting, setDeleting] = useState(false);

  async function handleChange(event: FormEvent) {
    event.preventDefault();
    setChangeError("");
    if (next !== repeat) {
      setChangeError("The two new passwords do not match.");
      return;
    }
    setChanging(true);
    try {
      await api("/account/password", { method: "POST", json: { currentPassword: current, newPassword: next } });
      // The server ended every session. The notice is kept in a variable, because ending the session makes this protected page redirect to the login page by itself.
      setFlash("Your password was changed. Log in again with the new one.");
      endSession();
      navigate("/login", { replace: true });
    } catch (caught) {
      setChangeError(errorMessage(caught));
      setChanging(false);
    }
  }

  async function handleDelete(event: FormEvent) {
    event.preventDefault();
    setDeleteError("");
    setDeleting(true);
    try {
      await api("/account/delete", { method: "POST", json: { password: deletePassword } });
      setFlash("Your account and all its data were deleted.");
      endSession();
      navigate("/login", { replace: true });
    } catch (caught) {
      setDeleteError(errorMessage(caught));
      setDeleting(false);
    }
  }

  return (
    <div className="container legal">
      <p>
        <Link to="/">Back to your readings</Link>
      </p>
      <h1>Your account</h1>
      <p className="muted">{email}</p>

      <form className="card" onSubmit={handleChange}>
        <h2>Change your password</h2>
        <label>
          Current password
          <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" required />
        </label>
        <label>
          New password
          <input
            type="password"
            value={next}
            onChange={(e) => setNext(e.target.value)}
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
        <p className="hint">At least 12 characters. You will be signed out everywhere and asked to log in again.</p>
        {changeError && (
          <p className="error" role="alert">
            {changeError}
          </p>
        )}
        <button type="submit" disabled={changing}>
          {changing ? "Please wait..." : "Change password"}
        </button>
      </form>

      <form className="card" onSubmit={handleDelete}>
        <h2>Delete your account</h2>
        <p>
          This permanently removes your login, all your readings and your target ranges. It cannot be undone. Download your
          data first from the Your data panel if you want a copy.
        </p>
        <label>
          Your password
          <input type="password" value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} autoComplete="current-password" required />
        </label>
        <label className="consent">
          <input type="checkbox" checked={understood} onChange={(e) => setUnderstood(e.target.checked)} />
          <span>I understand that this cannot be undone.</span>
        </label>
        {deleteError && (
          <p className="error" role="alert">
            {deleteError}
          </p>
        )}
        <button type="submit" className="danger" disabled={!understood || deletePassword === "" || deleting}>
          {deleting ? "Deleting..." : "Delete my account"}
        </button>
      </form>
    </div>
  );
}