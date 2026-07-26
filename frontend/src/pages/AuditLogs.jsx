import { useState } from 'react';
import { useAuditLogs } from '../api/auditLogs';
import Card from '../components/ui/Card';
import Table from '../components/ui/Table';
import Pagination from '../components/ui/Pagination';
import Modal from '../components/ui/Modal';
import Button from '../components/ui/Button';
import { Input, Select } from '../components/ui/Field';
import { SkeletonRows } from '../components/ui/Skeleton';
import './AuditLogs.css';

const EVENT_TYPES = [
  'USER_SYNCED', 'TICKET_CREATED', 'TICKET_STATUS_CHANGED', 'TICKET_APPROVED',
  'PIPELINE_STARTED', 'PIPELINE_COMPLETED', 'PIPELINE_FAILED',
];

function formatDate(iso) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
}

function prettyJson(details) {
  try {
    return JSON.stringify(JSON.parse(details), null, 2);
  } catch {
    return details;
  }
}

export default function AuditLogs() {
  const [eventType, setEventType] = useState('');
  const [entityType, setEntityType] = useState('');
  const [actorUserId, setActorUserId] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);

  const filters = {
    ...(eventType && { eventType }),
    ...(entityType && { entityType }),
    ...(actorUserId && { actorUserId }),
  };
  const { data, isLoading } = useAuditLogs(filters, page, 25);

  const columns = [
    { key: 'createdAt', header: 'Date', render: (a) => formatDate(a.createdAt) },
    { key: 'eventType', header: 'Événement' },
    { key: 'entityType', header: 'Entité', render: (a) => `${a.entityType}${a.entityId ? ` #${a.entityId}` : ''}` },
    { key: 'actorUserId', header: 'Acteur' },
    { key: 'description', header: 'Description' },
  ];

  return (
    <div>
      <div className="audit-filters">
        <Select value={eventType} onChange={(e) => { setEventType(e.target.value); setPage(0); }}>
          <option value="">Tous les événements</option>
          {EVENT_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
        </Select>
        <Select value={entityType} onChange={(e) => { setEntityType(e.target.value); setPage(0); }}>
          <option value="">Toutes les entités</option>
          <option value="TICKET">TICKET</option>
          <option value="PIPELINE_EXECUTION">PIPELINE_EXECUTION</option>
          <option value="USER">USER</option>
        </Select>
        <Input placeholder="ID acteur…" value={actorUserId} onChange={(e) => { setActorUserId(e.target.value); setPage(0); }} />
      </div>

      <Card>
        {isLoading ? (
          <SkeletonRows rows={8} />
        ) : (
          <>
            <Table
              columns={columns}
              data={data?.content}
              onRowClick={setSelected}
              emptyMessage="Aucune entrée d'audit ne correspond à ces filtres"
            />
            <Pagination page={page} totalPages={data?.totalPages} onPageChange={setPage} />
          </>
        )}
      </Card>

      <Modal
        open={!!selected}
        title={selected ? `${selected.eventType} — ${selected.entityType} #${selected.entityId ?? '—'}` : ''}
        onClose={() => setSelected(null)}
        footer={<Button variant="primary" onClick={() => setSelected(null)}>Fermer</Button>}
      >
        {selected && (
          <>
            <p style={{ marginBottom: 12 }}>{selected.description}</p>
            <pre className="audit-json">{prettyJson(selected.details)}</pre>
          </>
        )}
      </Modal>
    </div>
  );
}
