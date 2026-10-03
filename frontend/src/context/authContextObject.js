import { createContext } from "react";

// Lives apart from AuthProvider (AuthContext.jsx) so that file only exports components — React Fast
// Refresh can then hot-reload it without a full page reload.
export const AuthContext = createContext(null);
