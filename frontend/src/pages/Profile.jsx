import { useCurrentUser } from '../api/users';
import Card from '../components/ui/Card';
import Avatar from '../components/ui/Avatar';
import { RoleBadge } from '../components/ui/Badge';
import { SkeletonRows } from '../components/ui/Skeleton';
import './Profile.css';

export default function Profile() {
  const { data: user, isLoading } = useCurrentUser();

  if (isLoading) {
    return <Card><SkeletonRows rows={4} /></Card>;
  }

  return (
    <div style={{ maxWidth: 480 }}>
      <Card className="profile-card">
        <Avatar name={user?.fullName || user?.email} size={72} />
        <h2 className="profile-name">{user?.fullName}</h2>
        <p className="profile-email">{user?.email}</p>
        <div className="profile-roles">
          {(user?.roles ?? '').split(',').filter(Boolean).map((r) => <RoleBadge key={r} role={r} />)}
        </div>
        <dl className="profile-meta">
          <dt>Identifiant Keycloak</dt>
          <dd>{user?.keycloakId}</dd>
          <dt>Membre depuis</dt>
          <dd>{user?.createdAt ? new Date(user.createdAt).toLocaleDateString('fr-FR') : '—'}</dd>
        </dl>
      </Card>
    </div>
  );
}
