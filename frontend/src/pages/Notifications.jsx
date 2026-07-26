import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMarkAllRead, useMarkRead, useNotifications } from '../api/notifications';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import { SkeletonRows } from '../components/ui/Skeleton';
import './Notifications.css';

function formatDate(iso) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
}

const ROUTE_BY_REFERENCE = {
  TICKET: (id) => `/tickets/${id}`,
  PIPELINE_EXECUTION: (id) => `/pipelines/${id}`,
};

export default function Notifications() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const navigate = useNavigate();

  const { data, isLoading } = useNotifications(unreadOnly, page, 20);
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();

  function handleClick(n) {
    if (!n.read) markRead.mutate(n.id);
    const route = ROUTE_BY_REFERENCE[n.referenceType];
    if (route && n.referenceId) navigate(route(n.referenceId));
  }

  return (
    <div>
      <div className="notif-toolbar">
        <label className="mine-toggle">
          <input type="checkbox" checked={unreadOnly} onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0); }} />
          Non lues uniquement
        </label>
        <Button variant="ghost" onClick={() => markAllRead.mutate()} loading={markAllRead.isPending}>
          Tout marquer comme lu
        </Button>
      </div>

      <Card>
        {isLoading ? (
          <SkeletonRows rows={6} />
        ) : data?.content?.length ? (
          <>
            <ul className="notif-list">
              {data.content.map((n) => (
                <li key={n.id}>
                  <button className={`notif-item ${!n.read ? 'notif-unread' : ''}`} onClick={() => handleClick(n)}>
                    <span className="notif-dot" />
                    <span className="notif-body">
                      <span className="notif-title">{n.title}</span>
                      <span className="notif-message">{n.message}</span>
                    </span>
                    <span className="notif-date">{formatDate(n.createdAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
            <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} />
          </>
        ) : (
          <EmptyState icon="🔔" title="Aucune notification" />
        )}
      </Card>
    </div>
  );
}
