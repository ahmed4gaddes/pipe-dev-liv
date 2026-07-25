import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTickets } from '../api/tickets';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Table from '../components/ui/Table';
import Pagination from '../components/ui/Pagination';
import StatusPill from '../components/ui/StatusPill';
import { PriorityBadge } from '../components/ui/Badge';
import { Select } from '../components/ui/Field';
import { SkeletonRows } from '../components/ui/Skeleton';
import { TICKET_STATUSES, TICKET_PRIORITIES } from '../constants/ticket';
import { labelForStatus } from '../styles/status';
import './TicketsList.css';

export default function TicketsList() {
  const { hasRole, userId } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [mineOnly, setMineOnly] = useState(false);
  const [page, setPage] = useState(0);

  const filters = {
    ...(status && { status }),
    ...(priority && { priority }),
    ...(mineOnly && userId && { createdByUserId: userId }),
    sort: 'createdAt,desc',
  };

  const { data, isLoading } = useTickets(filters, page, 20);

  const columns = [
    { key: 'id', header: '#' },
    { key: 'title', header: 'Titre' },
    { key: 'status', header: 'Statut', render: (t) => <StatusPill status={t.status} /> },
    { key: 'priority', header: 'Priorité', render: (t) => <PriorityBadge priority={t.priority} /> },
    { key: 'targetEnvironment', header: 'Environnement', render: (t) => t.targetEnvironment ?? '—' },
    { key: 'createdByUserId', header: 'Créé par' },
  ];

  return (
    <div>
      <div className="tickets-toolbar">
        <div className="tickets-filters">
          <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="">Tous les statuts</option>
            {TICKET_STATUSES.map((s) => (
              <option key={s} value={s}>{labelForStatus(s)}</option>
            ))}
          </Select>
          <Select value={priority} onChange={(e) => { setPriority(e.target.value); setPage(0); }}>
            <option value="">Toutes priorités</option>
            {TICKET_PRIORITIES.map((p) => (
              <option key={p} value={p}>{p}</option>
            ))}
          </Select>
          <label className="mine-toggle">
            <input
              type="checkbox"
              checked={mineOnly}
              onChange={(e) => { setMineOnly(e.target.checked); setPage(0); }}
            />
            Mes tickets
          </label>
        </div>
        {hasRole('ROLE_DEVELOPER') && (
          <Button variant="primary" onClick={() => navigate('/tickets/new')}>
            + Nouveau ticket
          </Button>
        )}
      </div>

      <Card>
        {isLoading ? (
          <SkeletonRows rows={6} />
        ) : (
          <>
            <Table
              columns={columns}
              data={data?.content}
              onRowClick={(row) => navigate(`/tickets/${row.id}`)}
              emptyMessage="Aucun ticket ne correspond à ces filtres"
            />
            <Pagination page={page} totalPages={data?.totalPages} onPageChange={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
