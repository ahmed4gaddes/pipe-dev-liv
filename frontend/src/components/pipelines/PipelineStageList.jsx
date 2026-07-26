import StatusPill from '../ui/StatusPill';
import EmptyState from '../ui/EmptyState';
import './PipelineStageList.css';

function formatDuration(seconds) {
  if (seconds == null) return '—';
  if (seconds < 60) return `${seconds}s`;
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
}

export default function PipelineStageList({ stages }) {
  if (!stages || stages.length === 0) {
    return <EmptyState icon="🧩" title="Aucune étape pour le moment" />;
  }

  const sorted = [...stages].sort((a, b) => a.stageOrder - b.stageOrder);

  return (
    <ol className="stage-list">
      {sorted.map((s) => (
        <li key={s.stageOrder} className="stage-item">
          <div className="stage-marker" />
          <div className="stage-info">
            <span className="stage-name">{s.name}</span>
            <span className="stage-duration">{formatDuration(s.durationSeconds)}</span>
          </div>
          <StatusPill status={s.status} />
        </li>
      ))}
    </ol>
  );
}
