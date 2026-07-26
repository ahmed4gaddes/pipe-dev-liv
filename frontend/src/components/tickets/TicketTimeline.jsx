import { labelForStatus } from '../../styles/status';
import EmptyState from '../ui/EmptyState';
import './TicketTimeline.css';

function formatDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function TicketTimeline({ history }) {
  if (!history || history.length === 0) {
    return <EmptyState icon="🕓" title="Aucun historique" />;
  }

  return (
    <ol className="timeline">
      {history.map((h, i) => (
        <li key={h.id} className={`timeline-item ${i === history.length - 1 ? 'timeline-item-current' : ''}`}>
          <div className="timeline-dot" />
          <div className="timeline-content">
            <div className="timeline-headline">
              {h.oldStatus ? `${labelForStatus(h.oldStatus)} → ${labelForStatus(h.newStatus)}` : `Création — ${labelForStatus(h.newStatus)}`}
            </div>
            {h.comment && <div className="timeline-comment">{h.comment}</div>}
            <div className="timeline-meta">{h.changedByUserId} · {formatDate(h.changedAt)}</div>
          </div>
        </li>
      ))}
    </ol>
  );
}
