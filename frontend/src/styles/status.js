// Couleurs par famille de statut, partagées par TicketStatusPill et PipelineStatusPill.
// Reflète TicketStateMachine (ticket-service) et PipelineStatus (pipeline-service) —
// UX uniquement, la légalité des transitions reste décidée côté backend.
export const STATUS_TONE = {
  DRAFT: 'gray',
  SUBMITTED: 'sky',
  PENDING_PROD_APPROVAL: 'sky',
  APPROVED: 'navy',
  DEPLOYING_DEV: 'gold-pulse',
  DEPLOYING_TEST: 'gold-pulse',
  DEPLOYING_PROD: 'gold-pulse',
  RUNNING: 'gold-pulse',
  QUEUED: 'gold-pulse',
  DEPLOYED_DEV: 'success',
  DEPLOYED_TEST: 'success',
  DEPLOYED_PROD: 'success',
  SUCCESS: 'success',
  FAILED: 'danger',
  REJECTED: 'muted-danger',
  CANCELLED: 'muted-danger',
  CLOSED: 'navy-solid',
};

export function toneForStatus(status) {
  return STATUS_TONE[status] ?? 'gray';
}

// Valeurs hex utilisées pour les remplissages SVG (graphiques) — les tokens CSS ne sont
// pas lisibles depuis recharts. Contraste vs blanc validé (voir explication_phase_8.md) ;
// sky/gold passent sous 3:1 donc chaque barre du graphique porte toujours une étiquette
// directe (canal de secours), jamais la couleur seule.
const TONE_HEX = {
  gray: '#9aa5b1',
  sky: '#48b3e1',
  navy: '#005186',
  'navy-solid': '#005186',
  'gold-pulse': '#d4af37',
  success: '#1f9d55',
  danger: '#d64545',
  'muted-danger': '#c6ced8',
};

export function hexForStatus(status) {
  return TONE_HEX[toneForStatus(status)] ?? TONE_HEX.gray;
}

export const STATUS_LABELS = {
  DRAFT: 'Brouillon',
  SUBMITTED: 'Soumis',
  APPROVED: 'Approuvé',
  REJECTED: 'Rejeté',
  CANCELLED: 'Annulé',
  DEPLOYING_DEV: 'Déploiement DEV…',
  DEPLOYED_DEV: 'Déployé en DEV',
  DEPLOYING_TEST: 'Déploiement TEST…',
  DEPLOYED_TEST: 'Déployé en TEST',
  PENDING_PROD_APPROVAL: "En attente d'approbation PROD",
  DEPLOYING_PROD: 'Déploiement PROD…',
  DEPLOYED_PROD: 'Déployé en PROD',
  FAILED: 'Échec',
  CLOSED: 'Clôturé',
  QUEUED: 'En file',
  RUNNING: 'En cours',
  SUCCESS: 'Succès',
  CANCELLED_PIPELINE: 'Annulé',
};

export function labelForStatus(status) {
  return STATUS_LABELS[status] ?? status;
}
