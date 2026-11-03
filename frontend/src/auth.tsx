import { createContext, useContext } from "react";
import type { Session } from "./types";
export const AuthContext = createContext<{
  session: Session | null;
  login: (session: Session) => void;
  logout: () => void;
}>({ session: null, login: () => {}, logout: () => {} });
export const useAuth = () => useContext(AuthContext);
