import { useAuth } from "../auth/useAuth";

// The readings arrive in the next pull request
export default function DashboardPage() {
  const { email, logout } = useAuth();

  return (
    <div className="container">
      <header className="topbar">
        <h1>VitalSafe</h1>
        <div className="row">
          <span className="muted">{email}</span>
          <button type="button" className="secondary" onClick={() => void logout()}>
            Log out
          </button>
        </div>
      </header>
      <section className="card">
        <h2>Your readings</h2>
        <p className="muted">Nothing to show yet.</p>
      </section>
    </div>
  );
}