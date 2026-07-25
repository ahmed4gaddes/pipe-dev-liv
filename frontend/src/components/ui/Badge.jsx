import './Badge.css';

const PRIORITY_TONE = { LOW: 'gray', MEDIUM: 'sky', HIGH: 'gold', CRITICAL: 'danger' };
const PRIORITY_LABEL = { LOW: 'Faible', MEDIUM: 'Moyenne', HIGH: 'Haute', CRITICAL: 'Critique' };

export default function Badge({ tone = 'gray', children }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export function PriorityBadge({ priority }) {
  return <Badge tone={PRIORITY_TONE[priority] ?? 'gray'}>{PRIORITY_LABEL[priority] ?? priority}</Badge>;
}

export function RoleBadge({ role }) {
  const label = role.replace('ROLE_', '');
  return <Badge tone="navy">{label}</Badge>;
}
