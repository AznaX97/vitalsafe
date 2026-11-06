import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, refreshSession, serverLogout, setToken, setUnauthorizedHandler, type Session } from "../api/client";
import { AuthContext, type AuthState } from "./useAuth";

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState<string | null>(null);
  const [authenticated, setAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);

  // Clears the local session and every cached reading (no server call)
  const endSession = useCallback(() => {
    setToken(null);
    setEmail(null);
    setAuthenticated(false);
    queryClient.clear();
  }, [queryClient]);

  const logout = useCallback(async () => {
    await serverLogout();
    endSession();
  }, [endSession]);

  useEffect(() => {
    setUnauthorizedHandler(endSession);
    return () => setUnauthorizedHandler(null);
  }, [endSession]);

  // On page load, try to restore the session from the refresh cookie
  useEffect(() => {
    let cancelled = false;
    refreshSession().then((session) => {
      if (cancelled) {
        return;
      }
      if (session) {
        setEmail(session.email);
        setAuthenticated(true);
      }
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const start = useCallback(async (path: string, json: unknown) => {
    const session = await api<Session>(path, { method: "POST", json });
    setToken(session.token);
    setEmail(session.email);
    setAuthenticated(true);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      email,
      authenticated,
      loading,
      login: (loginEmail, password) => start("/auth/login", { email: loginEmail, password }),
      register: (registerEmail, password, consent) => start("/auth/register", { email: registerEmail, password, consent }),
      logout,
      endSession,
    }),
    [email, authenticated, loading, start, logout, endSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}