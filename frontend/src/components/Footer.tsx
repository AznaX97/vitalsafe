import { Link } from "react-router-dom";

export default function Footer() {
  return (
    <footer className="footer">
      <Link to="/privacy">Privacy notice and medical disclaimer</Link>
      <span className="muted"> · Not medical advice. Not for emergencies.</span>
    </footer>
  );
}