import { createContext, useCallback, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { api, setOnUnauthorized } from "../lib/api";
import {
  clearToken,
  getToken,
  getTokenEmail,
  getTokenUserId,
  isAuthenticated,
  setToken,
} from "../lib/auth";
import type { LoginResponse, RegisterResponse } from "../lib/types";

interface AuthUser {
  email: string;
  id: number;
}

interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  register: (email: string, password: string) => Promise<AuthUser>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Rebuilds the user object solely from the stored JWT (never exposes the token). */
function userFromToken(): AuthUser | null {
  const token = getToken();
  if (!token || !isAuthenticated()) return null;
  const email = getTokenEmail(token);
  const id = getTokenUserId(token);
  if (!email) return null;
  return { email, id: id ?? 0 };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => userFromToken());

  const logout = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  useEffect(() => {
    setOnUnauthorized(logout);
    return () => setOnUnauthorized(null);
  }, [logout]);

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.post<LoginResponse>("/api/v1/auth/login", { email, password });
    setToken(res.accessToken);
    const next = userFromToken();
    setUser(next);
    return next ?? { email, id: 0 };
  }, []);

  const register = useCallback(async (email: string, password: string) => {
    const res = await api.post<RegisterResponse>("/api/v1/auth/register", { email, password });
    // Registration does not issue a token; the user logs in on the next screen.
    return { email: res.email, id: res.id };
  }, []);

  const value: AuthContextValue = {
    user,
    isAuthenticated: user !== null,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within <AuthProvider>");
  return ctx;
}