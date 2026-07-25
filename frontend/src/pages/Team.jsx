import { useUsers } from '../api/users';
import Card from '../components/ui/Card';
import Avatar from '../components/ui/Avatar';
import { RoleBadge } from '../components/ui/Badge';
import EmptyState from '../components/ui/EmptyState';
import { SkeletonCard } from '../components/ui/Skeleton';
import './Team.css';

export default function Team() {
  const { data, isLoading } = useUsers(0, 100);

  if (isLoading) {
    return (
      <div className="team-grid">
        {Array.from({ length: 6 }).map((_, i) => <Card key={i}><SkeletonCard /></Card>)}
      </div>
    );
  }

  if (!data?.content?.length) {
    return <EmptyState icon="👥" title="Aucun utilisateur synchronisé pour le moment" />;
  }

  return (
    <div className="team-grid">
      {data.content.map((u) => (
        <Card key={u.id} className="team-card">
          <Avatar name={u.fullName || u.email} size={48} />
          <div className="team-name">{u.fullName || '—'}</div>
          <div className="team-email">{u.email}</div>
          <div className="team-roles">
            {(u.roles ?? '').split(',').filter(Boolean).map((r) => <RoleBadge key={r} role={r} />)}
          </div>
        </Card>
      ))}
    </div>
  );
}
