export const TICKET_STATUSES = [
  'DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED',
  'DEPLOYING_DEV', 'DEPLOYED_DEV', 'DEPLOYING_TEST', 'DEPLOYED_TEST',
  'PENDING_PROD_APPROVAL', 'DEPLOYING_PROD', 'DEPLOYED_PROD', 'FAILED', 'CLOSED',
];

export const TICKET_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

// Miroir de TicketStateMachine (ticket-service) — quels boutons afficher, pas la légalité
// réelle (revalidée côté backend à chaque appel). `requiresTechLead` = hasRole('TECH_LEAD')
// requis ; `allowOwner` = visible aussi pour le créateur du ticket (règle métier : un
// propriétaire non TECH_LEAD ne peut demander que SUBMITTED ou CANCELLED).
export const TICKET_ACTIONS = {
  DRAFT: [
    { key: 'submit', label: 'Soumettre', kind: 'status', target: 'SUBMITTED', allowOwner: true, variant: 'primary' },
    { key: 'cancel', label: 'Annuler', kind: 'status', target: 'CANCELLED', allowOwner: true, confirm: true, variant: 'danger' },
  ],
  SUBMITTED: [
    { key: 'approve', label: 'Approuver', kind: 'approve', requiresTechLead: true, variant: 'primary' },
    { key: 'reject', label: 'Rejeter', kind: 'reject', requiresTechLead: true, confirm: true, withComment: true, variant: 'danger' },
    { key: 'cancel', label: 'Annuler', kind: 'status', target: 'CANCELLED', allowOwner: true, confirm: true, variant: 'ghost' },
  ],
  APPROVED: [
    { key: 'deploy-dev', label: 'Déployer en DEV', kind: 'deploy', env: 'DEV', requiresTechLead: true, confirm: true, variant: 'primary' },
  ],
  DEPLOYED_DEV: [
    { key: 'deploy-test', label: 'Déployer en TEST', kind: 'deploy', env: 'TEST', requiresTechLead: true, confirm: true, variant: 'primary' },
  ],
  DEPLOYED_TEST: [
    { key: 'request-prod', label: "Demander l'approbation PROD", kind: 'status', target: 'PENDING_PROD_APPROVAL', requiresTechLead: true, variant: 'primary' },
  ],
  PENDING_PROD_APPROVAL: [
    { key: 'approve-prod', label: 'Approuver pour PROD', kind: 'approve', requiresTechLead: true, confirm: true, variant: 'gold' },
    { key: 'reject', label: 'Rejeter', kind: 'reject', requiresTechLead: true, confirm: true, withComment: true, variant: 'danger' },
  ],
  DEPLOYED_PROD: [
    { key: 'close', label: 'Clôturer', kind: 'status', target: 'CLOSED', requiresTechLead: true, variant: 'primary' },
  ],
  FAILED: [
    { key: 'resubmit', label: 'Resoumettre', kind: 'status', target: 'SUBMITTED', allowOwner: true, variant: 'primary' },
  ],
  REJECTED: [
    { key: 'back-to-draft', label: 'Remettre en brouillon', kind: 'status', target: 'DRAFT', requiresTechLead: true, variant: 'ghost' },
  ],
  CANCELLED: [],
  CLOSED: [],
};
