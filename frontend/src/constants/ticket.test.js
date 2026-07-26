import { describe, expect, it } from 'vitest';
import { TICKET_ACTIONS, TICKET_STATUSES } from './ticket';

// Verrouille le contrat entre ce tableau (utilisé par TicketActionBar pour l'affichage) et les
// règles métier réelles de TicketServiceImpl/TicketStateMachine (backend) — voir
// explication_phase_8.md §4. Une divergence silencieuse ici ferait afficher un bouton pour une
// action que le backend refuserait (ou l'inverse).
// Statuts sans bouton d'action : les statuts terminaux (CANCELLED/CLOSED) ET les statuts de
// déploiement en cours (DEPLOYING_*, pilotés par le pipeline automatisé, pas par un clic
// utilisateur) — TicketActionBar.jsx traite une clé absente comme [] (`?? []`), donc l'un ou
// l'autre est un comportement correct, pas un oubli.
const NO_ACTION_STATUSES = ['CANCELLED', 'CLOSED', 'DEPLOYING_DEV', 'DEPLOYING_TEST', 'DEPLOYING_PROD'];

describe('TICKET_ACTIONS', () => {
  it('terminal statuses (CANCELLED, CLOSED) explicitly declare no actions', () => {
    expect(TICKET_ACTIONS.CANCELLED).toEqual([]);
    expect(TICKET_ACTIONS.CLOSED).toEqual([]);
  });

  it('every status that is neither terminal nor an in-flight deploy offers at least one action', () => {
    TICKET_STATUSES
      .filter((status) => !NO_ACTION_STATUSES.includes(status))
      .forEach((status) => {
        expect(TICKET_ACTIONS[status]?.length ?? 0).toBeGreaterThan(0);
      });
  });

  it('a non-TECH_LEAD owner on a DRAFT ticket can only reach SUBMITTED or CANCELLED', () => {
    const ownerTargets = TICKET_ACTIONS.DRAFT.filter((a) => a.allowOwner).map((a) => a.target);
    expect(ownerTargets.sort()).toEqual(['CANCELLED', 'SUBMITTED']);
  });

  it('REJECTED -> DRAFT requires TECH_LEAD+, even for the ticket owner', () => {
    const action = TICKET_ACTIONS.REJECTED.find((a) => a.target === 'DRAFT');
    expect(action).toBeDefined();
    expect(action.requiresTechLead).toBe(true);
    expect(action.allowOwner).toBeFalsy();
  });

  it('on SUBMITTED, approve/reject require TECH_LEAD+ while cancel stays owner-reachable', () => {
    const approve = TICKET_ACTIONS.SUBMITTED.find((a) => a.key === 'approve');
    const reject = TICKET_ACTIONS.SUBMITTED.find((a) => a.key === 'reject');
    const cancel = TICKET_ACTIONS.SUBMITTED.find((a) => a.key === 'cancel');
    expect(approve.requiresTechLead).toBe(true);
    expect(reject.requiresTechLead).toBe(true);
    expect(cancel.allowOwner).toBe(true);
  });

  it('high-impact actions (reject, cancel, deploy to PROD) require confirmation', () => {
    expect(TICKET_ACTIONS.SUBMITTED.find((a) => a.key === 'reject').confirm).toBe(true);
    expect(TICKET_ACTIONS.SUBMITTED.find((a) => a.key === 'cancel').confirm).toBe(true);
    expect(TICKET_ACTIONS.PENDING_PROD_APPROVAL.find((a) => a.key === 'approve-prod').confirm).toBe(true);
  });
});
