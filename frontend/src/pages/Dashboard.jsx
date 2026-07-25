import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTicketStats, useTickets } from '../api/tickets';
import { usePipelineExecutions } from '../api/pipelines';
import { useUnreadCount } from '../api/notifications';
import { useAuditLogs } from '../api/auditLogs';
import StatCard from '../components/dashboard/StatCard';
import StatusBreakdownChart from '../components/dashboard/StatusBreakdownChart';
import Card from '../components/ui/Card';
import StatusPill from '../components/ui/StatusPill';
import { SkeletonCard, SkeletonRows } from '../components/ui/Skeleton';
import EmptyState from '../components/ui/EmptyState';
import './Dashboard.css';

const ACTIVE_PIPELINE_STATUSES = new Set(['QUEUED', 'RUNNING']);

export default function Dashboard() {
  const { hasRole, fullName } = useAuth();
  const isApprover = hasRole('ROLE_TECH_LEAD');
  const isAdmin = hasRole('ROLE_ADMIN');

  const { data: stats, isLoading: statsLoading } = useTicketStats();
  const { data: unread } = useUnreadCount();
  const { data: pipelinesPage } = usePipelineExecutions(0, 100);
  const { data: recentTicketsPage, isLoading: recentLoading } = useTickets({ sort: 'createdAt,desc' }, 0, 5);
  const { data: recentAuditPage } = useAuditLogs({}, 0, 5, { enabled: isAdmin });

  const pendingApprovals = isApprover
    ? (stats?.countByStatus?.SUBMITTED ?? 0) + (stats?.countByStatus?.PENDING_PROD_APPROVAL ?? 0)
    : 0;
  const activePipelines = pipelinesPage?.content?.filter((e) => ACTIVE_PIPELINE_STATUSES.has(e.status)).length ?? 0;

  return (
    <div className="dashboard">
      <p className="dashboard-greeting">Bonjour {fullName?.split(' ')[0] ?? ''} — voici l'état de la plateforme.</p>

      <div className="stat-grid">
        {statsLoading ? (
          <>
            <SkeletonCard />
            <SkeletonCard />
            <SkeletonCard />
          </>
        ) : (
          <>
            <StatCard label="Tickets au total" value={stats?.totalTickets ?? 0} icon="ticket" accent="navy" index={0} />
            {isApprover && (
              <StatCard label="En attente d'approbation" value={pendingApprovals} icon="check" accent="gold" index={1} />
            )}
            <StatCard label="Pipelines actifs" value={activePipelines} icon="pipeline" accent="sky" index={2} />
            <StatCard label="Notifications non lues" value={unread?.count ?? 0} icon="bell" accent="gold" index={3} />
          </>
        )}
      </div>

      <div className="dashboard-grid">
        <Card>
          <h3 className="section-title">Répartition des tickets par statut</h3>
          {statsLoading ? <SkeletonRows rows={5} /> : <StatusBreakdownChart countByStatus={stats?.countByStatus} />}
        </Card>

        <Card>
          <div className="section-header">
            <h3 className="section-title">Tickets récents</h3>
            <Link to="/tickets">Voir tout →</Link>
          </div>
          {recentLoading ? (
            <SkeletonRows rows={5} />
          ) : recentTicketsPage?.content?.length ? (
            <ul className="recent-list">
              {recentTicketsPage.content.map((t) => (
                <li key={t.id}>
                  <Link to={`/tickets/${t.id}`} className="recent-item">
                    <span className="recent-item-title">#{t.id} — {t.title}</span>
                    <StatusPill status={t.status} />
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState icon="🗂️" title="Aucun ticket récent" />
          )}
        </Card>

        {isAdmin && (
          <Card className="dashboard-span-2">
            <div className="section-header">
              <h3 className="section-title">Activité récente (audit)</h3>
              <Link to="/audit-logs">Voir tout →</Link>
            </div>
            {recentAuditPage?.content?.length ? (
              <ul className="recent-list">
                {recentAuditPage.content.map((a) => (
                  <li key={a.id} className="recent-item">
                    <span className="recent-item-title">{a.description}</span>
                    <span className="recent-item-meta">{a.actorUserId}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState icon="🕓" title="Aucune activité récente" />
            )}
          </Card>
        )}
      </div>
    </div>
  );
}
