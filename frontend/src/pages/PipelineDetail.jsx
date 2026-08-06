import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { usePipelineExecution, usePipelineStages } from '../api/pipelines';
import client from '../api/client';
import { useToast } from '../components/ui/ToastProvider';
import Card from '../components/ui/Card';
import StatusPill from '../components/ui/StatusPill';
import Button from '../components/ui/Button';
import Icon from '../components/ui/Icon';
import { SkeletonRows } from '../components/ui/Skeleton';
import EmptyState from '../components/ui/EmptyState';
import PipelineStageList from '../components/pipelines/PipelineStageList';
import './PipelineDetail.css';

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function PipelineDetail() {
  const { id } = useParams();
  const toast = useToast();
  const [loadingLogs, setLoadingLogs] = useState(false);

  const { data: execution, isLoading } = usePipelineExecution(id);
  const { data: stages } = usePipelineStages(id);

  async function openLogs() {
    setLoadingLogs(true);
    try {
      const result = await client.get(`/api/pipelines/executions/${id}/logs`);
      window.open(result.url, '_blank', 'noopener');
    } catch (err) {
      toast.error(err.message);
    } finally {
      setLoadingLogs(false);
    }
  }

  async function downloadReport() {
    try {
      const response = await client.get(`/api/pipelines/executions/${id}/report`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `rapport_execution_${id}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.parentNode.removeChild(link);
    } catch (err) {
      toast.error('Erreur lors du téléchargement du rapport');
    }
  }

  if (isLoading) {
    return <Card><SkeletonRows rows={5} /></Card>;
  }

  if (!execution) {
    return <EmptyState icon="🚀" title="Exécution introuvable" />;
  }

  return (
    <div className="pipeline-detail">
      <Card>
        <div className="pipeline-header">
          <div>
            <div className="ticket-id">Exécution #{execution.id}</div>
            <h2 className="ticket-title">{execution.environment}</h2>
          </div>
          <StatusPill status={execution.status} />
        </div>

        <div className="ticket-chips">
          <Link to={`/tickets/${execution.ticketId}`} className="chip chip-link">Ticket #{execution.ticketId}</Link>
          {execution.gitBranch && <span className="chip">⎇ {execution.gitBranch}</span>}
          {execution.gitCommitSha && <span className="chip chip-mono">{execution.gitCommitSha.slice(0, 7)}</span>}
          <span className="chip">Déclenché par {execution.triggeredByUserId}</span>
          <span className="chip">Démarré {formatDate(execution.startedAt)}</span>
          {execution.completedAt && <span className="chip">Terminé {formatDate(execution.completedAt)}</span>}
        </div>

        <div style={{ display: 'flex', gap: '1rem' }}>
          <Button variant="ghost" onClick={openLogs} loading={loadingLogs}>
            <Icon name="externalLink" size={16} /> Voir les logs sur GitHub
          </Button>
          <Button variant="primary" onClick={downloadReport}>
             Télécharger le rapport PDF
          </Button>
        </div>
      </Card>

      <Card>
        <h3 className="section-title">Étapes</h3>
        <PipelineStageList stages={stages} />
      </Card>
    </div>
  );
}
