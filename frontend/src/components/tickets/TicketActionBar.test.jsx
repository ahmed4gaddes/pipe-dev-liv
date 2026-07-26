import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import TicketActionBar from './TicketActionBar';

// Mocks les hooks API (react-query) et le toast pour isoler purement la logique d'affichage :
// quels boutons apparaissent pour quelle combinaison statut/rôle/propriétaire. Chaque clic
// délègue toujours au vrai endpoint (voir ces hooks dans api/tickets.js) — non testé ici.
const mutateSpy = vi.fn();
function stubMutation() {
  return { mutate: mutateSpy, isPending: false };
}

vi.mock('../../api/tickets', () => ({
  useChangeStatus: () => stubMutation(),
  useApproveTicket: () => stubMutation(),
  useRejectTicket: () => stubMutation(),
  useDeployTicket: () => stubMutation(),
}));

vi.mock('../ui/ToastProvider', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
}));

let mockAuth = { hasRole: () => false, userId: 'owner-1' };
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => mockAuth,
}));

function ticket(overrides = {}) {
  return { id: 1, status: 'SUBMITTED', createdByUserId: 'owner-1', ...overrides };
}

describe('TicketActionBar', () => {
  it('a TECH_LEAD sees Approve, Reject and Cancel on a SUBMITTED ticket', () => {
    mockAuth = { hasRole: (r) => r === 'ROLE_TECH_LEAD', userId: 'someone-else' };

    render(<TicketActionBar ticket={ticket()} />);

    expect(screen.getByText('Approuver')).toBeInTheDocument();
    expect(screen.getByText('Rejeter')).toBeInTheDocument();
    expect(screen.getByText('Annuler')).toBeInTheDocument();
  });

  it('the plain ticket owner only sees Cancel on a SUBMITTED ticket', () => {
    mockAuth = { hasRole: () => false, userId: 'owner-1' };

    render(<TicketActionBar ticket={ticket({ createdByUserId: 'owner-1' })} />);

    expect(screen.queryByText('Approuver')).not.toBeInTheDocument();
    expect(screen.queryByText('Rejeter')).not.toBeInTheDocument();
    expect(screen.getByText('Annuler')).toBeInTheDocument();
  });

  it('a non-owner, non-TECH_LEAD viewer sees no actions at all', () => {
    mockAuth = { hasRole: () => false, userId: 'someone-else' };

    const { container } = render(<TicketActionBar ticket={ticket({ createdByUserId: 'owner-1' })} />);

    expect(container).toBeEmptyDOMElement();
  });

  it('a terminal-status ticket (CLOSED) renders nothing for anyone', () => {
    mockAuth = { hasRole: () => true, userId: 'owner-1' };

    const { container } = render(<TicketActionBar ticket={ticket({ status: 'CLOSED' })} />);

    expect(container).toBeEmptyDOMElement();
  });
});
