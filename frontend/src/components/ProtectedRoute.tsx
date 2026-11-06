import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { authenticated, loading } = useAuth();
  if (loading) {
    return <p className="muted center">Loading...</p>;
  }
  return authenticated ? children : <Navigate to="/login" replace />;
}