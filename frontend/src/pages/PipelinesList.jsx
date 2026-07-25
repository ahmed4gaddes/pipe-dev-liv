import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePipelineExecutions } from '../api/pipelines';
import Card from '../components/ui/Card';
import Table from '../components/ui/Table';
import Pagination from '../components/ui/Pagination';
import StatusPill from '../components/ui/StatusPill';
import { SkeletonRows } from '../components/ui/Skeleton';

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function PipelinesList() {
  const [page, setPage] = useState(0);
  const navigate = useNavigate();
  const { data, isLoading } = usePipelineExecutions(page, 20);

  const columns = [
    { key: 'id', header: '#' },
    { key: 'ticketId', header: 'Ticket', render: (e) => `#${e.ticketId}` },
    { key: 'environment', header: 'Environnement' },
    { key: 'status', header: 'Statut', render: (e) => <StatusPill status={e.status} /> },
    { key: 'triggeredByUserId', header: 'Déclenché par' },
    { key: 'startedAt', header: 'Démarré', render: (e) => formatDate(e.startedAt) },
  ];

  return (
    <Card>
      {isLoading ? (
        <SkeletonRows rows={8} />
      ) : (
        <>
          <Table
            columns={columns}
            data={data?.content}
            onRowClick={(row) => navigate(`/pipelines/${row.id}`)}
            emptyMessage="Aucun déploiement pour le moment"
          />
          <Pagination page={page} totalPages={data?.totalPages} onPageChange={setPage} />
        </>
      )}
    </Card>
  );
}
