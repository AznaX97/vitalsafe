import { TYPE_LABELS, formatReading, type Page, type Vital } from "./types";

interface Props {
  data: Page<Vital>;
  onEdit: (vital: Vital) => void;
  onDelete: (vital: Vital) => void;
  onPage: (page: number) => void;
}

export default function VitalList({ data, onEdit, onDelete, onPage }: Props) {
  if (data.items.length === 0) {
    return <p className="muted">No readings yet.</p>;
  }

  const totalPages = Math.max(data.totalPages, 1);

  return (
    <>
      <ul className="readings">
        {data.items.map((vital) => (
          <li key={vital.id}>
            <div>
              <strong>{formatReading(vital)}</strong>
              <span className="tag">{TYPE_LABELS[vital.type]}</span>
              <p className="muted">{new Date(vital.measuredAt).toLocaleString()}</p>
              {vital.note && <p>{vital.note}</p>}
            </div>
            <div className="row">
              <button type="button" className="secondary" onClick={() => onEdit(vital)}>
                Edit
              </button>
              <button type="button" className="danger" onClick={() => onDelete(vital)}>
                Delete
              </button>
            </div>
          </li>
        ))}
      </ul>

      <div className="pager">
        <button type="button" className="secondary" disabled={data.page <= 0} onClick={() => onPage(data.page - 1)}>
          Previous
        </button>
        <span className="muted">
          Page {data.page + 1} of {totalPages}
        </span>
        <button
          type="button"
          className="secondary"
          disabled={data.page + 1 >= totalPages}
          onClick={() => onPage(data.page + 1)}
        >
          Next
        </button>
      </div>
    </>
  );
}