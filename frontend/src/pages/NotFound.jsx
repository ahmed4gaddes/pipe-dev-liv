import { Link } from 'react-router-dom';
import EmptyState from '../components/ui/EmptyState';
import Button from '../components/ui/Button';

export default function NotFound() {
  return (
    <div style={{ padding: '64px 0' }}>
      <EmptyState
        icon="🧭"
        title="Page introuvable"
        description="Cette page n'existe pas ou a été déplacée."
        action={
          <Link to="/">
            <Button variant="primary">Retour au tableau de bord</Button>
          </Link>
        }
      />
    </div>
  );
}
