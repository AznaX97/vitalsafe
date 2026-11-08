import { useState } from "react";
import { errorMessage } from "../api/client";
import { useAuth } from "../auth/useAuth";
import { TYPE_LABELS, VITAL_TYPES, type Vital, type VitalInput, type VitalType } from "../vitals/types";
import { useDeleteVital, useSaveVital, useVitalsPage } from "../vitals/useVitals";
import VitalForm from "../vitals/VitalForm";
import VitalList from "../vitals/VitalList";

const PAGE_SIZE = 10;

export default function DashboardPage() {
  const { email, logout } = useAuth();
  const [type, setType] = useState<VitalType | "">("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Vital | null>(null);
  const [formVersion, setFormVersion] = useState(0);
  const [actionError, setActionError] = useState("");

  const query = useVitalsPage({ type, page, size: PAGE_SIZE });
  const save = useSaveVital();
  const remove = useDeleteVital();

  async function handleSubmit(input: VitalInput) {
    await save.mutateAsync({ id: editing?.id, input });
    setActionError("");
    setEditing(null);
    setFormVersion((version) => version + 1);   // gives the form a fresh state
  }

  async function handleDelete(vital: Vital) {
    if (!window.confirm("Delete this reading permanently?")) {
      return;
    }
    try {
      await remove.mutateAsync(vital.id);
      setActionError("");
      if (editing?.id === vital.id) {
        setEditing(null);
      }
      // Deleting the last reading on a later page would leave an empty page, so step back
      if (query.data && query.data.items.length === 1 && page > 0) {
        setPage(page - 1);
      }
    } catch (caught) {
      setActionError(errorMessage(caught));
    }
  }

  function changeType(next: VitalType | "") {
    setType(next);
    setPage(0);
  }

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

      <div className="grid">
        <div className="stack">
          <VitalForm
            key={editing?.id ?? `new-${formVersion}`}
            initial={editing}
            onSubmit={handleSubmit}
            onCancel={() => setEditing(null)}
          />
        </div>

        <section className="card">
          <div className="topbar">
            <h2>Your readings</h2>
            <label>
              Show
              <select value={type} onChange={(e) => changeType(e.target.value as VitalType | "")}>
                <option value="">All readings</option>
                {VITAL_TYPES.map((option) => (
                  <option key={option} value={option}>
                    {TYPE_LABELS[option]}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {actionError && (
            <p className="error" role="alert">
              {actionError}
            </p>
          )}
          {query.isPending && <p className="muted">Loading...</p>}
          {query.isError && (
            <p className="error" role="alert">
              {errorMessage(query.error)}
            </p>
          )}
          {query.data && <VitalList data={query.data} onEdit={setEditing} onDelete={handleDelete} onPage={setPage} />}
        </section>
      </div>
    </div>
  );
}