import { act } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import keycloak from './keycloak';

// keycloak-js elle-même n'est pas testée ici (adaptateur tiers) — seule la logique hasRole()
// d'AuthContext, le miroir client de common-lib.RoleHierarchy, est sous test.
vi.mock('./keycloak', () => ({
  default: {
    init: vi.fn().mockResolvedValue(true),
    tokenParsed: { sub: 'user-1', realm_access: { roles: [] } },
    login: vi.fn(),
    logout: vi.fn(),
  },
}));

function Probe() {
  const { hasRole } = useAuth();
  const order = ['ROLE_VIEWER', 'ROLE_DEVELOPER', 'ROLE_TECH_LEAD', 'ROLE_RELEASE_MANAGER', 'ROLE_ADMIN'];
  return (
    <ul>
      {order.map((role) => (
        <li key={role} data-testid={role}>{String(hasRole(role))}</li>
      ))}
    </ul>
  );
}

async function renderWithRoles(roles) {
  keycloak.tokenParsed = { sub: 'user-1', realm_access: { roles } };
  let result;
  // AuthProvider's useEffect resolves keycloak.init(...) asynchronously and calls setState —
  // flushed here inside act() so React doesn't warn about an update outside of it. hasRole()
  // itself doesn't depend on that state (it reads keycloak.tokenParsed directly), only the
  // warning does.
  await act(async () => {
    result = render(<AuthProvider><Probe /></AuthProvider>);
  });
  return result;
}

describe('AuthContext.hasRole', () => {
  beforeEach(() => {
    keycloak.tokenParsed = { sub: 'user-1', realm_access: { roles: [] } };
  });

  it('a ROLE_VIEWER-only user reaches only VIEWER', async () => {
    await renderWithRoles(['ROLE_VIEWER']);
    expect(screen.getByTestId('ROLE_VIEWER')).toHaveTextContent('true');
    expect(screen.getByTestId('ROLE_DEVELOPER')).toHaveTextContent('false');
    expect(screen.getByTestId('ROLE_TECH_LEAD')).toHaveTextContent('false');
  });

  it('a ROLE_TECH_LEAD user reaches VIEWER..TECH_LEAD but not RELEASE_MANAGER/ADMIN', async () => {
    await renderWithRoles(['ROLE_TECH_LEAD']);
    expect(screen.getByTestId('ROLE_VIEWER')).toHaveTextContent('true');
    expect(screen.getByTestId('ROLE_DEVELOPER')).toHaveTextContent('true');
    expect(screen.getByTestId('ROLE_TECH_LEAD')).toHaveTextContent('true');
    expect(screen.getByTestId('ROLE_RELEASE_MANAGER')).toHaveTextContent('false');
    expect(screen.getByTestId('ROLE_ADMIN')).toHaveTextContent('false');
  });

  it('a ROLE_ADMIN user reaches every role in the hierarchy', async () => {
    await renderWithRoles(['ROLE_ADMIN']);
    ['ROLE_VIEWER', 'ROLE_DEVELOPER', 'ROLE_TECH_LEAD', 'ROLE_RELEASE_MANAGER', 'ROLE_ADMIN'].forEach((role) => {
      expect(screen.getByTestId(role)).toHaveTextContent('true');
    });
  });

  it('a user with no roles reaches nothing', async () => {
    await renderWithRoles([]);
    expect(screen.getByTestId('ROLE_VIEWER')).toHaveTextContent('false');
  });
});
