import { Link, useParams } from 'react-router-dom';
import Card from '../components/ui/Card';
import StatusPill from '../components/ui/StatusPill';
import { PriorityBadge } from '../components/ui/Badge';
import { SkeletonRows } from '../components/ui/Skeleton';
import EmptyState from '../components/ui/EmptyState';
import TicketActionBar from '../components/tickets/TicketActionBar';
import TicketTimeline from '../components/tickets/TicketTimeline';
import TicketComments from '../components/tickets/TicketComments';
import { useTicket, useTicketHistory } from '../api/tickets';
import { usePipelinesByTicket } from '../api/pipelines';
import './TicketDetail.css';

export default function TicketDetail() {
  const { id } = useParams();
  const { data: ticket, isLoading } = useTicket(id);
  const { data: history } = useTicketHistory(id);
  const { data: executions } = usePipelinesByTicket(id);

  if (isLoading) {
    return <Card><SkeletonRows rows={6} /></Card>;
  }

  if (!ticket) {
    return <EmptyState icon="🗂️" title="Ticket introuvable" />;
  }

  return (
    <div className="ticket-detail">
      <Card className="ticket-header">
        <div className="ticket-header-top">
          <div>
            <div className="ticket-id">#{ticket.id}</div>
            <h2 className="ticket-title">{ticket.title}</h2>
          </div>
          <div className="ticket-header-badges">
            <PriorityBadge priority={ticket.priority} />
            <StatusPill status={ticket.status} />
          </div>
        </div>

        {ticket.description && <p className="ticket-description">{ticket.description}</p>}

        <div className="ticket-chips">
          {ticket.targetEnvironment && <span className="chip">Env. {ticket.targetEnvironment}</span>}
          {ticket.gitBranch && <span className="chip">⎇ {ticket.gitBranch}</span>}
          {ticket.gitCommitSha && <span className="chip chip-mono">{ticket.gitCommitSha.slice(0, 7)}</span>}
          <span className="chip">Créé par {ticket.createdByUserId}</span>
          {ticket.assignedToUserId && <span className="chip">Assigné à {ticket.assignedToUserId}</span>}
        </div>

        <TicketActionBar ticket={ticket} />
      </Card>

      <div className="ticket-body-grid">
        <div className="ticket-col">
          <Card>
            <h3 className="section-title">Historique</h3>
            <TicketTimeline history={history} />
          </Card>

          <Card>
            <h3 className="section-title">Commentaires</h3>
            <TicketComments ticketId={ticket.id} />
          </Card>
        </div>

        <div className="ticket-col">
          <Card>
            <h3 className="section-title">Déploiements</h3>
            {executions?.length ? (
              <ul className="exec-list">
                {executions.map((e) => (
                  <li key={e.id}>
                    <Link to={`/pipelines/${e.id}`} className="exec-item">
                      <span>{e.environment}</span>
                      <StatusPill status={e.status} />
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState icon="🚀" title="Aucun déploiement" />
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
