import { createContext, useContext, useEffect, useRef, useState } from 'react';
import keycloak from './keycloak';

// Reflète common-lib.RoleHierarchy côté backend (ADMIN > RELEASE_MANAGER > TECH_LEAD >
// DEVELOPER > VIEWER) — UX uniquement (masquer/afficher des boutons), jamais la vraie
// frontière de sécurité : chaque endpoint reste protégé par son propre @PreAuthorize.
const ROLE_ORDER = ['ROLE_VIEWER', 'ROLE_DEVELOPER', 'ROLE_TECH_LEAD', 'ROLE_RELEASE_MANAGER', 'ROLE_ADMIN'];

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [state, setState] = useState({ initialized: false, authenticated: false });
  const initRan = useRef(false);

  useEffect(() => {
    if (initRan.current) return;
    initRan.current = true;

    keycloak
      .init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
      })
      .then((authenticated) => {
        setState({ initialized: true, authenticated });
      })
      .catch(() => {
        setState({ initialized: true, authenticated: false });
      });

    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).catch(() => keycloak.login());
    };
  }, []);

  const roles = keycloak.tokenParsed?.realm_access?.roles ?? [];

  function hasRole(minRole) {
    const minIndex = ROLE_ORDER.indexOf(minRole);
    if (minIndex === -1) return false;
    return roles.some((r) => ROLE_ORDER.indexOf(r) >= minIndex);
  }

  const value = {
    initialized: state.initialized,
    authenticated: state.authenticated,
    userId: keycloak.tokenParsed?.sub ?? null,
    email: keycloak.tokenParsed?.email ?? null,
    fullName: keycloak.tokenParsed?.name ?? keycloak.tokenParsed?.preferred_username ?? null,
    roles,
    hasRole,
    login: () => keycloak.login(),
    logout: () => keycloak.logout({ redirectUri: window.location.origin }),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components -- hook colocated with its provider by design
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
