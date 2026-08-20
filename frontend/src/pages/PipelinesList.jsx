import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { usePipelineExecutions, useDeletePipelineExecution } from '../api/pipelines';
import { useUsers } from '../api/users';
import Card from '../components/ui/Card';
import Table from '../components/ui/Table';
import Pagination from '../components/ui/Pagination';
import StatusPill from '../components/ui/StatusPill';
import { SkeletonRows } from '../components/ui/Skeleton';
import DeleteButton from '../components/ui/DeleteButton';

const TERMINAL_STATUSES = ['SUCCESS', 'FAILED', 'CANCELLED'];

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function PipelinesList() {
  const [page, setPage] = useState(0);
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const { data, isLoading } = usePipelineExecutions(page, 20);
  const { data: usersData } = useUsers(0, 200);
  const deleteExecution = useDeletePipelineExecution();

  const usersByKeycloakId = useMemo(() => {
    const map = {};
    (usersData?.content ?? []).forEach((u) => { map[u.keycloakId] = u.fullName || u.email; });
    return map;
  }, [usersData]);

  const columns = [
    { key: 'id', header: '#' },
    { key: 'ticketId', header: 'Ticket', render: (e) => `#${e.ticketId}` },
    { key: 'environment', header: 'Environnement' },
    { key: 'status', header: 'Statut', render: (e) => <StatusPill status={e.status} /> },
    { key: 'triggeredByUserId', header: 'Déclenché par', render: (e) => usersByKeycloakId[e.triggeredByUserId] ?? e.triggeredByUserId },
    { key: 'startedAt', header: 'Démarré', render: (e) => formatDate(e.startedAt) },
    {
      key: 'actions',
      header: '',
      render: (e) => {
        const canDelete = hasRole('ROLE_TECH_LEAD') && TERMINAL_STATUSES.includes(e.status);
        if (!canDelete) return null;
        return (
          <DeleteButton
            title={`Supprimer l'exécution #${e.id}`}
            message={`L'exécution du pipeline pour le ticket #${e.ticketId} sera supprimée définitivement.`}
            onDelete={() => deleteExecution.mutateAsync(e.id)}
          />
        );
      },
    },
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
